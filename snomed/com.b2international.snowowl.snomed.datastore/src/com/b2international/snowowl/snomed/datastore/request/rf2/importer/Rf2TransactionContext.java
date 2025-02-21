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
package com.b2international.snowowl.snomed.datastore.request.rf2.importer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.index.revision.Commit;
import com.b2international.index.revision.StagingArea;
import com.b2international.snowowl.core.codesystem.CodeSystemEntry;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.DelegatingTransactionContext;
import com.b2international.snowowl.core.domain.IComponent;
import com.b2international.snowowl.core.domain.TransactionContext;
import com.b2international.snowowl.core.exceptions.ComponentNotFoundException;
import com.b2international.snowowl.core.repository.RepositoryTransactionContext;
import com.b2international.snowowl.core.terminology.TerminologyRegistry;
import com.b2international.snowowl.snomed.cis.ISnomedIdentifierService;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.*;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSet;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMember;
import com.b2international.snowowl.snomed.core.store.SnomedComponentBuilder;
import com.b2international.snowowl.snomed.core.store.SnomedComponents;
import com.b2international.snowowl.snomed.core.store.SnomedMemberBuilder;
import com.b2international.snowowl.snomed.datastore.index.entry.*;
import com.b2international.snowowl.snomed.datastore.request.SnomedOWLExpressionConverter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * @since 6.0
 */
public final class Rf2TransactionContext extends DelegatingTransactionContext {
	
	private static final Logger LOG = LoggerFactory.getLogger("import");

	private static final List<Class<? extends SnomedDocument>> IMPORT_ORDER = ImmutableList.of(SnomedConceptDocument.class, SnomedDescriptionIndexEntry.class, SnomedRelationshipIndexEntry.class, SnomedRefSetMemberIndexEntry.class);
	
	private Map<String, SnomedDocument> newComponents = newHashMap();
	private Set<String> idsToPublish = newHashSet();
	private Set<String> idsToRegister = newHashSet();
	private boolean loadOnDemand;
	
	Rf2TransactionContext(TransactionContext context, boolean loadOnDemand, Rf2ImportConfiguration importConfig) {
		super(context);
		this.loadOnDemand = loadOnDemand;
		bind(SnomedOWLExpressionConverter.class, new SnomedOWLExpressionConverter(context));
		bind(Rf2ImportConfiguration.class, importConfig);
		// override the default tx context with the rf2 tx context
		service(StagingArea.class).withContext(this);
	}
	
	@Override
	protected RepositoryTransactionContext getDelegate() {
		return (RepositoryTransactionContext) super.getDelegate();
	}
	
	@Override
	public Optional<Commit> commit() {
		throw new UnsupportedOperationException("Use the single supported commit(String) method");
	}
	
	@Override
	public Optional<Commit> commit(String commitComment) {		
		// clear local cache before executing commit
		newComponents = newHashMap();
		LOG.info("Pushing changes: {}", commitComment);
		Optional<Commit> commit = getDelegate().commit(commitComment);
		// after successful commit register all commited IDs to CIS
		final ISnomedIdentifierService cis = service(ISnomedIdentifierService.class);
		if (cis.importSupported()) {
			cis.register(idsToRegister);
			cis.publish(idsToPublish);
		}
		// clear local id cache after commit
		idsToPublish.clear();
		idsToRegister.clear();
		return commit;
	}
	
	@Override
	public Optional<Commit> commit(String commitComment, String parentContextDescription) {
		throw new UnsupportedOperationException("Use the single supported commit(String) method");
	}
	
	@Override
	public Optional<Commit> commit(String userId, String commitComment, String parentContextDescription) {
		throw new UnsupportedOperationException("Use the single supported commit(String) method");
	}
	
	@Override
	public <T> T lookup(String componentId, Class<T> type) throws ComponentNotFoundException {
		if (newComponents.containsKey(componentId)) {
			return type.cast(newComponents.get(componentId));
		} else {
			final T obj = getDelegate().getResolvedObjectById(componentId, type);
			// XXX but use the resolvedObjects cache when we are looking up dependencies in #add method 
			if (obj != null) {
				return obj;
			} else if (CodeSystemEntry.class.isAssignableFrom(type) || loadOnDemand) {
				// XXX allow lookup only for codesystems and when loadOnDemand is enabled, 
				return getDelegate().lookup(componentId, type);
			} else {
				throw new IllegalArgumentException("Missing component from maps: " + componentId);
			}
		}
	}
	
