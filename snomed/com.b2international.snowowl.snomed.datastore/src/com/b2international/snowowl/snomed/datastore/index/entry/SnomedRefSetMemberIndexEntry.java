/*
 * Copyright 2011-2021 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.datastore.index.entry;

import static com.b2international.index.query.Expressions.exactMatch;
import static com.b2international.index.query.Expressions.exists;
import static com.b2international.index.query.Expressions.match;
import static com.b2international.index.query.Expressions.matchAny;
import static com.b2international.index.query.Expressions.matchAnyDecimal;
import static com.b2international.index.query.Expressions.matchAnyInt;
import static com.b2international.index.query.Expressions.matchRange;
import static com.b2international.index.query.Expressions.nestedMatch;
import static com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.CONCEPT_NUMBER;
import static com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.DESCRIPTION_NUMBER;
import static com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants.RELATIONSHIP_NUMBER;
import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.b2international.commons.collections.Collections3;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.index.Doc;
import com.b2international.index.Keyword;
import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions.ExpressionBuilder;
import com.b2international.index.revision.ObjectId;
import com.b2international.index.revision.Revision;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.terminology.TerminologyRegistry;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedCoreComponent;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;
import com.b2international.snowowl.snomed.core.domain.SnomedRelationship;
import com.b2international.snowowl.snomed.core.domain.refset.DataType;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedRefSetType;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMember;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Lightweight representation of a SNOMED CT reference set member.
 */
@Doc(
	type="member",
	revisionHash = { 
		SnomedDocument.Fields.ACTIVE, 
		SnomedDocument.Fields.EFFECTIVE_TIME, 
		SnomedDocument.Fields.MODULE_ID, 
		SnomedDocument.Fields.RELEASED, // XXX required for SnomedComponentRevisionConflictProcessor CHANGED vs. DELETED detection
		SnomedRefSetMemberIndexEntry.Fields.TARGET_COMPONENT,
		SnomedRefSetMemberIndexEntry.Fields.VALUE_ID,
		SnomedRefSetMemberIndexEntry.Fields.STRING_VALUE,
		SnomedRefSetMemberIndexEntry.Fields.BOOLEAN_VALUE,
		SnomedRefSetMemberIndexEntry.Fields.INTEGER_VALUE,
		SnomedRefSetMemberIndexEntry.Fields.DECIMAL_VALUE,
		SnomedRefSetMemberIndexEntry.Fields.RELATIONSHIP_GROUP,
		SnomedRefSetMemberIndexEntry.Fields.TYPE_ID,
		SnomedRefSetMemberIndexEntry.Fields.CHARACTERISTIC_TYPE_ID,
		SnomedRefSetMemberIndexEntry.Fields.DESCRIPTION_LENGTH,
		SnomedRefSetMemberIndexEntry.Fields.DESCRIPTION_FORMAT,
		SnomedRefSetMemberIndexEntry.Fields.ACCEPTABILITY_ID,
		SnomedRefSetMemberIndexEntry.Fields.SOURCE_EFFECTIVE_TIME,
		SnomedRefSetMemberIndexEntry.Fields.TARGET_EFFECTIVE_TIME,
		SnomedRefSetMemberIndexEntry.Fields.MAP_TARGET,
		SnomedRefSetMemberIndexEntry.Fields.MAP_TARGET_DESCRIPTION,
		SnomedRefSetMemberIndexEntry.Fields.MAP_CATEGORY_ID,
		SnomedRefSetMemberIndexEntry.Fields.CORRELATION_ID,
		SnomedRefSetMemberIndexEntry.Fields.MAP_ADVICE,
		SnomedRefSetMemberIndexEntry.Fields.MAP_RULE,
		SnomedRefSetMemberIndexEntry.Fields.MAP_GROUP,
		SnomedRefSetMemberIndexEntry.Fields.MAP_PRIORITY,
		SnomedRefSetMemberIndexEntry.Fields.QUERY,
		SnomedRefSetMemberIndexEntry.Fields.OWL_EXPRESSION,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_DOMAIN_CONSTRAINT,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_PARENT_DOMAIN,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_PROXIMAL_PRIMITIVE_REFINEMENT,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_EDITORIAL_GUIDE_REFERENCE,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_GROUPED,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_ATTRIBUTE_CARDINALITY,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_RULE_STRENGTH_ID,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_CONTENT_TYPE_ID,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_RANGE_CONSTRAINT,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_ATTRIBUTE_RULE,
		SnomedRefSetMemberIndexEntry.Fields.MRCM_RULE_REFSET_ID,
		SnomedRefSetMemberIndexEntry.Fields.MAP_BLOCK,
	}
)
@JsonDeserialize(builder = SnomedRefSetMemberIndexEntry.Builder.class)
public final class SnomedRefSetMemberIndexEntry extends SnomedDocument {

	public static class Fields extends SnomedDocument.Fields {
		// All member types
		public static final String REFERENCE_SET_ID = "referenceSetId"; // XXX different than the RF2 header field name
		public static final String REFERENCED_COMPONENT_ID = SnomedRf2Headers.FIELD_REFERENCED_COMPONENT_ID;
		public static final String REFSET_TYPE = "referenceSetType";
		public static final String REFERENCED_COMPONENT_TYPE = "referencedComponentType";

		// Language type
		public static final String ACCEPTABILITY_ID = SnomedRf2Headers.FIELD_ACCEPTABILITY_ID;
		
		// Attribute value type
		public static final String VALUE_ID = SnomedRf2Headers.FIELD_VALUE_ID;
		
		// Association type
		public static final String TARGET_COMPONENT = SnomedRf2Headers.FIELD_TARGET_COMPONENT;
		
		// Simple, complex extended map type
		public static final String MAP_TARGET = SnomedRf2Headers.FIELD_MAP_TARGET;
		public static final String MAP_TARGET_DESCRIPTION = SnomedRf2Headers.FIELD_MAP_TARGET_DESCRIPTION;
		public static final String MAP_GROUP = SnomedRf2Headers.FIELD_MAP_GROUP;
		public static final String MAP_PRIORITY = SnomedRf2Headers.FIELD_MAP_PRIORITY;
		public static final String MAP_RULE = SnomedRf2Headers.FIELD_MAP_RULE;
		public static final String MAP_ADVICE = SnomedRf2Headers.FIELD_MAP_ADVICE;
		public static final String MAP_CATEGORY_ID = SnomedRf2Headers.FIELD_MAP_CATEGORY_ID;
		public static final String CORRELATION_ID = SnomedRf2Headers.FIELD_CORRELATION_ID;
		
		// Description format
		public static final String DESCRIPTION_FORMAT = SnomedRf2Headers.FIELD_DESCRIPTION_FORMAT;
		public static final String DESCRIPTION_LENGTH = SnomedRf2Headers.FIELD_DESCRIPTION_LENGTH;
		
		// Query type
		public static final String QUERY = SnomedRf2Headers.FIELD_QUERY;
		
