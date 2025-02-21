/*
 * Copyright 2018-2021 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.fhir;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.b2international.commons.StringUtils;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystemVersion;
import com.b2international.snowowl.core.codesystem.CodeSystemVersionEntry;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.codesystem.version.CodeSystemVersionSearchRequestBuilder;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.plugin.Component;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.uri.CodeSystemURI;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.fhir.core.codesystems.CommonConceptProperties;
import com.b2international.snowowl.fhir.core.codesystems.IssueSeverity;
import com.b2international.snowowl.fhir.core.codesystems.IssueType;
import com.b2international.snowowl.fhir.core.codesystems.OperationOutcomeCode;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.exceptions.FhirException;
import com.b2international.snowowl.fhir.core.model.Designation;
import com.b2international.snowowl.fhir.core.model.codesystem.Filter;
import com.b2international.snowowl.fhir.core.model.codesystem.Filters;
import com.b2international.snowowl.fhir.core.model.codesystem.IConceptProperty;
import com.b2international.snowowl.fhir.core.model.codesystem.LookupRequest;
import com.b2international.snowowl.fhir.core.model.codesystem.LookupResult;
import com.b2international.snowowl.fhir.core.model.codesystem.Property;
import com.b2international.snowowl.fhir.core.model.codesystem.SupportedCodeSystemRequestProperties;
import com.b2international.snowowl.fhir.core.model.dt.Coding;
import com.b2international.snowowl.fhir.core.model.dt.Uri;
import com.b2international.snowowl.fhir.core.provider.CodeSystemApiProvider;
import com.b2international.snowowl.fhir.core.provider.ICodeSystemApiProvider;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.RelationshipValue;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.request.SnomedConceptGetRequestBuilder;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.fhir.SnomedUri.Builder;
import com.b2international.snowowl.snomed.fhir.codesystems.CoreSnomedConceptProperties;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Provider for the SNOMED CT FHIR support
 * @since 6.4
 * @see ICodeSystemApiProvider
 * @see CodeSystemApiProvider
 */
public class SnomedCodeSystemApiProvider extends CodeSystemApiProvider {

	private static final String LOCATION_MARKER_SUBSUMES = "CodeSystem$subsumes.system";

	@Component
	public static final class Factory implements ICodeSystemApiProvider.Factory {
		@Override
		public ICodeSystemApiProvider create(IEventBus bus, List<ExtendedLocale> locales) {
			return new SnomedCodeSystemApiProvider(bus, locales);
		}
	}
	
	private static final String URI_BASE = "http://snomed.info";
	
	private static final Set<String> SUPPORTED_URIS = ImmutableSet.of(
		SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME,
		SnomedTerminologyComponentConstants.SNOMED_INT_LINK,
		SnomedUri.SNOMED_BASE_URI_STRING
	);
	
	public SnomedCodeSystemApiProvider(IEventBus bus, List<ExtendedLocale> locales) {
		super(bus, locales, SnomedDatastoreActivator.REPOSITORY_UUID);
	}
	
	@Override
	public LookupResult lookup(LookupRequest lookup) {
		
		SnomedUri snomedUri = SnomedUri.fromUriString(lookup.getSystem(), "CodeSystem$lookup.system");
		
		validateVersion(snomedUri, lookup.getVersion());
		
		CodeSystemVersion codeSystemVersion = getCodeSystemVersion(snomedUri.getVersionTag());
		String branchPath = codeSystemVersion.getPath();
		String versionString = codeSystemVersion.getEffectiveDate();
		
		validateRequestedProperties(lookup);
		
		boolean requestedChild = lookup.containsProperty(CommonConceptProperties.CHILD.getCode());
		boolean requestedParent = lookup.containsProperty(CommonConceptProperties.PARENT.getCode());
		
		String expandDescendants = requestedChild ? ",descendants(direct:true,expand(pt()))" : "";
		String expandAncestors = requestedParent ? ",ancestors(direct:true,expand(pt()))" : "";
		String displayLanguage = lookup.getDisplayLanguage() != null ? lookup.getDisplayLanguage().getCodeValue() : "en-GB";
		
		SnomedConceptGetRequestBuilder req = SnomedRequests.prepareGetConcept(lookup.getCode())
			.setExpand(String.format("descriptions(expand(type(expand(pt())))),pt()%s%s", expandDescendants, expandAncestors))
			.setLocales(ImmutableList.of(ExtendedLocale.valueOf(displayLanguage)));
		
		SnomedConcept concept = req.build(getRepositoryId(), branchPath)
			.execute(getBus())
			.getSync();
		
		return mapToLookupResult(concept, lookup, versionString);
	}
	