	@Override
	public <T> Map<String, T> lookup(Collection<String> componentIds, Class<T> type) {
		final Map<String, T> resolvedComponentById = newHashMap();
		Set<String> unresolvedComponentIds = newHashSet();

		// resolve by new components first
		for (String componentId : componentIds) {
			if (newComponents.containsKey(componentId)) {
				resolvedComponentById.put(componentId, type.cast(newComponents.get(componentId)));
			} else {
				unresolvedComponentIds.add(componentId);
			}
		}
		
		// load any unresolved components via index lookup
		if (!unresolvedComponentIds.isEmpty()) {
			resolvedComponentById.putAll(getDelegate().lookup(unresolvedComponentIds, type));
		}
		
		return resolvedComponentById;
	}
	
	void add(Collection<SnomedComponent> componentChanges, Multimap<Class<? extends SnomedDocument>, String> dependenciesByType) {
		final Multimap<Class<? extends SnomedDocument>, SnomedComponent> componentChangesByType = Multimaps.index(componentChanges, this::getDocType);
		for (Class<? extends SnomedDocument> type : IMPORT_ORDER) {
			final Collection<SnomedComponent> rf2Components = componentChangesByType.get(type);
			final Set<String> componentsToLookup = rf2Components.stream().map(IComponent::getId).collect(Collectors.toSet());
			// add all dependencies with the same type
			componentsToLookup.addAll(dependenciesByType.get(type));
			
			final Map<String, ? extends SnomedDocument> existingComponents = lookup(componentsToLookup, type);
			final Map<String, SnomedConceptDocument> existingRefSets;
			if (SnomedRefSetMemberIndexEntry.class == type) {
				existingRefSets = lookup(rf2Components.stream().map(member -> ((SnomedReferenceSetMember) member).getReferenceSetId()).collect(Collectors.toSet()), SnomedConceptDocument.class);
			} else {
				existingRefSets = Collections.emptyMap();
			}

			final Set<String> newRefSetIds = newHashSet();
			
			// seed missing component before applying row changes
			// and check for existing components with the same or greater effective time and skip them
			final Collection<SnomedComponent> componentsToImport = newArrayList();
			for (SnomedComponent rf2Component : rf2Components) {
				SnomedDocument existingObject = existingComponents.get(rf2Component.getId());
				if (existingObject == null) {
					// new component, add to new components and register row for import
					newComponents.put(rf2Component.getId(), createIdOnlyDoc(rf2Component.getId(), type));
					componentsToImport.add(rf2Component);
					if (rf2Component instanceof SnomedCoreComponent) {
						if (rf2Component.getEffectiveTime() == null) {
							idsToRegister.add(rf2Component.getId());
						} else {
							idsToPublish.add(rf2Component.getId());
						}
					}
				} else if (existingObject instanceof SnomedDocument && rf2Component instanceof SnomedComponent) {
					final SnomedComponent rf2Row = (SnomedComponent) rf2Component;
					final SnomedDocument existingRow = (SnomedDocument) existingObject;
					if (rf2Row.getEffectiveTime() == null || EffectiveTimes.getEffectiveTime(rf2Row.getEffectiveTime()) > existingRow.getEffectiveTime()) {
						componentsToImport.add(rf2Component);
						if (existingRow instanceof SnomedComponentDocument && rf2Row.getEffectiveTime() !=null && !existingRow.isReleased()) {
							idsToPublish.add(existingRow.getId());
						}
					}
				}
				
				// check and register refset props on concept docs
				if (rf2Component instanceof SnomedReferenceSetMember) {
					final SnomedReferenceSetMember member = (SnomedReferenceSetMember) rf2Component;
					// seed the refset if missing
					final String refSetId = member.getReferenceSetId();
					SnomedConceptDocument conceptDocToUpdate = existingRefSets.get(refSetId);
					if (conceptDocToUpdate == null || newComponents.containsKey(refSetId)) {
						conceptDocToUpdate = (SnomedConceptDocument) newComponents.get(refSetId);
					}
					if (conceptDocToUpdate.getRefSetType() == null) {
						final String referencedComponentType = SnomedTerminologyComponentConstants.getTerminologyComponentId(member.getReferencedComponent().getId());
						String mapTargetComponentType = TerminologyRegistry.UNSPECIFIED;
						try {
							mapTargetComponentType = SnomedTerminologyComponentConstants.getTerminologyComponentId((String) member.getProperties().get(SnomedRf2Headers.FIELD_MAP_TARGET));
						} catch (IllegalArgumentException e) {
							// ignored
						}
						
						final SnomedReferenceSet refSet = new SnomedReferenceSet();
						refSet.setType(member.type());
						refSet.setReferencedComponentType(referencedComponentType);
						refSet.setMapTargetComponentType(mapTargetComponentType);
						
						final SnomedConceptDocument updatedConcept = SnomedConceptDocument.builder(conceptDocToUpdate).refSet(refSet).build();
						if (newComponents.containsKey(refSetId)) {
							newComponents.put(refSetId, updatedConcept);
							newRefSetIds.add(refSetId);
						} else {
							update(conceptDocToUpdate, updatedConcept);
						}
					}
				}
			}
			
			// apply row changes
			for (SnomedComponent rf2Component : componentsToImport) {
				final String id = rf2Component.getId();
				SnomedDocument existingRevision = null;
				SnomedDocument.Builder<?, ?> newRevision;
				if (newComponents.containsKey(id)) {
					newRevision = createDocBuilder(id, type, newComponents.get(id));
				} else if (existingComponents.containsKey(id)) {
					existingRevision = existingComponents.get(id);
					newRevision = createDocBuilder(id, type, existingRevision);
				} else {
					throw new IllegalStateException(String.format("Current revision is null for %s", id));
				}
				final SnomedComponentBuilder builder;
				if (rf2Component instanceof SnomedCoreComponent) {
					builder = prepareCoreComponent(rf2Component);
				} else if (rf2Component instanceof SnomedReferenceSetMember) {
					builder = prepareMember((SnomedReferenceSetMember) rf2Component);
				} else {
					throw new UnsupportedOperationException("Unsupported component: " + rf2Component);
				}
				// apply row changes
				builder.init(newRevision, this);
				if (existingRevision == null) {
					// in this case the component is new, and the default values are okay to use
					add(newRevision.build());
				} else {
					// in this case, recalculate the released flag based on the currently available revision
					if (existingRevision.isReleased()) {
						update(existingRevision, newRevision
								.released(existingRevision.isReleased())
								.build());
					} else {
						update(existingRevision, newRevision.build());
					}
				}
			}
			
			// make sure we always attach refset properties to identifier concepts
			final StagingArea staging = service(StagingArea.class);
			for (String newRefSetId : newRefSetIds) {
				SnomedConceptDocument newRefSet = (SnomedConceptDocument) newComponents.get(newRefSetId);
				SnomedConceptDocument stagedNewRefSet = (SnomedConceptDocument) staging.getNewObject(SnomedConceptDocument.class, newRefSetId);
				if (newRefSet != null && stagedNewRefSet != null) {
					if (stagedNewRefSet.getRefSetType() == null) {
						add(SnomedConceptDocument.builder(stagedNewRefSet)
								.refSetType(newRefSet.getRefSetType())
								.referencedComponentType(newRefSet.getReferencedComponentType())
								.mapTargetComponentType(newRefSet.getMapTargetComponentType())
								.build());
					}
				}
			}
		}
	}