		// Concrete domain type
		public static final String RELATIONSHIP_GROUP = SnomedRf2Headers.FIELD_RELATIONSHIP_GROUP;
		public static final String TYPE_ID = SnomedRf2Headers.FIELD_TYPE_ID;
		public static final String CHARACTERISTIC_TYPE_ID = SnomedRf2Headers.FIELD_CHARACTERISTIC_TYPE_ID;
		public static final String DATA_TYPE = "dataType";
		public static final String SERIALIZED_VALUE = SnomedRf2Headers.FIELD_VALUE;
		public static final String BOOLEAN_VALUE = "booleanValue";
		public static final String STRING_VALUE = "stringValue";
		public static final String INTEGER_VALUE = "integerValue";
		public static final String DECIMAL_VALUE = "decimalValue";

		// Module dependency type
		public static final String SOURCE_EFFECTIVE_TIME = SnomedRf2Headers.FIELD_SOURCE_EFFECTIVE_TIME;
		public static final String TARGET_EFFECTIVE_TIME = SnomedRf2Headers.FIELD_TARGET_EFFECTIVE_TIME;
		
		// OWL expression type
		public static final String OWL_EXPRESSION = SnomedRf2Headers.FIELD_OWL_EXPRESSION;
		
		// MRCM domain type
		public static final String MRCM_DOMAIN_CONSTRAINT = SnomedRf2Headers.FIELD_MRCM_DOMAIN_CONSTRAINT;
		public static final String MRCM_PARENT_DOMAIN = SnomedRf2Headers.FIELD_MRCM_PARENT_DOMAIN;
		public static final String MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT = SnomedRf2Headers.FIELD_MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT;
		public static final String MRCM_PROXIMAL_PRIMITIVE_REFINEMENT = SnomedRf2Headers.FIELD_MRCM_PROXIMAL_PRIMITIVE_REFINEMENT;
		public static final String MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION = SnomedRf2Headers.FIELD_MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION;
		public static final String MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION = SnomedRf2Headers.FIELD_MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION;
		public static final String MRCM_EDITORIAL_GUIDE_REFERENCE = SnomedRf2Headers.FIELD_MRCM_EDITORIAL_GUIDE_REFERENCE;
		
		// MRCM attribute domain type
		public static final String MRCM_DOMAIN_ID = SnomedRf2Headers.FIELD_MRCM_DOMAIN_ID;
		public static final String MRCM_GROUPED = SnomedRf2Headers.FIELD_MRCM_GROUPED;
		public static final String MRCM_ATTRIBUTE_CARDINALITY = SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_CARDINALITY;
		public static final String MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY = SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY;

		// MRCM attribute range type
		public static final String MRCM_RANGE_CONSTRAINT = SnomedRf2Headers.FIELD_MRCM_RANGE_CONSTRAINT;
		public static final String MRCM_ATTRIBUTE_RULE = SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_RULE;
		
		// Used both in MRCM attribute domain and range reference sets
		public static final String MRCM_RULE_STRENGTH_ID = SnomedRf2Headers.FIELD_MRCM_RULE_STRENGTH_ID;
		public static final String MRCM_CONTENT_TYPE_ID = SnomedRf2Headers.FIELD_MRCM_CONTENT_TYPE_ID;
		
		// MRCM module scope
		public static final String MRCM_RULE_REFSET_ID = SnomedRf2Headers.FIELD_MRCM_RULE_REFSET_ID;
		public static final String CLASS_AXIOM_RELATIONSHIP = "classAxiomRelationships";
		public static final String GCI_AXIOM_RELATIONSHIP = "gciAxiomRelationships";
		
		// Complex map with map block
		public static final String MAP_BLOCK = "mapBlock";
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static Builder builder(final SnomedRefSetMemberIndexEntry source) {
		return builder()
				.id(source.getId())
				.active(source.isActive())
				.effectiveTime(source.getEffectiveTime())
				.released(source.isReleased())
				.moduleId(source.getModuleId())
				.referencedComponentId(source.getReferencedComponentId())
				.referencedComponentType(source.getReferencedComponentType())
				.referenceSetId(source.getReferenceSetId())
				.referenceSetType(source.getReferenceSetType())
				.released(source.isReleased())
				.gciAxiomRelationships(source.getGciAxiomRelationships())
				.classAxiomRelationships(source.getClassAxiomRelationships())
				.fields(source.getAdditionalFields());
	}
	
	public static final Builder builder(final SnomedReferenceSetMember input) {
		final Builder builder = builder()
				.id(input.getId())
				.active(input.isActive())
				.effectiveTime(EffectiveTimes.getEffectiveTime(input.getEffectiveTime()))
				.released(input.isReleased())
				.moduleId(input.getModuleId())
				.referencedComponentId(input.getReferencedComponent().getId())
				.referenceSetId(input.getReferenceSetId())
				.referenceSetType(input.type());
		
		if (input.getReferencedComponent() instanceof SnomedConcept) {
			builder.referencedComponentType(CONCEPT_NUMBER);
		} else if (input.getReferencedComponent() instanceof SnomedDescription) {
			builder.referencedComponentType(DESCRIPTION_NUMBER);
		} else if (input.getReferencedComponent() instanceof SnomedRelationship) {
			builder.referencedComponentType(RELATIONSHIP_NUMBER);
		} else {
			builder.referencedComponentType(TerminologyRegistry.UNSPECIFIED_NUMBER_SHORT);
		}
		
		if (input.getEquivalentOWLRelationships() != null) {
			builder.classAxiomRelationships(input.getEquivalentOWLRelationships());
		} else if (input.getClassOWLRelationships() != null) {
			builder.classAxiomRelationships(input.getClassOWLRelationships());
		} else if (input.getGciOWLRelationships() != null) {
			builder.gciAxiomRelationships(input.getGciOWLRelationships());
		}
		
		for (Entry<String, Object> entry : input.getProperties().entrySet()) {
			final Object value = entry.getValue();
			final String fieldName = entry.getKey();
			// certain RF2 fields can be expanded into full blown representation class, get the ID in this case
			if (value instanceof SnomedCoreComponent) {
				builder.field(fieldName, ((SnomedCoreComponent) value).getId());
			} else {
				builder.field(fieldName, convertValue(entry.getKey(), value));
			}
		}
		
		return builder;
	}
	
	private static Object convertValue(String rf2Field, Object value) {
		switch (rf2Field) {
		case SnomedRf2Headers.FIELD_SOURCE_EFFECTIVE_TIME:
		case SnomedRf2Headers.FIELD_TARGET_EFFECTIVE_TIME:
			return EffectiveTimes.getEffectiveTime((LocalDate) value);
		default: 
			return value;
		}
	}

	public static Collection<SnomedRefSetMemberIndexEntry> from(final Iterable<SnomedReferenceSetMember> refSetMembers) {
		return FluentIterable.from(refSetMembers)
				.transform(refSetMember -> builder(refSetMember).build())
				.toList();
	}

	public static final class Expressions extends SnomedDocument.Expressions {
		
		public static Expression referenceSetId(String referenceSetId) {
			return exactMatch(Fields.REFERENCE_SET_ID, referenceSetId);
		}

		public static Expression referenceSetId(Collection<String> referenceSetIds) {
			return matchAny(Fields.REFERENCE_SET_ID, referenceSetIds);
		}
		
		public static Expression referencedComponentId(String referencedComponentId) {
			return exactMatch(Fields.REFERENCED_COMPONENT_ID, referencedComponentId);
		}
		