	@Override
	protected Collection<IConceptProperty> getSupportedConceptProperties() {
		
		// what should be the locale here? Likely we need to add the config locale as well
		final List<ExtendedLocale> locales = ImmutableList.of(ExtendedLocale.valueOf("en-x-" + Concepts.REFSET_LANGUAGE_TYPE_US));
		
		final ImmutableList.Builder<IConceptProperty> properties = ImmutableList.builder();
		
		// add basic properties
		properties.add(CoreSnomedConceptProperties.INACTIVE); 
		properties.add(CoreSnomedConceptProperties.MODULE_ID); 
		properties.add(CoreSnomedConceptProperties.EFFECTIVE_TIME); 
		properties.add(CoreSnomedConceptProperties.SUFFICIENTLY_DEFINED); 
		properties.add(CommonConceptProperties.CHILD); 
		properties.add(CommonConceptProperties.PARENT); 
		
		// fetch available relationship types and register them as supported concept property
		SnomedRequests.prepareSearchConcept()
			.all()
			.filterByActive(true)
			.filterByAncestor(Concepts.CONCEPT_MODEL_ATTRIBUTE)
			.setExpand("pt()")
			.setLocales(locales)
			.build(getRepositoryId(), Branch.MAIN_PATH)
			.execute(getBus())
			.getSync()
			.stream()
			.map(type -> {
				final String displayName = getPreferredTermOrId(type);
				return IConceptProperty.Dynamic.valueCode(URI_BASE + "/id", displayName, type.getId());
			})
			.forEach(properties::add);
		
		return properties.build();
	}
	
	
	@Override
	public boolean isSupported(CodeSystemURI codeSystemURI) {
		return codeSystemURI.getCodeSystem().startsWith(SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME);
	}
	
	@Override
	public final boolean isSupported(String uri) {
		if (Strings.isNullOrEmpty(uri)) return false;
		
		//supported URI perfect match
		boolean foundInList = getSupportedURIs().stream()
			.filter(uri::equalsIgnoreCase)
			.findAny()
			.isPresent();
		
		//extension and version is part of the URI
		boolean extensionUri = uri.startsWith(SnomedUri.SNOMED_BASE_URI_STRING);
		
		return foundInList || extensionUri;
	}
	
	@Override
	protected Set<String> fetchAncestors(final CodeSystemURI codeSystemUri, final String componentId) {
		return SnomedConcept.GET_ANCESTORS.apply(SnomedRequests.prepareGetConcept(componentId)
			.build(codeSystemUri)
			.execute(getBus())
			.getSync());
	}
	
	@Override
	protected int getCount(CodeSystemVersion codeSystemVersion) {
		return SnomedRequests.prepareSearchConcept().setLimit(0)
			.build(codeSystemVersion.getUri())
			.execute(getBus()).getSync().getTotal();
	}
	
	@Override
	protected Collection<Filter> getSupportedFilters() {
		return ImmutableList.of(
				Filters.EXPRESSION_FILTER, 
				Filters.EXPRESSIONS_FILTER,
				Filters.IS_A_FILTER, 
				Filters.REFSET_MEMBER_OF);
	}
	
	@Override
	public Collection<String> getSupportedURIs() {
		return SUPPORTED_URIS;
	}
	
	@Override
	protected String getCodeSystemShortName() {
		return SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME;
	}
	
	@Override
	protected Uri getFhirUri(com.b2international.snowowl.core.codesystem.CodeSystem codeSystem, CodeSystemVersion codeSystemVersion) {
		
		//TODO: edition module should come here
		Builder builder = SnomedUri.builder();
		
		if (codeSystemVersion != null) {
			builder.version(codeSystemVersion.getEffectiveDate());
		} 
		return builder.build().toUri();
	}
	