	/* Creates a minimal object to represent an item from the RF2 archive, ID and unreleased only */
	private SnomedDocument createIdOnlyDoc(String id, Class<? extends SnomedDocument> type) {
		if (type.isAssignableFrom(SnomedRefSetMemberIndexEntry.class)) {
			return SnomedRefSetMemberIndexEntry.builder().id(id)
					.released(false)
					.build();
		} else if (type.isAssignableFrom(SnomedConceptDocument.class)) {
			return SnomedConceptDocument.builder().id(id)
					.released(false)
					.exhaustive(false)
					.build();
		} else if (type.isAssignableFrom(SnomedDescriptionIndexEntry.class)) {
			return SnomedDescriptionIndexEntry.builder().id(id)
					.released(false)
					.build();
		} else if (type.isAssignableFrom(SnomedRelationshipIndexEntry.class)) {
			return SnomedRelationshipIndexEntry.builder().id(id)
					.released(false)
					.build();
		} else {
			throw new UnsupportedOperationException("Unknown core component type: " + type);
		}
	}

	private Class<? extends SnomedDocument> getDocType(SnomedComponent component) {
		if (component instanceof SnomedConcept) {
			return SnomedConceptDocument.class;
		} else if (component instanceof SnomedDescription) {
			return SnomedDescriptionIndexEntry.class;
		} else if (component instanceof SnomedRelationship) {
			return SnomedRelationshipIndexEntry.class;
		} else if (component instanceof SnomedReferenceSetMember) {
			return SnomedRefSetMemberIndexEntry.class;
		}
		throw new UnsupportedOperationException("Unsupported component: " + component.getClass().getSimpleName());
	}
	