		public static Expression mapTargets(Collection<String> mapTargets) {
			return matchAny(Fields.MAP_TARGET, mapTargets);
		}
		
		public static Expression mapTargetDescriptions(Collection<String> mapTargetDescriptions) {
			return matchAny(Fields.MAP_TARGET_DESCRIPTION, mapTargetDescriptions);
		}
		
		public static Expression mapGroups(Collection<Integer> mapGroups) {
			return matchAnyInt(Fields.MAP_GROUP, mapGroups);
		}
		
		public static Expression mapPriority(Collection<Integer> mapPriorities) {
			return matchAnyInt(Fields.MAP_PRIORITY, mapPriorities);
		}

		public static Expression mapBlock(Collection<Integer> mapBlocks) {
			return matchAnyInt(Fields.MAP_BLOCK, mapBlocks);
		}
		
		public static Expression referencedComponentTypes(Collection<Short> referencedComponentTypes) {
			return matchAnyInt(Fields.REFERENCED_COMPONENT_TYPE, referencedComponentTypes.stream().map(Short::intValue).collect(Collectors.toSet()));
		}
		
		public static Expression referencedComponentIds(Collection<String> referencedComponentIds) {
			return matchAny(Fields.REFERENCED_COMPONENT_ID, referencedComponentIds);
		}
		
		public static Expression targetComponents(Collection<String> targetComponentIds) {
			return matchAny(Fields.TARGET_COMPONENT, targetComponentIds);
		}
		
		public static Expression acceptabilityIds(Collection<String> acceptabilityIds) {
			return matchAny(Fields.ACCEPTABILITY_ID, acceptabilityIds);
		}
		
		public static Expression characteristicTypeIds(Collection<String> characteristicTypeIds) {
			return matchAny(Fields.CHARACTERISTIC_TYPE_ID, characteristicTypeIds);
		}
		
		public static Expression characteristicTypeId(String characteristicTypeId) {
			return exactMatch(Fields.CHARACTERISTIC_TYPE_ID, characteristicTypeId);
		}
		
		public static Expression correlationIds(Collection<String> correlationIds) {
			return matchAny(Fields.CORRELATION_ID, correlationIds);
		}
		
		public static Expression descriptionFormats(Collection<String> descriptionFormats) {
			return matchAny(Fields.DESCRIPTION_FORMAT, descriptionFormats);
		}
		
		public static Expression mapCategoryIds(Collection<String> mapCategoryIds) {
			return matchAny(Fields.MAP_CATEGORY_ID, mapCategoryIds);
		}
		
		public static Expression valueIds(Collection<String> valueIds) {
			return matchAny(Fields.VALUE_ID, valueIds);
		}
		
		public static Expression domainIds(Collection<String> domainIds) {
			return matchAny(Fields.MRCM_DOMAIN_ID, domainIds);
		}
		
		public static Expression contentTypeIds(Collection<String> contentTypeIds) {
			return matchAny(Fields.MRCM_CONTENT_TYPE_ID, contentTypeIds);
		}
		
		public static Expression ruleStrengthIds(Collection<String> strengthIds) {
			return matchAny(Fields.MRCM_RULE_STRENGTH_ID, strengthIds);
		}
		
		public static Expression ruleRefSetIds(Collection<String> refSetIds) {
			return matchAny(Fields.MRCM_RULE_REFSET_ID, refSetIds);
		}
		
		public static Expression grouped(boolean grouped) {
			return match(Fields.MRCM_GROUPED, grouped);
		}
		
		public static Expression rangeConstraint(String rangeConstraint) {
			return exactMatch(Fields.MRCM_RANGE_CONSTRAINT, rangeConstraint);
		}
		
		public static Expression values(DataType type, Collection<? extends Object> values) {
			switch (type) {
			case BOOLEAN:
				if (values.size() > 1) {
					throw new BadRequestException("Only one boolean filter value (either true or false) is allowed. Got: %s", values);
				}
				return match(Fields.BOOLEAN_VALUE, (Boolean) Iterables.getOnlyElement(values));
			case STRING: 
				return matchAny(Fields.STRING_VALUE, FluentIterable.from(values).filter(String.class).toSet());
			case INTEGER:
				return matchAnyInt(Fields.INTEGER_VALUE, FluentIterable.from(values).filter(Integer.class).toSet());
			case DECIMAL:
				return matchAnyDecimal(Fields.DECIMAL_VALUE, FluentIterable.from(values).filter(BigDecimal.class).toSet());
			default:
				throw new UnsupportedOperationException("Unsupported data type when filtering by values, " + type);
			}
		}
		
		public static Expression valueRange(DataType type, final Object lower, final Object upper, boolean includeLower, boolean includeUpper) {
			switch (type) {
			case STRING: 
				return matchRange(Fields.STRING_VALUE, (String) lower, (String) upper, includeLower, includeUpper);
			case INTEGER:
				return matchRange(Fields.INTEGER_VALUE, (Integer) lower, (Integer) upper, includeLower, includeUpper);
			case DECIMAL:
				return matchRange(Fields.DECIMAL_VALUE, (BigDecimal) lower, (BigDecimal) upper, includeLower, includeUpper);
			default:
				throw new UnsupportedOperationException("Unsupported data type when filtering by values, " + type);
			}
		}
		
		public static Expression dataTypes(Collection<DataType> dataTypes) {
			return matchAny(Fields.DATA_TYPE, FluentIterable.from(dataTypes)
					.transform(DataType::name)
					.toSet());
		}
		
		public static Expression relationshipGroup(int relationshipGroup) {
			return match(Fields.RELATIONSHIP_GROUP, relationshipGroup);
		}
		
		public static Expression relationshipGroup(int relationshipGroupStart, int relationshipGroupEnd) {
			checkArgument(relationshipGroupStart <= relationshipGroupEnd, "Group end should be greater than or equal to groupStart");
			if (relationshipGroupStart == relationshipGroupEnd) {
				return relationshipGroup(relationshipGroupStart);
			} else {
				return matchRange(Fields.RELATIONSHIP_GROUP, relationshipGroupStart, relationshipGroupEnd);
			}
		}
		
		public static Expression typeIds(Collection<String> typeIds) {
			return matchAny(Fields.TYPE_ID, typeIds);
		}
		
		public static Expression sourceEffectiveTime(long effectiveTime) {
			return exactMatch(Fields.SOURCE_EFFECTIVE_TIME, effectiveTime);
		}
		
		public static Expression targetEffectiveTime(long effectiveTime) {
			return exactMatch(Fields.TARGET_EFFECTIVE_TIME, effectiveTime);
		}
		
		public static Expression refSetTypes(Collection<SnomedRefSetType> refSetTypes) {
			return matchAny(Fields.REFSET_TYPE, FluentIterable.from(refSetTypes).transform(type -> type.name()).toSet());
		}
		
		public static Expression mrcmGrouped(boolean mrcmGrouped) {
			return match(Fields.MRCM_GROUPED, mrcmGrouped);
		}

