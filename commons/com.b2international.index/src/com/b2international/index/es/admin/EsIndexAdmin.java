/*
 * Copyright 2017-2021 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.index.es.admin;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest.Level;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.ReflectionUtils;
import com.b2international.index.*;
import com.b2international.index.admin.IndexAdmin;
import com.b2international.index.es.client.EsClient;
import com.b2international.index.es.query.EsQueryBuilder;
import com.b2international.index.mapping.DocumentMapping;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.b2international.index.util.JsonDiff;
import com.b2international.index.util.NumericClassUtils;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;

/**
 * @since 5.10
 */
public final class EsIndexAdmin implements IndexAdmin {

	private static final Pattern FIELD_ALIAS_CHANGE_PROPERTY_PATTERN = Pattern.compile("properties(/[a-zA-Z0-9_]+)+/fields(/[a-zA-Z0-9_]+)?");
	
	/**
	 * List of Elasticsearch supported dynamic settings.
	 */
	private static final Set<String> DYNAMIC_SETTINGS = Set.of(
		IndexClientFactory.RESULT_WINDOW_KEY
	);
	/**
	 * Local Settings are Snow Owl index client only configuration, not actual Elasticsearch supported configuration, they are implicitly dynamic.
	 */
	private static final Set<String> LOCAL_SETTINGS = Set.of(
		IndexClientFactory.COMMIT_WATERMARK_LOW_KEY,
		IndexClientFactory.COMMIT_WATERMARK_HIGH_KEY
	);
	
	private static final int DEFAULT_MAX_NUMBER_OF_VERSION_CONFLICT_RETRIES = 5;
	
	private final Random random = new Random();
	private final EsClient client;
	private final ObjectMapper mapper;
	private final String name;
	private final Map<String, Object> settings;
	
	private Mappings mappings;
	
	private final Logger log;
	private final String prefix;

	public EsIndexAdmin(EsClient client, ObjectMapper mapper, String name, Mappings mappings, Map<String, Object> settings) {
		this.client = client;
		this.mapper = mapper;
		this.name = name.toLowerCase();
		this.mappings = mappings;
		this.settings = newHashMap(settings);
		
		this.log = LoggerFactory.getLogger(String.format("index.%s", this.name));
		
		this.settings.putIfAbsent(IndexClientFactory.COMMIT_CONCURRENCY_LEVEL, IndexClientFactory.DEFAULT_COMMIT_CONCURRENCY_LEVEL);
		this.settings.putIfAbsent(IndexClientFactory.RESULT_WINDOW_KEY, ""+IndexClientFactory.DEFAULT_RESULT_WINDOW);
		this.settings.putIfAbsent(IndexClientFactory.MAX_TERMS_COUNT_KEY, ""+IndexClientFactory.DEFAULT_MAX_TERMS_COUNT);
		this.settings.putIfAbsent(IndexClientFactory.TRANSLOG_SYNC_INTERVAL_KEY, IndexClientFactory.DEFAULT_TRANSLOG_SYNC_INTERVAL);
		this.settings.putIfAbsent(IndexClientFactory.BULK_ACTIONS_SIZE, IndexClientFactory.DEFAULT_BULK_ACTIONS_SIZE);
		this.settings.putIfAbsent(IndexClientFactory.BULK_ACTIONS_SIZE_IN_MB, IndexClientFactory.DEFAULT_BULK_ACTIONS_SIZE_IN_MB);
		this.settings.putIfAbsent(IndexClientFactory.COMMIT_WATERMARK_LOW_KEY, IndexClientFactory.DEFAULT_COMMIT_WATERMARK_LOW_VALUE);
		this.settings.putIfAbsent(IndexClientFactory.COMMIT_WATERMARK_HIGH_KEY, IndexClientFactory.DEFAULT_COMMIT_WATERMARK_HIGH_VALUE);
		
		final String prefix = (String) settings.getOrDefault(IndexClientFactory.INDEX_PREFIX, IndexClientFactory.DEFAULT_INDEX_PREFIX);
		this.prefix = prefix.isEmpty() ? "" : prefix + ".";
	}
	
	@Override
	public Logger log() {
		return log;
	}