	private SnomedDocument.Builder<?, ?> createDocBuilder(String componentId, Class<? extends SnomedDocument> type, SnomedDocument initializeFrom) {
		checkNotNull(initializeFrom, "InitializeFrom value is missing for %s - %s", componentId, type);
		if (type.isAssignableFrom(SnomedRefSetMemberIndexEntry.class)) {
			return SnomedRefSetMemberIndexEntry.builder((SnomedRefSetMemberIndexEntry) initializeFrom);
		} else if (type.isAssignableFrom(SnomedConceptDocument.class)) {
			return SnomedConceptDocument.builder((SnomedConceptDocument) initializeFrom);
		} else if (type.isAssignableFrom(SnomedDescriptionIndexEntry.class)) {
			return SnomedDescriptionIndexEntry.builder((SnomedDescriptionIndexEntry) initializeFrom);
		} else if (type.isAssignableFrom(SnomedRelationshipIndexEntry.class)) {
			return SnomedRelationshipIndexEntry.builder((SnomedRelationshipIndexEntry) initializeFrom);
		} else {
			throw new UnsupportedOperationException("Unknown core component type: " + type);
		}
	}
	
	private SnomedComponentBuilder<?, ?, ?> prepareCoreComponent(SnomedComponent component) {
		if (component instanceof SnomedConcept) {
			SnomedConcept concept = (SnomedConcept) component;
			return SnomedComponents.newConcept()
					.withId(component.getId())
					.withActive(concept.isActive())
					.withEffectiveTime(concept.getEffectiveTime())
					.withModuleId(concept.getModuleId())
					.withDefinitionStatusId(concept.getDefinitionStatusId())
					.withExhaustive(concept.getSubclassDefinitionStatus().isExhaustive());
		} else if (component instanceof SnomedDescription) { 
			SnomedDescription description = (SnomedDescription) component;
			return SnomedComponents.newDescription()
					.withId(component.getId())
					.withActive(description.isActive())
					.withEffectiveTime(description.getEffectiveTime())
					.withModuleId(description.getModuleId())
					.withCaseSignificanceId(description.getCaseSignificanceId())
					.withLanguageCode(description.getLanguageCode())
					.withType(description.getTypeId())
					.withTerm(description.getTerm())
					.withConcept(description.getConceptId());
		} else if (component instanceof SnomedRelationship) {
			SnomedRelationship relationship = (SnomedRelationship) component;
			return SnomedComponents.newRelationship()
					.withId(component.getId())
					.withActive(relationship.isActive())
					.withEffectiveTime(relationship.getEffectiveTime())
					.withModuleId(relationship.getModuleId())
					.withSourceId(relationship.getSourceId())
					.withTypeId(relationship.getTypeId())
					.withDestinationId(relationship.getDestinationId())
					.withDestinationNegated(false)
					.withValue(relationship.getValueAsObject())
					.withCharacteristicTypeId(relationship.getCharacteristicTypeId())
					.withGroup(relationship.getGroup())
					.withUnionGroup(relationship.getUnionGroup())
					.withModifierId(relationship.getModifierId());
		} else {
			throw new UnsupportedOperationException("Cannot prepare unknown core component: " + component);
		}
	}
	