	@Override
	protected CodeSystemURI getCodeSystemUri(final String system, final String version) {
		
		SnomedUri snomedUri = SnomedUri.fromUriString(system, LOCATION_MARKER_SUBSUMES);
		validateVersion(snomedUri, version);
		
		String extensionModuleId = snomedUri.getExtensionModuleId();
		
		if (StringUtils.isEmpty(extensionModuleId)) {
			extensionModuleId = Concepts.MODULE_SCT_CORE;
		}
		
		CodeSystem moduleCodeSystem = findCodeSystemByModule(extensionModuleId);
		
		CodeSystemVersionSearchRequestBuilder versionSearchRequestBuilder = CodeSystemRequests.prepareSearchCodeSystemVersion()
			.one()
			.filterByCodeSystemShortName(moduleCodeSystem.getShortName())
			.sortBy(SearchResourceRequest.SortField.descending(CodeSystemVersionEntry.Fields.EFFECTIVE_DATE));
		
		//Use the version tag from the URI
		String versionTag = snomedUri.getVersionTag();
		if (versionTag != null) {
			versionSearchRequestBuilder.filterByEffectiveDate(EffectiveTimes.parse(versionTag, DateFormats.SHORT));
		}
		
		CodeSystemURI codeSystemURI = versionSearchRequestBuilder
				.build(getRepositoryId())
				.execute(getBus())
				.getSync()
				.first()
				.map(CodeSystemVersion::getUri)
				//never been versioned, return 'the latest', should be head?
				.orElse(moduleCodeSystem.getCodeSystemURI());
		
		return codeSystemURI;
	}
	
	private CodeSystem findCodeSystemByModule(String extensionModuleId) {
		
		CodeSystems codeSystems = CodeSystemRequests.prepareSearchCodeSystem()
				.all()
				.filterByToolingId(SnomedTerminologyComponentConstants.TERMINOLOGY_ID)
				.build(getRepositoryId())
				.execute(getBus())
				.getSync();
		
		for (CodeSystem codeSystem : codeSystems) {
			
			Map<String, Object> additionalProperties = codeSystem.getAdditionalProperties();
			
			if (additionalProperties == null) continue;
			if (!additionalProperties.containsKey(SnomedTerminologyComponentConstants.CODESYSTEM_MODULES_CONFIG_KEY)) continue;
			
			Object modules = additionalProperties.get(SnomedTerminologyComponentConstants.CODESYSTEM_MODULES_CONFIG_KEY);
			
			if (modules instanceof Iterable) {
				@SuppressWarnings("unchecked")
				Iterable<String> moduleIterable  = (Iterable<String>) modules;
				if (moduleIterable.iterator().hasNext()) {
					String firstModule = moduleIterable.iterator().next();
					if (extensionModuleId.equals(firstModule)) {
						return codeSystem;
					}
				}
			} else if (modules instanceof String) {
				if (extensionModuleId.equals(modules)) {
					return codeSystem;
				}
			};
		}
		
		throw new FhirException(IssueSeverity.ERROR,
				IssueType.NOT_FOUND,
				String.format("Could not find code system for SNOMED CT module '%s'.", extensionModuleId),
				OperationOutcomeCode.MSG_NO_MODULE, LOCATION_MARKER_SUBSUMES);
		
	}