		public static Expression gciAxiom(boolean isGci) {
			Expression nestedQuery = nestedMatch("gciAxiomRelationships", exists("typeId"));
			if (isGci) {
				return nestedQuery;
			} else {
				final ExpressionBuilder query = com.b2international.index.query.Expressions.builder();
				query.mustNot(nestedQuery);
				return query.build();
			}
		}

		public static Expression owlExpressionConcept(String...conceptIds) {
			return owlExpressionConcept(ImmutableSet.copyOf(conceptIds));
		}
		
		public static Expression owlExpressionConcept(Iterable<String> conceptIds) {
			final ExpressionBuilder query = com.b2international.index.query.Expressions.builder();
			query.should(nestedMatch("classAxiomRelationships", com.b2international.index.query.Expressions.builder()
					.should(matchAny("typeId", conceptIds))
					.should(matchAny("destinationId", conceptIds))
					.build()));
			query.should(nestedMatch("gciAxiomRelationships", com.b2international.index.query.Expressions.builder()
					.should(matchAny("typeId", conceptIds))
					.should(matchAny("destinationId", conceptIds))
					.build()));
			return query.build();
		}
		
		public static Expression owlExpressionHasDestinationId() {
			final ExpressionBuilder query = com.b2international.index.query.Expressions.builder();
			query.should(nestedMatch("classAxiomRelationships", exists("destinationId")));
			query.should(nestedMatch("gciAxiomRelationships", exists("destinationId")));
			return query.build();
		}
		
		public static Expression owlExpressionDestination(Iterable<String> destinationIds) {
			final ExpressionBuilder query = com.b2international.index.query.Expressions.builder();
			query.should(nestedMatch("classAxiomRelationships", matchAny("destinationId", destinationIds)));
			query.should(nestedMatch("gciAxiomRelationships", matchAny("destinationId", destinationIds)));
			return query.build();
		}
		
		public static Expression owlExpressionType(Iterable<String> typeIds) {
			final ExpressionBuilder query = com.b2international.index.query.Expressions.builder();
			query.should(nestedMatch("classAxiomRelationships", matchAny("typeId", typeIds)));
			query.should(nestedMatch("gciAxiomRelationships", matchAny("typeId", typeIds)));
			return query.build();
		}
		
	}

	@JsonPOJOBuilder(withPrefix="")
	public static final class Builder extends SnomedDocument.Builder<Builder, SnomedRefSetMemberIndexEntry> {

		private String referencedComponentId;

		private String referenceSetId;
		private SnomedRefSetType referenceSetType;
		private short referencedComponentType = TerminologyRegistry.UNSPECIFIED_NUMBER_SHORT;

		// Member specific fields, they can be null or emptyish values
		// ASSOCIATION reference set members
		private String targetComponent;
		// ATTRIBUTE VALUE
		private String valueId;
		// CONCRETE DOMAIN reference set members
		private DataType dataType;
		private Object value;
		private Integer relationshipGroup;
		private String typeId;
		private String characteristicTypeId;
		// DESCRIPTION
		private Integer descriptionLength;
		private String descriptionFormat;
		// LANGUAGE
		private String acceptabilityId;
		// MODULE
		private Long sourceEffectiveTime;
		private Long targetEffectiveTime;
		// SIMPLE MAP reference set members
		private String mapTarget;
		private String mapTargetDescription;
		// COMPLEX MAP
		private String mapCategoryId;
		private String correlationId;
		private String mapAdvice;
		private String mapRule;
		private Integer mapGroup;
		private Integer mapPriority;
		// QUERY
		private String query;
		// OWL Axiom
		private String owlExpression;
		private List<SnomedOWLRelationshipDocument> classAxiomRelationships;
		private List<SnomedOWLRelationshipDocument> gciAxiomRelationships;
		// MRCM Domain
		private String domainConstraint;
		private String parentDomain;
		private String proximalPrimitiveConstraint;
		private String proximalPrimitiveRefinement;
		private String domainTemplateForPrecoordination;
		private String domainTemplateForPostcoordination;
		private String editorialGuideReference;
		// MRCM Attribute Domain		
		private String domainId;
		private Boolean grouped;
		private String attributeCardinality;
		private String attributeInGroupCardinality;
		private String ruleStrengthId;
		private String contentTypeId;
		// MRCM Attribute Range
		private String rangeConstraint;
		private String attributeRule;
		// MRCM Module Scope
		private String mrcmRuleRefsetId;
		// Complex map with map block
		private Integer mapBlock;

		@JsonCreator
		private Builder() {
			// Disallow instantiation outside static method
		}

		public Builder fields(Map<String, Object> fields) {
			for (Entry<String, Object> entry : fields.entrySet()) {
				field(entry.getKey(), entry.getValue());
			}
			return this;
		}
		
		public Builder field(String fieldName, Object value) {
			switch (fieldName) {
			case Fields.ACCEPTABILITY_ID: this.acceptabilityId = (String) value; break;
			case Fields.RELATIONSHIP_GROUP: this.relationshipGroup = (Integer) value; break;
			case Fields.TYPE_ID: this.typeId = (String) value; break;
			case Fields.CHARACTERISTIC_TYPE_ID: this.characteristicTypeId = (String) value; break;
			case Fields.CORRELATION_ID: this.correlationId = (String) value; break;
			case Fields.DATA_TYPE: this.dataType = (DataType) value; break;
			case Fields.SERIALIZED_VALUE: this.value = value; break;
			case Fields.DESCRIPTION_FORMAT: this.descriptionFormat = (String) value; break;
			case Fields.DESCRIPTION_LENGTH: this.descriptionLength = (Integer) value; break;
			case Fields.MAP_ADVICE: this.mapAdvice = (String) value; break;
			case Fields.MAP_CATEGORY_ID: this.mapCategoryId = (String) value; break;
			case Fields.MAP_GROUP: this.mapGroup = (Integer) value; break;
			case Fields.MAP_PRIORITY: this.mapPriority = (Integer) value; break;
			case Fields.MAP_RULE: this.mapRule = (String) value; break;
			case Fields.MAP_TARGET: this.mapTarget = (String) value; break;
			case Fields.MAP_TARGET_DESCRIPTION: this.mapTargetDescription = (String) value; break;
			case Fields.QUERY: this.query = (String) value; break;
			case Fields.SOURCE_EFFECTIVE_TIME: this.sourceEffectiveTime = (Long) value; break;
			case Fields.TARGET_COMPONENT: this.targetComponent = (String) value; break;
			case Fields.TARGET_EFFECTIVE_TIME: this.targetEffectiveTime = (Long) value; break;
			case Fields.VALUE_ID: this.valueId = (String) value; break;
			case Fields.OWL_EXPRESSION: this.owlExpression = (String) value; break;
			
			case Fields.MRCM_DOMAIN_CONSTRAINT: this.domainConstraint = (String) value; break;
			case Fields.MRCM_PARENT_DOMAIN: this.parentDomain = (String) value; break;
			case Fields.MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT: this.proximalPrimitiveConstraint = (String) value; break;
			case Fields.MRCM_PROXIMAL_PRIMITIVE_REFINEMENT: this.proximalPrimitiveRefinement = (String) value; break;
			case Fields.MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION: this.domainTemplateForPrecoordination = (String) value; break;
			case Fields.MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION: this.domainTemplateForPostcoordination = (String) value; break;
			case Fields.MRCM_EDITORIAL_GUIDE_REFERENCE: this.editorialGuideReference = (String) value; break;
			
			case Fields.MRCM_DOMAIN_ID: this.domainId = (String) value; break;
			case Fields.MRCM_GROUPED: this.grouped = (Boolean) value; break;
			case Fields.MRCM_ATTRIBUTE_CARDINALITY: this.attributeCardinality = (String) value; break;
			case Fields.MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY: this.attributeInGroupCardinality = (String) value; break;
			case Fields.MRCM_RULE_STRENGTH_ID: this.ruleStrengthId = (String) value; break;
			case Fields.MRCM_CONTENT_TYPE_ID: this.contentTypeId = (String) value; break;
			
			case Fields.MRCM_RANGE_CONSTRAINT: this.rangeConstraint = (String) value; break;
			case Fields.MRCM_ATTRIBUTE_RULE: this.attributeRule = (String) value; break;

			case Fields.MRCM_RULE_REFSET_ID: this.mrcmRuleRefsetId = (String) value; break;
			
			case Fields.MAP_BLOCK: this.mapBlock = (Integer) value; break;
			
			default: throw new UnsupportedOperationException("Unknown RF2 member field: " + fieldName);
			}
			return this;
		}