	@Override
	public boolean exists() {
		try {
			return client().indices().exists(indices());
		} catch (Exception e) {
			throw new IndexException("Couldn't check the existence of all ES indices.", e);
		}
	}

	private boolean exists(DocumentMapping mapping) {
		final String index = getTypeIndex(mapping);
		try {
			return client().indices().exists(index);
		} catch (Exception e) {
			throw new IndexException("Couldn't check the existence of ES index '" + index + "'.", e);
		}
	}

	@Override
	public void create() {
		log.info("Preparing '{}' indexes...", name);
		// register any type that requires a refresh at the end of the index create/open
		Set<DocumentMapping> mappingsToRefresh = Sets.newHashSet();
		
		// create number of indexes based on number of types
		for (DocumentMapping mapping : mappings.getMappings()) {
			final String index = getTypeIndex(mapping);
			final String type = mapping.typeAsString();
			final Map<String, Object> typeMapping = ImmutableMap.<String, Object>builder()
					.put("date_detection", false)
					.put("numeric_detection", false)
					.put("dynamic_templates", List.of(stringsAsKeywords()))
					.putAll(toProperties(mapping))
					.build();
			
			if (exists(mapping)) {
				// update mapping if required
				final MappingMetadata currentIndexMapping;
				
				try {
					final GetMappingsRequest getMappingsRequest = new GetMappingsRequest().indices(index);
					currentIndexMapping = client.indices().getMapping(getMappingsRequest)
						.mappings()
						.get(index);
				} catch (Exception e) {
					throw new IndexException(String.format("Failed to get mapping of '%s' for type '%s'", name, mapping.typeAsString()), e);
				}
				
				try {
					final ObjectNode newTypeMapping = mapper.valueToTree(typeMapping);
					final ObjectNode currentTypeMapping = mapper.valueToTree(currentIndexMapping.getSourceAsMap());
					SortedSet<String> compatibleChanges = Sets.newTreeSet();
					SortedSet<String> incompatibleChanges = Sets.newTreeSet();
					JsonDiff.diff(currentTypeMapping, newTypeMapping).forEach(change -> {
						if (change.isAdd()) {
							compatibleChanges.add(change.getFieldPath());
						} else if (change.isMove() || change.isReplace()) {
							incompatibleChanges.add(change.getFieldPath());
						}
					});
					if (!incompatibleChanges.isEmpty()) {
						log.warn("Cannot migrate index '{}' to new mapping with breaking changes on properties '{}'. Run repository reindex to migrate to new mapping schema or drop that index manually using the Elasticsearch API.", index, incompatibleChanges);
					} else if (!compatibleChanges.isEmpty()) {
						log.info("Applying mapping changes {} in index {}", compatibleChanges, index);
						AcknowledgedResponse response = client.indices().updateMapping(new PutMappingRequest(index).source(typeMapping));
						checkState(response.isAcknowledged(), "Failed to update mapping '%s' for type '%s'", name, mapping.typeAsString());
						// if there are field alias changes, then run update_by_query on all documents to simply reindex them
						// new fields do not require reindex, they will be added to new documents, existing documents don't have any data that needs reindex 
						if (hasFieldAliasChange(compatibleChanges)) {
							if (bulkIndexByScroll(client, mapping, Expressions.matchAll(), "update", null /*no script, in place update of docs to pick up mapping changes*/, "mapping migration")) {
								mappingsToRefresh.add(mapping);
							}
							log.info("Migrated documents to new mapping in index '{}'", index);
						}
					}
				} catch (IOException e) {
					throw new IndexException(String.format("Failed to update mapping '%s' for type '%s'", name, mapping.typeAsString()), e);
				}
			} else {
				// create index
				final Map<String, Object> indexSettings;
				try {
					indexSettings = createIndexSettings();
					log.info("Configuring '{}' index with settings: {}", index, indexSettings);
				} catch (IOException e) {
					throw new IndexException("Couldn't prepare settings for index " + index, e);
				}
				
				final CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
				createIndexRequest.mapping(typeMapping);
				createIndexRequest.settings(indexSettings);
				
				try {
					final CreateIndexResponse response = client.indices().create(createIndexRequest);
					checkState(response.isAcknowledged(), "Failed to create index '%s' for type '%s'", name, mapping.typeAsString());
				} catch (Exception e) {
					throw new IndexException(String.format("Failed to create index '%s' for type '%s'", name, mapping.typeAsString()), e);
				}
			}
		}
		// wait until the cluster processes each index create request
		waitForYellowHealth(indices());
		if (!mappingsToRefresh.isEmpty()) {
			refresh(mappingsToRefresh);
		}
		log.info("'{}' indexes are ready.", name);
	}