	private LookupResult mapToLookupResult(SnomedConcept concept, LookupRequest lookupRequest, String version) {
		
		final LookupResult.Builder resultBuilder = LookupResult.builder();
		
		setBaseProperties(lookupRequest, resultBuilder, SnomedTerminologyComponentConstants.SNOMED_NAME, version, getPreferredTermOrId(concept));
		
		//add terms as designations
		if (lookupRequest.isPropertyRequested(SupportedCodeSystemRequestProperties.DESIGNATION)) {
				
			String languageCode = lookupRequest.getDisplayLanguage() != null ? lookupRequest.getDisplayLanguage().getCodeValue() : "en-GB";
				for (SnomedDescription description : concept.getDescriptions()) {
						
					Coding coding = Coding.builder()
						.system(SnomedUri.SNOMED_BASE_URI_STRING)
						.code(description.getTypeId())
						.display(getPreferredTermOrId(description.getType()))
						.build();
						
					resultBuilder.addDesignation(Designation.builder()
						.languageCode(languageCode)
						.use(coding)
						.value(description.getTerm())
						.build());
				}
		}
				
		// add basic SNOMED properties
		if (lookupRequest.isPropertyRequested(CoreSnomedConceptProperties.INACTIVE)) {
			resultBuilder.addProperty(CoreSnomedConceptProperties.INACTIVE.propertyOf(!concept.isActive()));
		}
		
		if (lookupRequest.isPropertyRequested(CoreSnomedConceptProperties.MODULE_ID)) {
			resultBuilder.addProperty(CoreSnomedConceptProperties.MODULE_ID.propertyOf(concept.getModuleId()));
		}
		
		if (lookupRequest.isPropertyRequested(CoreSnomedConceptProperties.SUFFICIENTLY_DEFINED)) {
			resultBuilder.addProperty(CoreSnomedConceptProperties.SUFFICIENTLY_DEFINED.propertyOf(!concept.isPrimitive()));
		}
		
		if (lookupRequest.isPropertyRequested(CoreSnomedConceptProperties.EFFECTIVE_TIME)) {
			resultBuilder.addProperty(CoreSnomedConceptProperties.EFFECTIVE_TIME.propertyOf(EffectiveTimes.format(concept.getEffectiveTime(), DateFormats.SHORT)));
		}
		
		//Optionally requested properties
		boolean requestedChild = lookupRequest.containsProperty(CommonConceptProperties.CHILD.getCode());
		boolean requestedParent = lookupRequest.containsProperty(CommonConceptProperties.PARENT.getCode());
		
		if (requestedChild && concept.getDescendants() != null) {
			for (SnomedConcept child : concept.getDescendants()) {
				resultBuilder.addProperty(CommonConceptProperties.CHILD.propertyOf(child.getId(), getPreferredTermOrId(child)));
			}
		}
		
		if (requestedParent && concept.getAncestors() != null) {
			for (SnomedConcept parent : concept.getAncestors()) {
				resultBuilder.addProperty(CommonConceptProperties.PARENT.propertyOf(parent.getId(), getPreferredTermOrId(parent)));
			}
		}
		
		//Relationship target properties
		Collection<String> properties = lookupRequest.getPropertyCodes();
		Set<String> relationshipTypeIds = properties.stream()
			.filter(p -> p.startsWith("http://snomed.info/id/"))
			.map(p -> p.substring(p.lastIndexOf('/') + 1, p.length()))
			.collect(Collectors.toSet());
		
		
		String branchPath = getBranchPath(lookupRequest.getVersion());
		
		if (!relationshipTypeIds.isEmpty()) {
			SnomedRequests.prepareSearchRelationship()
				.all()
				.filterByActive(true)
				.filterByCharacteristicType(Concepts.INFERRED_RELATIONSHIP)
				.filterBySource(concept.getId())
				.filterByType(relationshipTypeIds)
				.build(getRepositoryId(), branchPath)
				.execute(getBus())
				.getSync()
				.forEach(r -> {
					Property.Builder propertyBuilder = Property.builder()
						.code(r.getTypeId());
					
					if (r.hasValue()) {
						RelationshipValue value = r.getValueAsObject();
						value.map(
							i -> propertyBuilder.valueInteger(i),
							/*
							 * XXX: The officially supported range of values should fit in a 64-bit double;
							 * loss of precision and/or saturation to infinity can happen when the stored
							 * value is outside this range.
							 */
							d -> propertyBuilder.valueDecimal(d.doubleValue()), 
							s -> propertyBuilder.valueString(s));
					} else {
						propertyBuilder.valueCode(r.getDestinationId());
					}
					
					resultBuilder.addProperty(propertyBuilder.build());
				});
		}
		
		return resultBuilder.build();
	}
	
	/*
	 * If version tag is supplied, it should be the same as the one defined in the SNOMED CT URI
	 */
	private void validateVersion(SnomedUri snomedUri, String version) {
		
		if (!StringUtils.isEmpty(version)) {
			if (snomedUri.getVersionTag() == null) {
				throw new BadRequestException(String.format("Version is not specified in the URI [%s], while it is set in the request [%s]", snomedUri.toString(), version), "LookupRequest.version");
			} else if (!snomedUri.getVersionTag().equals(version)) {
				throw new BadRequestException(String.format("Version specified in the URI [%s] does not match the version set in the request [%s]", snomedUri.toString(), version), "LookupRequest.version");
			}
		}
	}

	private String getPreferredTermOrId(SnomedConcept concept) {
		return concept.getPt() == null ? concept.getId() : concept.getPt().getTerm();
	}
	
}