	private SnomedComponentBuilder<?, ?, ?> prepareMember(SnomedReferenceSetMember rf2Component) {
		final Map<String, Object> properties = rf2Component.getProperties();
		SnomedMemberBuilder<?> builder;
		switch (rf2Component.type()) {
			case ASSOCIATION: 
				builder = SnomedComponents.newAssociationMember()
						.withTargetComponentId((String) properties.get(SnomedRf2Headers.FIELD_TARGET_COMPONENT_ID));
				break;
			case ATTRIBUTE_VALUE:
				builder = SnomedComponents.newAttributeValueMember()
						.withValueId((String) properties.get(SnomedRf2Headers.FIELD_VALUE_ID));
				break;
			case DESCRIPTION_TYPE: 
				builder = SnomedComponents.newDescriptionTypeMember()
						.withDescriptionFormatId((String) properties.get(SnomedRf2Headers.FIELD_DESCRIPTION_FORMAT))
						.withDescriptionLength((Integer) properties.get(SnomedRf2Headers.FIELD_DESCRIPTION_LENGTH));
				break;
			case COMPLEX_MAP:
			case EXTENDED_MAP:
				builder = SnomedComponents.newComplexMapMember()
						.withGroup((Integer) properties.get(SnomedRf2Headers.FIELD_MAP_GROUP))
						.withPriority((Integer) properties.get(SnomedRf2Headers.FIELD_MAP_PRIORITY))
						.withMapAdvice((String) properties.get(SnomedRf2Headers.FIELD_MAP_ADVICE))
						.withCorrelationId((String) properties.get(SnomedRf2Headers.FIELD_CORRELATION_ID))
						.withMapCategoryId((String) properties.get(SnomedRf2Headers.FIELD_MAP_CATEGORY_ID))
						.withMapRule((String) properties.get(SnomedRf2Headers.FIELD_MAP_RULE))
						.withMapTargetId((String) properties.get(SnomedRf2Headers.FIELD_MAP_TARGET));
				break;
			case COMPLEX_BLOCK_MAP:
				builder = SnomedComponents.newComplexBlockMapMember()
						.withGroup((Integer) properties.get(SnomedRf2Headers.FIELD_MAP_GROUP))
						.withPriority((Integer) properties.get(SnomedRf2Headers.FIELD_MAP_PRIORITY))
						.withMapAdvice((String) properties.get(SnomedRf2Headers.FIELD_MAP_ADVICE))
						.withCorrelationId((String) properties.get(SnomedRf2Headers.FIELD_CORRELATION_ID))
						.withMapRule((String) properties.get(SnomedRf2Headers.FIELD_MAP_RULE))
						.withMapTargetId((String) properties.get(SnomedRf2Headers.FIELD_MAP_TARGET))
						.withBlock((Integer) properties.get(SnomedRf2Headers.FIELD_MAP_BLOCK));
				break;
			case LANGUAGE: 
				builder = SnomedComponents.newLanguageMember()
						.withAcceptability(Acceptability.getByConceptId((String) properties.get(SnomedRf2Headers.FIELD_ACCEPTABILITY_ID)));
				break;
			case SIMPLE_MAP_WITH_DESCRIPTION: 
				builder = SnomedComponents.newSimpleMapMember()
						.withMapTargetId((String) properties.get(SnomedRf2Headers.FIELD_MAP_TARGET))
						.withMapTargetDescription((String) properties.get(SnomedRf2Headers.FIELD_MAP_TARGET_DESCRIPTION));
				break;
			case SIMPLE_MAP: 
				builder = SnomedComponents.newSimpleMapMember()
						.withMapTargetId((String) properties.get(SnomedRf2Headers.FIELD_MAP_TARGET));
				break;
			case MODULE_DEPENDENCY:
				builder = SnomedComponents.newModuleDependencyMember()
						.withSourceEffectiveTime((LocalDate) properties.get(SnomedRf2Headers.FIELD_SOURCE_EFFECTIVE_TIME))
						.withTargetEffectiveTime((LocalDate) properties.get(SnomedRf2Headers.FIELD_TARGET_EFFECTIVE_TIME));
				break;
			case SIMPLE:
				builder = SnomedComponents.newSimpleMember();
				break;
			case OWL_AXIOM: //$FALL-THROUGH$
			case OWL_ONTOLOGY:
				builder = SnomedComponents.newOWLExpressionReferenceSetMember()
						.withOWLExpression((String) properties.get(SnomedRf2Headers.FIELD_OWL_EXPRESSION));
				break;
			case MRCM_DOMAIN:
				builder = SnomedComponents.newMRCMDomainReferenceSetMember()
						.withDomainConstraint((String) properties.get(SnomedRf2Headers.FIELD_MRCM_DOMAIN_CONSTRAINT))
						.withParentDomain((String) properties.get(SnomedRf2Headers.FIELD_MRCM_PARENT_DOMAIN))
						.withProximalPrimitiveConstraint((String) properties.get(SnomedRf2Headers.FIELD_MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT))
						.withProximalPrimitiveRefinement((String) properties.get(SnomedRf2Headers.FIELD_MRCM_PROXIMAL_PRIMITIVE_REFINEMENT))
						.withDomainTemplateForPrecoordination((String) properties.get(SnomedRf2Headers.FIELD_MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION))
						.withDomainTemplateForPostcoordination((String) properties.get(SnomedRf2Headers.FIELD_MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION))
						.withEditorialGuideReference((String) properties.get(SnomedRf2Headers.FIELD_MRCM_EDITORIAL_GUIDE_REFERENCE));
				break;
			case MRCM_ATTRIBUTE_DOMAIN:
				builder = SnomedComponents.newMRCMAttributeDomainReferenceSetMember()
						.withDomainId((String) properties.get(SnomedRf2Headers.FIELD_MRCM_DOMAIN_ID))
						.withGrouped((Boolean) properties.get(SnomedRf2Headers.FIELD_MRCM_GROUPED))
						.withAttributeCardinality((String) properties.get(SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_CARDINALITY))
						.withAttributeInGroupCardinality((String) properties.get(SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY))
						.withRuleStrengthId((String) properties.get(SnomedRf2Headers.FIELD_MRCM_RULE_STRENGTH_ID))
						.withContentTypeId((String) properties.get(SnomedRf2Headers.FIELD_MRCM_CONTENT_TYPE_ID));
				break;
			case MRCM_ATTRIBUTE_RANGE:
				builder = SnomedComponents.newMRCMAttributeRangeReferenceSetMember()
						.withRangeConstraint((String) properties.get(SnomedRf2Headers.FIELD_MRCM_RANGE_CONSTRAINT))
						.withAttributeRule((String) properties.get(SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_RULE))
						.withRuleStrengthId((String) properties.get(SnomedRf2Headers.FIELD_MRCM_RULE_STRENGTH_ID))
						.withContentTypeId((String) properties.get(SnomedRf2Headers.FIELD_MRCM_CONTENT_TYPE_ID));
				break;
			case MRCM_MODULE_SCOPE:
				builder = SnomedComponents.newMRCMModuleScopeReferenceSetMember()
						.withMRCMRuleRefsetId((String) properties.get(SnomedRf2Headers.FIELD_MRCM_RULE_REFSET_ID));
				break;
			case CONCRETE_DATA_TYPE:
				builder = SnomedComponents.newConcreteDomainReferenceSetMember()
						.withCharacteristicTypeId((String) properties.get(SnomedRf2Headers.FIELD_CHARACTERISTIC_TYPE_ID))
						.withGroup(Integer.parseInt((String) properties.get(SnomedRf2Headers.FIELD_RELATIONSHIP_GROUP)))
						.withTypeId((String) properties.get(SnomedRf2Headers.FIELD_TYPE_ID))
						.withSerializedValue((String) properties.get(SnomedRf2Headers.FIELD_VALUE));
				break;
			case QUERY:
				builder = SnomedComponents.newQueryMember()
						.withQuery((String) properties.get(SnomedRf2Headers.FIELD_QUERY));
				break;
			default: 
				throw new UnsupportedOperationException("Unknown refset member type: " + rf2Component.type());
		}
		return builder
				.withId(rf2Component.getId())
				.withActive(rf2Component.isActive())
				.withEffectiveTime(rf2Component.getEffectiveTime())
				.withModuleId(rf2Component.getModuleId())
				.withReferencedComponent(rf2Component.getReferencedComponent().getId())
				.withRefSet(rf2Component.getReferenceSetId());
	}
}