	private boolean hasFieldAliasChange(SortedSet<String> compatibleChanges) {
		return compatibleChanges.stream()
				.map(FIELD_ALIAS_CHANGE_PROPERTY_PATTERN::matcher)
				.anyMatch(Matcher::matches);
	}
	
	private Map<String, Object> stringsAsKeywords() {
		return Map.of(
			"strings_as_keywords", Map.of(
				"match_mapping_type", "string",
				"mapping", Map.of(
					"type", "keyword"
				)
			)
		);
	}

	private Map<String, Object> createIndexSettings() throws IOException {
		InputStream analysisStream = getClass().getResourceAsStream("analysis.json");
		Settings analysisSettings = Settings.builder()
				.loadFromStream("analysis.json", analysisStream, true)
				.build();
		
		// FIXME: Is XContent a good alternative to a Map? getAsStructureMap is now private
		Map<String, Object> analysisMap = ReflectionUtils.callMethod(Settings.class, analysisSettings, "getAsStructuredMap");
		
		return ImmutableMap.<String, Object>builder()
				.put("analysis", analysisMap)
				.put("number_of_shards", String.valueOf(settings().getOrDefault(IndexClientFactory.NUMBER_OF_SHARDS, "1")))
				.put("number_of_replicas", "0")
				// disable es refresh, we will do it manually on each commit
				.put("refresh_interval", "-1")
				// use async durability for the translog
				.put("translog.durability", "async")
				// wait all shards during writes
				.put("write.wait_for_active_shards", "all")
				.put(IndexClientFactory.RESULT_WINDOW_KEY, settings().get(IndexClientFactory.RESULT_WINDOW_KEY))
				.put(IndexClientFactory.MAX_TERMS_COUNT_KEY, settings().get(IndexClientFactory.MAX_TERMS_COUNT_KEY))
				.put(IndexClientFactory.TRANSLOG_SYNC_INTERVAL_KEY, settings().get(IndexClientFactory.TRANSLOG_SYNC_INTERVAL_KEY))
				.build();
	}
	
	private void waitForYellowHealth(String... indices) {
		if (!CompareUtils.isEmpty(indices)) {
			/*
			 * See https://www.elastic.co/guide/en/elasticsearch/reference/6.3/cluster-health.html 
			 * for the low-level structure of the cluster health request.
			 */
			final Object clusterTimeoutSetting = settings.getOrDefault(IndexClientFactory.CLUSTER_HEALTH_TIMEOUT, IndexClientFactory.DEFAULT_CLUSTER_HEALTH_TIMEOUT);
			final Object socketTimeoutSetting = settings.getOrDefault(IndexClientFactory.SOCKET_TIMEOUT, IndexClientFactory.DEFAULT_SOCKET_TIMEOUT);
			final int clusterTimeout = clusterTimeoutSetting instanceof Integer ? (int) clusterTimeoutSetting : Integer.parseInt((String) clusterTimeoutSetting);
			final int socketTimeout = socketTimeoutSetting instanceof Integer ? (int) socketTimeoutSetting : Integer.parseInt((String) socketTimeoutSetting);
			final int pollTimeout = socketTimeout / 2;
			
			final ClusterHealthRequest req = new ClusterHealthRequest(indices)
					.waitForYellowStatus() // Wait until yellow status is reached
					.timeout(String.format("%sms", pollTimeout)); // Poll interval is half the socket timeout
			req.level(Level.INDICES); // Detail level should be concerned with the indices in the path
			
			final long startTime = System.currentTimeMillis();
			final long endTime = startTime + clusterTimeout; // Polling finishes when the cluster timeout is reached
			long currentTime = startTime;
			
			ClusterHealthResponse response = null;
			
			do {
				
				try {
					response = client().cluster().health(req);
					currentTime = System.currentTimeMillis();
					if (response != null && !response.isTimedOut()) {
						break; 
					}
				} catch (Exception e) {
					throw new IndexException("Couldn't retrieve cluster health for index " + name, e);
				}
				
			} while (currentTime < endTime);
			
			if (response == null || response.isTimedOut()) {
				throw new IndexException(String.format("Cluster health did not reach yellow status for '%s' indexes after %s ms.", name, currentTime - startTime), null);
			} else {
				log.info("Cluster health for '{}' indexes reported as '{}' after {} ms.", name, response.getStatus(), currentTime - startTime);
			}
		}
	}