		@Override
		protected Builder getSelf() {
			return this;
		}

		public Builder referencedComponentId(final String referencedComponentId) {
			this.referencedComponentId = referencedComponentId;
			return this;
		}

		public Builder referenceSetId(final String referenceSetId) {
			this.referenceSetId = referenceSetId;
			return this;
		}

		public Builder referenceSetType(final SnomedRefSetType referenceSetType) {
			this.referenceSetType = referenceSetType;
			return this;
		}

		public Builder targetComponent(String targetComponent) {
			this.targetComponent = targetComponent;
			return this;
		}
		
		public Builder referencedComponentType(final short referencedComponentType) {
			this.referencedComponentType = referencedComponentType;
			return this;
		}
		
		public Builder acceptabilityId(String acceptabilityId) {
			this.acceptabilityId = acceptabilityId;
			return getSelf();
		}
		
		public Builder relationshipGroup(Integer relationshipGroup) {
			this.relationshipGroup = relationshipGroup;
			return getSelf();
		}
		
		public Builder typeId(String typeId) {
			this.typeId = typeId;
			return getSelf();
		}
		
		public Builder characteristicTypeId(final String characteristicTypeId) {
			this.characteristicTypeId = characteristicTypeId;
			return getSelf();
		}
		
		public Builder correlationId(final String correlationId) {
			this.correlationId = correlationId;
			return getSelf();
		}
		
		public Builder dataType(final DataType dataType) {
			this.dataType = dataType;
			return getSelf();
		}
		
		public Builder descriptionFormat(final String descriptionFormat) {
			this.descriptionFormat = descriptionFormat;
			return getSelf();
		}
		
		public Builder descriptionLength(final Integer descriptionLength) {
			this.descriptionLength = descriptionLength;
			return getSelf();
		}
		
		public Builder mapAdvice(final String mapAdvice) {
			this.mapAdvice = mapAdvice;
			return getSelf();
		}
		
		public Builder mapCategoryId(final String mapCategoryId) {
			this.mapCategoryId = mapCategoryId;
			return getSelf();
		}
		
		public Builder mapGroup(final Integer mapGroup) {
			this.mapGroup = mapGroup;
			return getSelf();
		}
		
		public Builder mapPriority(final Integer mapPriority) {
			this.mapPriority = mapPriority;
			return getSelf();
		}
		
		public Builder mapRule(final String mapRule) {
			this.mapRule = mapRule;
			return getSelf();
		}
		
		public Builder mapTarget(final String mapTarget) {
			this.mapTarget = mapTarget;
			return getSelf();
		}
		
		public Builder mapTargetDescription(final String mapTargetDescription) {
			this.mapTargetDescription = mapTargetDescription;
			return getSelf();
		}
		
		public Builder query(final String query) {
			this.query = query;
			return getSelf();
		}
		
		public Builder sourceEffectiveTime(final Long sourceEffectiveTime) {
			this.sourceEffectiveTime = sourceEffectiveTime;
			return getSelf();
		}
		
		public Builder targetEffectiveTime(final Long targetEffectiveTime) {
			this.targetEffectiveTime = targetEffectiveTime;
			return getSelf();
		}
		
		/**
		 * @deprecated - this is no longer a valid refset member index field, but required to make pre-5.4 dataset work with 5.4 without migration
		 */
		Builder value(final Object value) {
			this.value = value;
			return getSelf();
		}
		
		public Builder decimalValue(final BigDecimal value) {
			this.value = value;
			return getSelf();
		}
		
		public Builder booleanValue(final Boolean value) {
			this.value = value;
			return getSelf();
		}
		
		public Builder integerValue(final Integer value) {
			this.value = value;
			return getSelf();
		}
		
		public Builder stringValue(final String value) {
			this.value = value;
			return getSelf();
		}
		
		public Builder valueId(String valueId) {
			this.valueId = valueId;
			return getSelf();
		}
		
		public Builder owlExpression(String owlExpression) {
			this.owlExpression = owlExpression;
			return getSelf();
		}
		
		public Builder classAxiomRelationships(List<SnomedOWLRelationshipDocument> classAxiomRelationships) {
			this.classAxiomRelationships = Collections3.toImmutableList(classAxiomRelationships);
			return getSelf();
		}
		
		public Builder gciAxiomRelationships(List<SnomedOWLRelationshipDocument> gciAxiomRelationships) {
			this.gciAxiomRelationships = Collections3.toImmutableList(gciAxiomRelationships);
			return getSelf();
		}
		
		public Builder domainConstraint(String domainConstraint) {
			this.domainConstraint = domainConstraint;
			return getSelf();
		}
		
		public Builder parentDomain(String parentDomain) {
			this.parentDomain = parentDomain;
			return getSelf();
		}
		
		public Builder proximalPrimitiveConstraint(String proximalPrimitiveConstraint) {
			this.proximalPrimitiveConstraint = proximalPrimitiveConstraint;
			return getSelf();
		}
		
		public Builder proximalPrimitiveRefinement(String proximalPrimitiveRefinement) {
			this.proximalPrimitiveRefinement = proximalPrimitiveRefinement;
			return getSelf();
		}
		
		public Builder domainTemplateForPrecoordination(String domainTemplateForPrecoordination) {
			this.domainTemplateForPrecoordination = domainTemplateForPrecoordination;
			return getSelf();
		}
		
		public Builder domainTemplateForPostcoordination(String domainTemplateForPostcoordination) {
			this.domainTemplateForPostcoordination = domainTemplateForPostcoordination;
			return getSelf();
		}
		
		public Builder editorialGuideReference(String editorialGuideReference) {
			this.editorialGuideReference = editorialGuideReference;
			return getSelf();
		}
		
		public Builder domainId(String domainId) {
			this.domainId = domainId;
			return getSelf();
		}
		
		public Builder grouped(Boolean grouped) {
			this.grouped = grouped;
			return getSelf();
		}
		