	private Map<String, Object> toProperties(DocumentMapping mapping) {
		Map<String, Object> properties = newHashMap();
		for (Field field : mapping.getFields()) {
			// skip transient fields
			if (Modifier.isTransient(field.getModifiers())) {
				continue;
			}
			
			final String property = field.getName();
			if (DocumentMapping._ID.equals(property)) continue;
			final Class<?> fieldType = NumericClassUtils.unwrapCollectionType(field);
			
			if (Map.class.isAssignableFrom(fieldType)) {
				// allow dynamic mappings for dynamic objects like field using Map
				final Map<String, Object> prop = newHashMap();
				prop.put("type", "object");
				prop.put("dynamic", "true");
				properties.put(property, prop);
				continue;
			} else if (fieldType.isAnnotationPresent(Doc.class)) {
				Doc annotation = fieldType.getAnnotation(Doc.class);
				// this is a nested document type create a nested mapping
				final Map<String, Object> prop = newHashMap();
				// XXX type: object is the default for nested objects, ES won't store it in the mapping and will default to object even if explicitly set, which would cause unnecessary mapping update during boot
				if (annotation.nested()) {
					prop.put("type", "nested");
				}
				// XXX enabled: true is the default, ES won't store it in the mapping and will default to true even if explicitly set, which would cause unnecessary mapping update during boot
				if (!annotation.index()) {
					prop.put("enabled", false);
				}
				prop.putAll(toProperties(new DocumentMapping(fieldType)));
				properties.put(property, prop);
			} else {
				final Map<String, Object> prop = newHashMap();
				
				if (!mapping.isText(property) && !mapping.isKeyword(property)) {
					addFieldProperties(prop, fieldType);
					properties.put(property, prop);
				} else {
					checkState(String.class.isAssignableFrom(fieldType), "Only String fields can have Text and Keyword annotation. Found them on '%s'", property);
					
					final Map<String, Text> textFields = mapping.getTextFields(property);
					final Map<String, Keyword> keywordFields = mapping.getKeywordFields(property);
					
					final Text textMapping = textFields.get(property);
					final Keyword keywordMapping = keywordFields.get(property);
					checkState(textMapping == null || keywordMapping == null, "Cannot declare both Text and Keyword annotation on same field '%s'", property);
					
					if (textMapping != null) {
						prop.put("type", "text");
						prop.put("analyzer", textMapping.analyzer().getAnalyzer());
						if (textMapping.searchAnalyzer() != Analyzers.INDEX) {
							prop.put("search_analyzer", textMapping.searchAnalyzer().getAnalyzer());
						}
					}
					
					if (keywordMapping != null) {
						prop.put("type", "keyword");
						String normalizer = keywordMapping.normalizer().getNormalizer();
						if (!Strings.isNullOrEmpty(normalizer)) {
							prop.put("normalizer", normalizer);
						}
						// XXX index: true is the default, ES won't store it in the mapping and will default to true even if explicitly set, which would cause unnecessary mapping update during boot
						// XXX doc_values: true is the default, ES won't store it in the mapping and will default to true even if explicitly set, which would cause unnecessary mapping update during boot
						if (!keywordMapping.index()) {
							prop.put("index", false);
							prop.put("doc_values", false);
						}
					}
					
					// put extra text fields into fields object
					final Map<String, Object> fields = newHashMapWithExpectedSize(textFields.size() + keywordFields.size());
					for (Entry<String, Text> analyzer : textFields.entrySet()) {
						final String extraField = analyzer.getKey();
						final String[] extraFieldParts = extraField.split(Pattern.quote(DocumentMapping.DELIMITER));
						if (extraFieldParts.length > 1) {
							final Text analyzed = analyzer.getValue();
							final Map<String, Object> fieldProps = newHashMap();
							fieldProps.put("type", "text");
							fieldProps.put("analyzer", analyzed.analyzer().getAnalyzer());
							if (analyzed.searchAnalyzer() != Analyzers.INDEX) {
								fieldProps.put("search_analyzer", analyzed.searchAnalyzer().getAnalyzer());
							}
							fields.put(extraFieldParts[1], fieldProps);
						}
					}
					
					// put extra keyword fields into fields object
					for (Entry<String, Keyword> analyzer : keywordFields.entrySet()) {
						final String extraField = analyzer.getKey();
						final String[] extraFieldParts = extraField.split(Pattern.quote(DocumentMapping.DELIMITER));
						if (extraFieldParts.length > 1) {
							final Keyword analyzed = analyzer.getValue();
							final Map<String, Object> fieldProps = newHashMap();
							fieldProps.put("type", "keyword");
							String normalizer = analyzed.normalizer().getNormalizer();
							if (!Strings.isNullOrEmpty(normalizer)) {
								fieldProps.put("normalizer", normalizer);
							}
							if (!analyzed.index()) {
								fieldProps.put("index", false);
							}
							fields.put(extraFieldParts[1], fieldProps);
						}
					}
					
					if (!fields.isEmpty()) {
						prop.put("fields", fields);
					}
					properties.put(property, prop);
				}
				
			}
		}
		
		return ImmutableMap.of("properties", properties);
	}