		public Builder attributeCardinality(String attributeCardinality) {
			this.attributeCardinality = attributeCardinality;
			return getSelf();
		}
		
		public Builder attributeInGroupCardinality(String attributeInGroupCardinality) {
			this.attributeInGroupCardinality = attributeInGroupCardinality;
			return getSelf();
		}
		
		public Builder ruleStrengthId(String ruleStrengthId) {
			this.ruleStrengthId = ruleStrengthId;
			return getSelf();
		}
		
		public Builder contentTypeId(String contentTypeId) {
			this.contentTypeId = contentTypeId;
			return getSelf();
		}
		
		public Builder rangeConstraint(String rangeConstraint) {
			this.rangeConstraint = rangeConstraint;
			return getSelf();
		}
		
		public Builder attributeRule(String attributeRule) {
			this.attributeRule = attributeRule;
			return getSelf();
		}
		
		public Builder mrcmRuleRefsetId(String mrcmRuleRefsetId) {
			this.mrcmRuleRefsetId = mrcmRuleRefsetId;
			return getSelf();
		}
		
		public Builder mapBlock(final Integer mapBlock) {
			this.mapBlock = mapBlock;
			return getSelf();
		}
		
		public SnomedRefSetMemberIndexEntry build() {
			final SnomedRefSetMemberIndexEntry doc = new SnomedRefSetMemberIndexEntry(id,
					label,
					moduleId, 
					released, 
					active, 
					effectiveTime, 
					referencedComponentId, 
					referenceSetId,
					referenceSetType,
					referencedComponentType);
			// association members
			doc.targetComponent = targetComponent;
			// attribute value
			doc.valueId = valueId;
			// concrete domain members
			doc.dataType = dataType;
			doc.typeId = typeId;
			if (dataType != null) {
				switch (dataType) {
				case BOOLEAN:
					if (value instanceof Boolean) {
						doc.booleanValue = (Boolean) value;
					} else if (value instanceof String) {
						doc.booleanValue = SnomedRefSetUtil.deserializeValue(dataType, (String) value);
					}
					break;
				case DECIMAL:
					if (value instanceof BigDecimal) {
						doc.decimalValue = (BigDecimal) value;
					} else if (value instanceof String) {
						doc.decimalValue = SnomedRefSetUtil.deserializeValue(dataType, (String) value);
					}
					break;
				case INTEGER:
					if (value instanceof Integer) {
						doc.integerValue = (Integer) value;
					} else if (value instanceof String) {
						doc.integerValue = SnomedRefSetUtil.deserializeValue(dataType, (String) value);
					}
					break;
				case STRING:
					doc.stringValue = (String) value;
					break;
				default: throw new UnsupportedOperationException("Unsupported concrete domain data type: " + dataType);
				}
			}
			doc.relationshipGroup = relationshipGroup;
			doc.characteristicTypeId = characteristicTypeId;
			// description
			doc.descriptionFormat = descriptionFormat;
			doc.descriptionLength = descriptionLength;
			// language reference set
			doc.acceptabilityId = acceptabilityId;
			// module
			doc.sourceEffectiveTime = sourceEffectiveTime;
			doc.targetEffectiveTime = targetEffectiveTime;
			// simple map
			doc.mapTarget = mapTarget;
			doc.mapTargetDescription = mapTargetDescription;
			// complex map
			doc.mapCategoryId = mapCategoryId;
			doc.mapAdvice = mapAdvice;
			doc.correlationId = correlationId;
			doc.mapGroup = mapGroup;
			doc.mapPriority = mapPriority;
			doc.mapRule = mapRule;
			// query
			doc.query = query;
			// OWL Axiom
			doc.owlExpression = owlExpression;
			doc.classAxiomRelationships = classAxiomRelationships;
			doc.gciAxiomRelationships = gciAxiomRelationships;
			
			// MRCM Domain
			doc.domainConstraint = domainConstraint;
			doc.parentDomain = parentDomain;
			doc.proximalPrimitiveConstraint = proximalPrimitiveConstraint;
			doc.proximalPrimitiveRefinement = proximalPrimitiveRefinement;
			doc.domainTemplateForPrecoordination = domainTemplateForPrecoordination;
			doc.domainTemplateForPostcoordination = domainTemplateForPostcoordination;
			doc.editorialGuideReference = editorialGuideReference;
			
			// MRCM Attribute Domain
			doc.domainId = domainId;
			doc.grouped = grouped;
			doc.attributeCardinality = attributeCardinality;
			doc.attributeInGroupCardinality = attributeInGroupCardinality;
			doc.ruleStrengthId = ruleStrengthId;
			doc.contentTypeId = contentTypeId;
			
			// MRCM Attribute Range			
			doc.rangeConstraint = rangeConstraint;
			doc.attributeRule = attributeRule;
			
			// MRCM Module Scope
			doc.mrcmRuleRefsetId = mrcmRuleRefsetId;
			
			// Complex map with map block
			doc.mapBlock = mapBlock;
			
			doc.setScore(score);
			return doc;
		}
	}

	private final String referencedComponentId;
	private final String referenceSetId;
	private final SnomedRefSetType referenceSetType;
	private final short referencedComponentType;
	
	// Member specific fields, they can be null or emptyish values
	// ASSOCIATION reference set members
	private String targetComponent;
	// ATTRIBUTE VALUE
	private String valueId;
	// CONCRETE DOMAIN reference set members
	private DataType dataType;
	
	// only one of these value fields should be set when this represents a concrete domain member
	private String stringValue;
	private Boolean booleanValue;
	private Integer integerValue;
	private BigDecimal decimalValue;

	private Integer relationshipGroup;
	private String typeId;
	private String characteristicTypeId;
	
	// DESCRIPTION
	private Integer descriptionLength;
	private String descriptionFormat;
	// LANGUAGE
	private String acceptabilityId;
	// MODULE
	private Long sourceEffectiveTime;
	private Long targetEffectiveTime;
	// SIMPLE MAP reference set members
	private String mapTarget;
	private String mapTargetDescription;
	// COMPLEX MAP
	private String mapCategoryId;
	private String correlationId;
	private String mapAdvice;
	private String mapRule;
	private Integer mapGroup;
	private Integer mapPriority;
	// QUERY
	@Keyword(index = false)
	private String query;
	// OWL Axiom
	private String owlExpression;
	private List<SnomedOWLRelationshipDocument> classAxiomRelationships;
	private List<SnomedOWLRelationshipDocument> gciAxiomRelationships;

	// MRCM Domain
	private String domainConstraint;
	private String parentDomain;
	private String proximalPrimitiveConstraint;
	private String proximalPrimitiveRefinement;
	private String domainTemplateForPrecoordination;
	private String domainTemplateForPostcoordination;
	private String editorialGuideReference;
	// MRCM Attribute Domain		
	private String domainId;
	private Boolean grouped;
	private String attributeCardinality;
	private String attributeInGroupCardinality;
	private String ruleStrengthId;
	private String contentTypeId;
	// MRCM Attribute Range
	private String rangeConstraint;
	private String attributeRule;
	// MRCM Module Scope
	private String mrcmRuleRefsetId;
	// Complex map with map block
	private Integer mapBlock;

	private SnomedRefSetMemberIndexEntry(final String id,
			final String label,
			final String moduleId, 
			final Boolean released,
			final Boolean active, 
			final Long effectiveTimeLong, 
			final String referencedComponentId, 
			final String referenceSetId,
			final SnomedRefSetType referenceSetType,
			final short referencedComponentType) {

		super(id, 
				label,
				referencedComponentId, // XXX: iconId is the referenced component identifier
				moduleId, 
				released, 
				active, 
				effectiveTimeLong);
		this.referencedComponentId = referencedComponentId;
		this.referenceSetId = referenceSetId;
		this.referenceSetType = referenceSetType;
		checkArgument(referencedComponentType >= TerminologyRegistry.UNSPECIFIED_NUMBER_SHORT, "Referenced component type '%s' is invalid.", referencedComponentType);
		if (!Strings.isNullOrEmpty(referencedComponentId)) {
			this.referencedComponentType = referencedComponentType == TerminologyRegistry.UNSPECIFIED_NUMBER_SHORT ? SnomedTerminologyComponentConstants.getTerminologyComponentIdValue(referencedComponentId) : referencedComponentType;
		} else {
			this.referencedComponentType = referencedComponentType;
		}
	}
	
	@Override
	protected Revision.Builder<?, ? extends Revision> toBuilder() {
		return builder(this);
	}

	@Override
	public ObjectId getContainerId() {
		return ObjectId.of(getReferencedComponentDocClass(), getReferencedComponentId());
	}

	@JsonIgnore
	Class<?> getReferencedComponentDocClass() {
		switch (referencedComponentType) {
		case SnomedTerminologyComponentConstants.REFSET_NUMBER:
		case SnomedTerminologyComponentConstants.CONCEPT_NUMBER: return SnomedConceptDocument.class;
		case SnomedTerminologyComponentConstants.DESCRIPTION_NUMBER: return SnomedDescriptionIndexEntry.class;
		case SnomedTerminologyComponentConstants.RELATIONSHIP_NUMBER: return SnomedRelationshipIndexEntry.class;
		default: throw new UnsupportedOperationException("Cannot get doc class for referenced component type: " + referencedComponentType);
		}
	}

	/**
	 * @return the referenced component identifier
	 */
	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	/**
	 * @return the identifier of the member's reference set
	 */
	public String getReferenceSetId() {
		return referenceSetId;
	}

	/**
	 * @return the type of the member's reference set
	 */
	public SnomedRefSetType getReferenceSetType() {
		return referenceSetType;
	}

	@JsonIgnore
	@SuppressWarnings("unchecked")
	public <T> T getValueAs() {
		return (T) getValue();
	}
	
	@JsonIgnore
	public Object getValue() {
		if (dataType == null) {
			return null;
		} else {
			switch (dataType) {
			case BOOLEAN: return booleanValue;
			case DECIMAL: return decimalValue;
			case INTEGER: return integerValue;
			case STRING: return stringValue;
			default: throw new UnsupportedOperationException("Unsupported concrete domain data type: " + dataType);
			}
		}
	}
	
	@JsonProperty
	BigDecimal getDecimalValue() {
		return decimalValue;
	}
	
	@JsonProperty
	Boolean getBooleanValue() {
		return booleanValue;
	}
	
	@JsonProperty
	Integer getIntegerValue() {
		return integerValue;
	}
	
	@JsonProperty
	String getStringValue() {
		return stringValue;
	}

	public DataType getDataType() {
		return dataType;
	}
	
	public Integer getRelationshipGroup() {
		return relationshipGroup;
	}

	public String getTypeId() {
		return typeId;
	}

	public String getCharacteristicTypeId() {
		return characteristicTypeId;
	}	

	public String getAcceptabilityId() {
		return acceptabilityId;
	}

	public Integer getDescriptionLength() {
		return descriptionLength;
	}
	
	public String getDescriptionFormat() {
		return descriptionFormat;
	}

	public String getMapTarget() {
		return mapTarget;
	}

	public Integer getMapGroup() {
		return mapGroup;
	}

	public Integer getMapPriority() {
		return mapPriority;
	}

	public String getMapRule() {
		return mapRule;
	}

	public String getMapAdvice() {
		return mapAdvice;
	}
	
	public String getMapCategoryId() {
		return mapCategoryId;
	}
	
	public String getCorrelationId() {
		return correlationId;
	}

	public String getMapTargetDescription() {
		return mapTargetDescription;
	}
	
	public String getQuery() {
		return query;
	}
	
	public String getTargetComponent() {
		return targetComponent;
	}
	
	public String getValueId() {
		return valueId;
	}
	
	public Long getSourceEffectiveTime() {
		return sourceEffectiveTime;
	}
	
	public Long getTargetEffectiveTime() {
		return targetEffectiveTime;
	}
	
	public short getReferencedComponentType() {
		return referencedComponentType;
	}
	
	public String getOwlExpression() {
		return owlExpression;
	}
	
	public List<SnomedOWLRelationshipDocument> getClassAxiomRelationships() {
		return classAxiomRelationships;
	}
	
	public List<SnomedOWLRelationshipDocument> getGciAxiomRelationships() {
		return gciAxiomRelationships;
	}
	
	public String getDomainConstraint() {
		return domainConstraint;
	}
	
	public String getParentDomain() {
		return parentDomain;
	}
	
	public String getProximalPrimitiveConstraint() {
		return proximalPrimitiveConstraint;
	}
	
	public String getProximalPrimitiveRefinement() {
		return proximalPrimitiveRefinement;
	}
	
	public String getDomainTemplateForPrecoordination() {
		return domainTemplateForPrecoordination;
	}
	
	public String getDomainTemplateForPostcoordination() {
		return domainTemplateForPostcoordination;
	}
	
	public String getEditorialGuideReference() {
		return editorialGuideReference;
	}
	
	public String getDomainId() {
		return domainId;
	}
	
	public Boolean isGrouped() {
		return grouped;
	}
	
	public String getAttributeCardinality() {
		return attributeCardinality;
	}
	
	public String getAttributeInGroupCardinality() {
		return attributeInGroupCardinality;
	}
	
	public String getRuleStrengthId() {
		return ruleStrengthId;
	}
	
	public String getContentTypeId() {
		return contentTypeId;
	}
	
	public String getRangeConstraint() {
		return rangeConstraint;
	}
	
	public String getAttributeRule() {
		return attributeRule;
	}
	
	public String getMrcmRuleRefsetId() {
		return mrcmRuleRefsetId;
	}
	
	public Integer getMapBlock() {
		return mapBlock;
	}
	
	// model helper methods

	@JsonIgnore
	public Acceptability getAcceptability() {
		return Acceptability.getByConceptId(getAcceptabilityId());
	}
	
	@JsonIgnore
	public String getSourceEffectiveTimeAsString() {
		return EffectiveTimes.format(getSourceEffectiveTime(), DateFormats.SHORT);
	}
	
	@JsonIgnore
	public String getTargetEffectiveTimeAsString() {
		return EffectiveTimes.format(getTargetEffectiveTime(), DateFormats.SHORT);
	}
	