	private void addFieldProperties(Map<String, Object> fieldProperties, Class<?> fieldType) {
		if (Enum.class.isAssignableFrom(fieldType) || NumericClassUtils.isBigDecimal(fieldType) || String.class.isAssignableFrom(fieldType)) {
			fieldProperties.put("type", "keyword");
		} else if (NumericClassUtils.isDouble(fieldType)) {
			fieldProperties.put("type", "double");
		} else if (NumericClassUtils.isFloat(fieldType)) {
			fieldProperties.put("type", "float");
		} else if (NumericClassUtils.isInt(fieldType)) {
			fieldProperties.put("type", "integer");
		} else if (NumericClassUtils.isShort(fieldType)) {
			fieldProperties.put("type", "short");
		} else if (NumericClassUtils.isDate(fieldType) || NumericClassUtils.isLong(fieldType)) {
			fieldProperties.put("type", "long");
		} else if (Boolean.class.isAssignableFrom(Primitives.wrap(fieldType))) {
			fieldProperties.put("type", "boolean");
		} else if (fieldType.isAnnotationPresent(IP.class)) {
			fieldProperties.put("type", "ip");
		} else if (hasJsonValue(fieldType)) {
			fieldProperties.put("type", "keyword"); // FIXME for now consider only String based @JsonValue annotations
		} else {
			// Any other type will result in a sub-object that only appears in _source
			fieldProperties.put("type", "object");
			fieldProperties.put("enabled", false);
		}
	}