	/**
	 * @return the {@code String} terminology component identifier of the component referenced in this member
	 */
	@JsonIgnore
	public String getReferencedComponentTypeAsString() {
		return TerminologyRegistry.INSTANCE.getTerminologyComponentByShortId(referencedComponentType).id();
	}

	/**
	 * Helper which converts all non-null/empty additional fields to a values {@link Map} keyed by their field name; 
	 * @return
	 */
	@JsonIgnore
	public Map<String, Object> getAdditionalFields() {
		final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
		// ASSOCIATION refset members
		putIfPresent(builder, Fields.TARGET_COMPONENT, getTargetComponent());
		// ATTRIBUTE_VALUE refset members 
		putIfPresent(builder, Fields.VALUE_ID, getValueId());
		// CONCRETE DOMAIN reference set members
		putIfPresent(builder, Fields.DATA_TYPE, getDataType());
		putIfPresent(builder, Fields.RELATIONSHIP_GROUP, getRelationshipGroup());
		putIfPresent(builder, Fields.TYPE_ID, getTypeId());
		putIfPresent(builder, Fields.SERIALIZED_VALUE, getValue());
		putIfPresent(builder, Fields.CHARACTERISTIC_TYPE_ID, getCharacteristicTypeId());
		// DESCRIPTION
		putIfPresent(builder, Fields.DESCRIPTION_LENGTH, getDescriptionLength());
		putIfPresent(builder, Fields.DESCRIPTION_FORMAT, getDescriptionFormat());
		// LANGUAGE
		putIfPresent(builder, Fields.ACCEPTABILITY_ID, getAcceptabilityId());
		// MODULE
		putIfPresent(builder, Fields.SOURCE_EFFECTIVE_TIME, getSourceEffectiveTime());
		putIfPresent(builder, Fields.TARGET_EFFECTIVE_TIME, getTargetEffectiveTime());
		// SIMPLE MAP reference set members
		putIfPresent(builder, Fields.MAP_TARGET, getMapTarget());
		putIfPresent(builder, Fields.MAP_TARGET_DESCRIPTION, getMapTargetDescription());
		// COMPLEX MAP
		putIfPresent(builder, Fields.MAP_CATEGORY_ID, getMapCategoryId());
		putIfPresent(builder, Fields.CORRELATION_ID, getCorrelationId());
		putIfPresent(builder, Fields.MAP_ADVICE, getMapAdvice());
		putIfPresent(builder, Fields.MAP_RULE, getMapRule());
		putIfPresent(builder, Fields.MAP_GROUP, getMapGroup());
		putIfPresent(builder, Fields.MAP_PRIORITY, getMapPriority());
		// QUERY
		putIfPresent(builder, Fields.QUERY, getQuery());
		// OWL Axiom
		putIfPresent(builder, Fields.OWL_EXPRESSION, getOwlExpression());
		// MRCM Domain
		putIfPresent(builder, Fields.MRCM_DOMAIN_CONSTRAINT, getDomainConstraint());
		putIfPresent(builder, Fields.MRCM_PARENT_DOMAIN, getParentDomain());
		putIfPresent(builder, Fields.MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT, getProximalPrimitiveConstraint());
		putIfPresent(builder, Fields.MRCM_PROXIMAL_PRIMITIVE_REFINEMENT, getProximalPrimitiveRefinement());
		putIfPresent(builder, Fields.MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION, getDomainTemplateForPrecoordination());
		putIfPresent(builder, Fields.MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION, getDomainTemplateForPostcoordination());
		putIfPresent(builder, Fields.MRCM_EDITORIAL_GUIDE_REFERENCE, getEditorialGuideReference());
		// MRCM Attribute Domain
		putIfPresent(builder, Fields.MRCM_DOMAIN_ID, getDomainId());
		putIfPresent(builder, Fields.MRCM_GROUPED, isGrouped());
		putIfPresent(builder, Fields.MRCM_ATTRIBUTE_CARDINALITY, getAttributeCardinality());
		putIfPresent(builder, Fields.MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY, getAttributeInGroupCardinality());
		putIfPresent(builder, Fields.MRCM_RULE_STRENGTH_ID, getRuleStrengthId());
		putIfPresent(builder, Fields.MRCM_CONTENT_TYPE_ID, getContentTypeId());
		// MRCM Attribute Range
		putIfPresent(builder, Fields.MRCM_RANGE_CONSTRAINT, getRangeConstraint());
		putIfPresent(builder, Fields.MRCM_ATTRIBUTE_RULE, getAttributeRule());
		// MRCM Module Scope
		putIfPresent(builder, Fields.MRCM_RULE_REFSET_ID, getMrcmRuleRefsetId());
		// Complex map with map block
		putIfPresent(builder, Fields.MAP_BLOCK, getMapBlock());
		
		return builder.build();
	}
	
	private static void putIfPresent(ImmutableMap.Builder<String, Object> builder, String key, Object value) {
		if (key != null && value != null) {
			builder.put(key, value);
		}
	}
	
	@Override
	protected ToStringHelper doToString() {
		return super.doToString()
				.add("referencedComponentId", referencedComponentId)
				.add("referenceSetId", referenceSetId)
				.add("referenceSetType", referenceSetType)
				.add("referencedComponentType", referencedComponentType)
				.add("targetComponent", targetComponent)
				.add("valueId", valueId)
				.add("dataType", dataType)
				.add("typeId", typeId)
				.add("relationshipGroup", relationshipGroup)
				.add("value", getValue())
				.add("characteristicTypeId", characteristicTypeId)
				.add("descriptionLength", descriptionLength)
				.add("descriptionFormat", descriptionFormat)
				.add("acceptabilityId", acceptabilityId)
				.add("sourceEffectiveTime", sourceEffectiveTime)
				.add("targetEffectiveTime", targetEffectiveTime)
				.add("mapTarget", mapTarget)
				.add("mapTargetDescription", mapTargetDescription)
				.add("mapCategoryId", mapCategoryId)
				.add("correlationId", correlationId)
				.add("mapAdvice", mapAdvice)
				.add("mapRule", mapRule)
				.add("mapGroup", mapGroup)
				.add("mapPriority", mapPriority)
				.add("query", query)
				.add("owlExpression", owlExpression)
				
				.add("domainConstraint", domainConstraint)
				.add("parentDomain", parentDomain)
				.add("proximalPrimitiveConstraint", proximalPrimitiveConstraint)
				.add("proximalPrimitiveRefinement", proximalPrimitiveRefinement)
				.add("domainTemplateForPrecoordination", domainTemplateForPrecoordination)
				.add("domainTemplateForPostcoordination", domainTemplateForPostcoordination)
				.add("editorialGuideReference", editorialGuideReference)
				
				.add("domainId", domainId)
				.add("grouped", grouped)
				.add("attributeCardinality", attributeCardinality)
				.add("attributeInGroupCardinality", attributeInGroupCardinality)
				.add("ruleStrengthId", ruleStrengthId)
				.add("contentTypeId", contentTypeId)
				
				.add("rangeConstraint", rangeConstraint)
				.add("attributeRule", attributeRule)
				
				.add("mrcmRuleRefsetId", mrcmRuleRefsetId)
				
				.add("mapBlock", mapBlock);
		
	}
}