	// returns true if one of the methods of the type has the @JsonValue annotation
	private boolean hasJsonValue(Class<?> fieldType) {
		for (Method method : fieldType.getMethods()) {
			if (method.isAnnotationPresent(JsonValue.class) && String.class == method.getReturnType()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void delete() {
		if (exists()) {
			final DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(name + "*");
			try {
				final AcknowledgedResponse deleteIndexResponse = client()
						.indices()
						.delete(deleteIndexRequest);
				checkState(deleteIndexResponse.isAcknowledged(), "Failed to delete all ES indices for '%s'.", name);
			} catch (Exception e) {
				throw new IndexException(String.format("Failed to delete all ES indices for '%s'.", name), e);
			}
		}
	}

	@Override
	public void clear(Collection<Class<?>> types) {
		if (CompareUtils.isEmpty(types)) {
			return;
		}
		
		final Set<DocumentMapping> typesToRefresh = Collections.synchronizedSet(newHashSetWithExpectedSize(types.size()));
		
		for (Class<?> type : types) {
			if (bulkDelete(new BulkDelete<>(type, Expressions.matchAll()))) {
				typesToRefresh.add(mappings.getMapping(type));
			}
		}
		
		refresh(typesToRefresh);
	}

	@Override
	public Map<String, Object> settings() {
		return settings;
	}
	
	@Override
	public void updateSettings(Map<String, Object> newSettings) {
		if (CompareUtils.isEmpty(newSettings)) {
			return;
		}

		// ignore local and/or dynamic settings
		final Set<String> unsupportedDynamicSettings = Sets.difference(Sets.difference(newSettings.keySet(), DYNAMIC_SETTINGS), LOCAL_SETTINGS);
		if (!unsupportedDynamicSettings.isEmpty()) {
			throw new IndexException(String.format("Settings [%s] are not dynamically updateable settings.", unsupportedDynamicSettings), null);
		}
		
		boolean shouldUpdate = false;
		for (String settingKey : newSettings.keySet()) {
			Object currentValue = settings.get(settingKey);
			Object newValue = newSettings.get(settingKey);
			if (!Objects.equals(currentValue, newValue)) {
				shouldUpdate = true;
			}
		}
		
		if (!shouldUpdate) {
			return;
		}
		
		Map<String, Object> esSettings = new HashMap<>(newSettings);
		// remove any local settings from esSettings
		esSettings.keySet().removeAll(LOCAL_SETTINGS);
		
		for (DocumentMapping mapping : mappings.getMappings()) {
			final String index = getTypeIndex(mapping);
			// if any index exists, then update the settings based on the new settings
			if (exists(mapping)) {
				try {
					log.info("Applying settings '{}' changes in index {}...", esSettings, index);
					AcknowledgedResponse response = client.indices().updateSettings(new UpdateSettingsRequest().indices(index).settings(esSettings));
					checkState(response.isAcknowledged(), "Failed to update index settings '%s'.", index);
				} catch (IOException e) {
					throw new IndexException(String.format("Couldn't update settings of index '%s'", index), e);
				}
			}
		}
		
		// update both local and es settings
		settings.putAll(newSettings);
	}

	@Override
	public Mappings mappings() {
		return mappings;
	}
	
	@Override
	public void updateMappings(Mappings mappings) {
		this.mappings = mappings;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void optimize(int maxSegments) {
//		client().admin().indices().prepareForceMerge(name).setMaxNumSegments(maxSegments).get();
//		waitForYellowHealth();
	}
	
	@Override
	public String getTypeIndex(DocumentMapping mapping) {
		if (mapping.getParent() != null) {
			return String.format("%s%s-%s", prefix, name, mapping.getParent().typeAsString());
		} else {
			return String.format("%s%s-%s", prefix, name, mapping.typeAsString());
		}
	}
	
	@Override
	public EsClient client() {
		return client;
	}
	
	public void refresh(Set<DocumentMapping> typesToRefresh) {
		if (!CompareUtils.isEmpty(typesToRefresh)) {
			final String[] indicesToRefresh;
			
			synchronized (typesToRefresh) {
				indicesToRefresh = typesToRefresh.stream()
						.map(this::getTypeIndex)
						.distinct()
						.toArray(String[]::new);
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Refreshing indexes '{}'", Arrays.toString(indicesToRefresh));
			}
			
			try {
			
				final RefreshRequest refreshRequest = new RefreshRequest(indicesToRefresh);
				final RefreshResponse refreshResponse = client()
						.indices()
						.refresh(refreshRequest);
				if (RestStatus.OK != refreshResponse.getStatus() && log.isErrorEnabled()) {
					log.error("Index refresh request of '{}' returned with status {}", Arrays.toString(indicesToRefresh), refreshResponse.getStatus());
				}
				
			} catch (Exception e) {
				throw new IndexException(String.format("Failed to refresh ES indexes '%s'.", Arrays.toString(indicesToRefresh)), e);
			}
		}
	}
	
	public boolean bulkUpdate(final BulkUpdate<?> update) {
		final DocumentMapping mapping = mappings().getMapping(update.getType());
		final String rawScript = mapping.getScript(update.getScript()).script();
		org.elasticsearch.script.Script script = new org.elasticsearch.script.Script(ScriptType.INLINE, "painless", rawScript, Map.copyOf(update.getParams()));
		return bulkIndexByScroll(client, mapping, update.getFilter(), "update", script, update.toString());
	}

	public boolean bulkDelete(final BulkDelete<?> delete) {
		final DocumentMapping mapping = mappings().getMapping(delete.getType());
		return bulkIndexByScroll(client, mapping, delete.getFilter(), "delete", null, delete.toString());
	}

	private boolean bulkIndexByScroll(final EsClient client,
			final DocumentMapping mapping,
			final Expression filter,
			final String command, 
			final org.elasticsearch.script.Script script,
			final String operationDescription) {
		
		final QueryBuilder query = new EsQueryBuilder(mapping, settings, log).build(filter);
		
		boolean needsRefresh = false;
		long versionConflicts = 0;
		int attempts = DEFAULT_MAX_NUMBER_OF_VERSION_CONFLICT_RETRIES;
		
		do {

			try {
				
				final BulkByScrollResponse response;
				final int batchSize = Integer.parseInt((String) settings.get(IndexClientFactory.RESULT_WINDOW_KEY));
				if ("update".equals(command)) {
					response = client.updateByQuery(getTypeIndex(mapping), batchSize, script, getConcurrencyLevel(), query);
				} else if ("delete".equals(command)) {
					response = client.deleteByQuery(getTypeIndex(mapping), batchSize, getConcurrencyLevel(), query);
				} else {
					throw new UnsupportedOperationException("Not implemented command: " + command);
				}
				
				final long updateCount = response.getUpdated();
				final long deleteCount = response.getDeleted();
				final long noops = response.getNoops();
				final List<Failure> failures = response.getBulkFailures();
				
				versionConflicts = response.getVersionConflicts();
				
				boolean updated = updateCount > 0;
				if (updated) {
					log().info("Updated {} {} documents with bulk {}", updateCount, mapping.typeAsString(), operationDescription);
					needsRefresh = true;
				}
				
				boolean deleted = deleteCount > 0;
				if (deleted) {
					log().info("Deleted {} {} documents with bulk {}", deleteCount, mapping.typeAsString(), operationDescription);
					needsRefresh = true;
				}
				
				if (!updated && !deleted) {
					log().warn("Bulk {} could not be applied to {} documents, no-ops ({}), conflicts ({})",
							operationDescription,
							mapping.typeAsString(), 
							noops, 
							versionConflicts);
				}
				
				if (failures.size() > 0) {
					boolean versionConflictsOnly = true;
					for (Failure failure : failures) {
						final String failureMessage = failure.getCause().getMessage();
						final int failureStatus = failure.getStatus().getStatus();
						
						if (failureStatus != RestStatus.CONFLICT.getStatus()) {
							versionConflictsOnly = false;
							log().error("Index failure during bulk update: {}", failureMessage);
						} else {
							log().warn("Version conflict reason: {}", failureMessage);
						}
					}

					if (!versionConflictsOnly) {
						throw new IllegalStateException("There were indexing failures during bulk updates. See logs for all failures.");
					}
				}
				
				if (attempts <= 0) {
					throw new IndexException("There were indexing failures during bulk updates. See logs for all failures.", null);
				}
				
				if (versionConflicts > 0) {
					--attempts;
					try {
						Thread.sleep(100 + random.nextInt(900));
						refresh(Collections.singleton(mapping));
					} catch (InterruptedException e) {
						throw new IndexException("Interrupted", e);
					}
				}
			} catch (IOException e) {
				throw new IndexException("Could not execute bulk update.", e);
			}
		} while (versionConflicts > 0);
		
		return needsRefresh;
	}
	
	public int getConcurrencyLevel() {
		return (int) settings().get(IndexClientFactory.COMMIT_CONCURRENCY_LEVEL);
	}
	
}
