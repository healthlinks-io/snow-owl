/*
 * Copyright 2011-2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.common;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Constant class for SNOMED CT specific constants.
 * 
 */
public abstract class SnomedConstants {
	// suppress constructor to avoid instantiation 
	private SnomedConstants() { }

	/**
	 * Constant class for frequently used SNOMED CT concept IDs.
	 * 
	 */
	public static abstract class Concepts {

		// suppress constructor to avoid instantiation
		private Concepts() { }
		
		public static final Set<String> DEFINING_CHARACTERISTIC_TYPES = ImmutableSet.of(
				Concepts.DEFINING_RELATIONSHIP, 
				Concepts.STATED_RELATIONSHIP, 
				Concepts.INFERRED_RELATIONSHIP);

		public static final String ROOT_CONCEPT = "138875005";
		public static final String IS_A = "116680003";
		public static final String ATTRIBUTE = "246061005";
		public static final String FINDING_SITE = "363698007";
		public static final String METHOD = "260686004";
		public static final String MORPHOLOGY = "116676008";
		public static final String PROCEDURE_SITE_DIRECT = "405813007";
		public static final String INTERPRETS = "363714003";
		public static final String CAUSATIVE_AGENT = "246075003";
		
		public static final String DEFINITION_STATUS_ROOT = "900000000000444006";
		public static final String FULLY_DEFINED = "900000000000073002";
		public static final String PRIMITIVE = "900000000000074008";
		public static final String CHARACTERISTIC_TYPE = "900000000000449001";
		public static final String DEFINING_RELATIONSHIP = "900000000000006009";
		public static final String QUALIFYING_RELATIONSHIP = "900000000000225001";
		public static final String INFERRED_RELATIONSHIP = "900000000000011006";
		public static final String STATED_RELATIONSHIP = "900000000000010007";
		public static final String ADDITIONAL_RELATIONSHIP = "900000000000227009";
		public static final String MODIFIER_ROOT = "900000000000450001";
		public static final String EXISTENTIAL_RESTRICTION_MODIFIER = "900000000000451002";
		public static final String UNIVERSAL_RESTRICTION_MODIFIER = "900000000000452009";
		public static final String FULLY_SPECIFIED_NAME = "900000000000003001";
		public static final String SYNONYM = "900000000000013009";
		public static final String TEXT_DEFINITION = "900000000000550004";
		public static final String ONLY_INITIAL_CHARACTER_CASE_INSENSITIVE = "900000000000020002";
		public static final String ENTIRE_TERM_CASE_INSENSITIVE = "900000000000448009";	//not used in description snapshot
		public static final String ENTIRE_TERM_CASE_SENSITIVE = "900000000000017005";
		public static final String FOUNDATION_METADATA_CONCEPTS = "900000000000454005";
		public static final String CASE_SIGNIFICANCE_ROOT_CONCEPT = "900000000000447004";
		
		//TODO: These are substitutes for concrete domain operational concepts that WILL be 
		//under the metadata hierarchy.  These need to be replaced once available.  bbanfai - 2012.09.11
		public static final String CD_EQUAL = "276136004";  // =
		public static final String CD_LESS = "276139006";  // <
		public static final String CD_GREATER = "276140008";  // >
		public static final String CD_LESS_OR_EQUAL = "276137008";  // <=
		public static final String CD_GREATER_OR_EQUAL = "276138003";  // >=
		public static final String CD_UNEQUAL = "431878004";  // <>
		

		public static final String TOPLEVEL_METADATA = "900000000000441003";
		public static final String LINKAGE = "106237007";
		public static final String PHARMACEUTICAL = "373873005";
		public static final String PHYSICAL_OBJECT = "260787004";
		public static final String QUALIFIER_VALUE_TOPLEVEL_CONCEPT = "362981000";
		
		public static final String NOT_REFINABLE = "900000000000007000";
		public static final String OPTIONAL_REFINABLE = "900000000000216007";
		public static final String MANDATORY_REFINABLE = "900000000000218008";
		
		public static final String DESCRIPTION_TYPE_ROOT_CONCEPT = "900000000000446008";
		public static final String DESCRIPTION_FORMAT_TYPE_ROOT_CONCEPT = "900000000000539002";
		public static final String DESCRIPTION_FORMAT_PLAIN_TEXT = "900000000000540000";
		
		public static final String DEVICE = "49062001";
		
		//ref sets
		public static final String REFSET_ROOT_CONCEPT = "900000000000455006";
		public static final String REFSET_ALL = REFSET_ROOT_CONCEPT;
		public static final String REFSET_SIMPLE_TYPE = "446609009";	// manually added by the importer to 0531 NEHTA (AU)
		public static final String REFSET_COMPLEX_MAP_TYPE = "447250001";	// manually added by the importer to 0531 NEHTA (AU)
		public static final String EXTENDED_MAP_TYPE = "609331003";
		public static final String REFSET_ATTRIBUTE_VALUE_TYPE = "900000000000480006";
		public static final String REFSET_ASSOCIATION_TYPE = "900000000000521006";
		public static final String REFSET_LANGUAGE_TYPE = "900000000000506000";
		public static final String REFSET_LANGUAGE_TYPE_UK = "900000000000508004";
		public static final String REFSET_LANGUAGE_TYPE_US = "900000000000509007";		
		public static final String REFSET_LANGUAGE_TYPE_SG = "9011000132109";
		public static final String REFSET_LANGUAGE_TYPE_ES = "450828004";
		public static final String REFSET_QUERY_SPECIFICATION_TYPE = "900000000000512005";
		public static final String REFSET_SIMPLE_MAP_FROM_SNOMEDCT_TYPE = "900000000000496009";
		public static final String REFSET_DESCRIPTION_TYPE = "900000000000538005";
		public static final String REFSET_CONCRETE_DOMAIN_TYPE_AU = "50131000036100"; //AU release -> NEHTA_0856_2012_AMTImplentationKit_20120229
		public static final String REFSET_MODULE_DEPENDENCY_TYPE = "900000000000534007";
		public static final String REFSET_SIMPLE_MAP_TO_SNOMEDCT = "1187636009";
		
		public static final String REFSET_DESCRIPTOR_REFSET = "900000000000456007";

		public static final String REFSET_ANNOTATION_TYPE = "900000000000516008";
		public static final String REFSET_OWL_EXPRESSION_TYPE = "762676003";
		public static final String REFSET_OWL_ONTOLOGY = "762103008";
		public static final String REFSET_OWL_AXIOM = "733073007";
		
		public static final String REFSET_MRCM_ROOT = "723564002";
		public static final String REFSET_MRCM_MODULE_SCOPE = "723563008";
		
		public static final String REFSET_MRCM_DOMAIN_ROOT = "723589008";
		public static final String REFSET_MRCM_DOMAIN_INTERNATIONAL = "723560006";
		
		public static final String REFSET_MRCM_ATTRIBUTE_DOMAIN_ROOT = "723604009";
		public static final String REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL = "723561005";
		
		public static final String REFSET_MRCM_ATTRIBUTE_RANGE_ROOT = "723592007";
		public static final String REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL = "723562003";
		
		//CMT reference sets
		public static final String REFSET_B2I_EXAMPLE = "780716481000154104"; //for more details see: https://github.com/b2ihealthcare/snowowl/issues/368
		public static final String REFSET_KP_CONVERGENT_MEDICAL_TERMINOLOGY = "494287621000154107"; //for more details see: https://github.com/b2ihealthcare/snowowl/issues/368
		public static final String REFSET_CORE_PROBLEM_LIST_REFERENCE_SETS = "344562521000154101";
		public static final String REFSET_INFOWAY_PRIMARY_HEALTH_CARE_REFERENCE_SETS = "372749141000154103";
		
		public static final String CARDIOLOGY_REFERENCE_SET = "152725851000154106";
		public static final String ENDOCRINOLOGY_UROLOGY_NEPHROLOGY_REFERENCE_SET = "674330851000154100";
		public static final String HEMATOLOGY_ONCOLOGY_REFERENCE_SET = "291212201000154102";
		public static final String MENTAL_HEALTH_REFERENCE_SET = "599282691000154102";
		public static final String MUSCULOSKELETAL_REFERENCE_SET = "99400311000154108";
		public static final String NEUROLOGY_REFERENCE_SET = "501847791000154106";
		public static final String OPHTHALMOLOGY_REFERENCE_SET = "735316271000154103";
		public static final String PRIMARY_CARE_REFERENCE_SET = "723916301000154101";
		public static final String HISTORY_AND_FAMILY_HISTORY_REFERENCE_SET = "470288881000154105";
		public static final String INJURIES_REFERENCE_SET = "111719501000154107";
		public static final String ORTHOPEDICS_REFERENCE_SET = "461251521000154104";
		public static final String OBSTETRICS_AND_GYNECOLOGY_REFERENCE_SET = "358903761000154109";
		public static final String SKIN_RESPIRATORY_REFERENCE_SET = "832566231000154105";
		public static final String ENT_GASTROINTESTINAL_INFECTIOUS_DISEASES_REFERENCE_SET = "149994661000154106";
		public static final String KP_PROBLEM_LIST_REFERENCE_SET = "376537701000154105";

		public static final Map<String, String> CMT_REFSET_NAME_ID_MAP = ImmutableMap.<String, String>builder()
				.put("Cardiology", CARDIOLOGY_REFERENCE_SET)
				.put("Endocrine, Nephrology, and Urology", ENDOCRINOLOGY_UROLOGY_NEPHROLOGY_REFERENCE_SET)
				.put("ENT, Gastrointestinal, Infectious Diseases", ENT_GASTROINTESTINAL_INFECTIOUS_DISEASES_REFERENCE_SET)
				.put("Hematology and Oncology", HEMATOLOGY_ONCOLOGY_REFERENCE_SET)
				.put("History and Family History", HISTORY_AND_FAMILY_HISTORY_REFERENCE_SET)
				.put("Injuries", INJURIES_REFERENCE_SET)
				.put("KP Problem List", KP_PROBLEM_LIST_REFERENCE_SET)
				.put("Mental Health", MENTAL_HEALTH_REFERENCE_SET)
				.put("Musculoskeletal", MUSCULOSKELETAL_REFERENCE_SET)
				.put("Neurology", NEUROLOGY_REFERENCE_SET)
				.put("Obstetrics and Gynecology", OBSTETRICS_AND_GYNECOLOGY_REFERENCE_SET)
				.put("Ophthalmology", OPHTHALMOLOGY_REFERENCE_SET)
				.put("Orthopedics", ORTHOPEDICS_REFERENCE_SET)
				.put("Primary Care", PRIMARY_CARE_REFERENCE_SET)
				.put("Skin/Dermatology and Respiratory", SKIN_RESPIRATORY_REFERENCE_SET)
				.build();
		
		public static final String SINGAPORE_UNIT_OF_MEASURE_REFERENCE_SET = "492227111000132107";
		public static final String SINGAPORE_EXTENSION_REFERENCE_SET = "843239231000132105";
		public static final String SDD_UNIT_OF_MEASURE_REFERENCE_SET = "62111000133108";
		public static final String SDD_SIMPLE_TYPE_REFERENCE_SET = "69511000133108";

		//concrete domain
		public static final String REFSET_BOOLEAN_DATATYPE = "759160691000154109";
		public static final String REFSET_DATETIME_DATATYPE = "492980241000154105";
		public static final String REFSET_INTEGER_DATATYPE = "373998411000154109";
		public static final String REFSET_FLOAT_DATATYPE = "744104701000154109";
		public static final String REFSET_STRING_DATATYPE = "513945551000154100";
		public static final String REFSET_CONCRETE_DOMAIN_TYPE = "289191171000154104";
		public static final String REFSET_DEFINING_TYPE = "384696201000154108";
		public static final String REFSET_MEASUREMENT_TYPE = "945726341000154109";

		public static final String REFSET_DRUG_TO_SOURCE_DRUG_SIMPLE_MAP = "776245861000133102";
		public static final String REFSET_DRUG_TO_GROUPER_SIMPLE_MAP = "499896751000133109";
		public static final String REFSET_DRUG_TO_PACKAGING_SIMPLE_MAP = "780548781000133105";
		
		// complex map with map block type 
		public static final String REFSET_COMPLEX_BLOCK_MAP_TYPE = "999001671000000105";

		//used for NEHTA AU AMT extension
		/**@deprecated For NEHTA only. {@value}*/
		@Deprecated public static final String REFSET_STRENGTH = "700000111000036105";
		/**@deprecated For NEHTA only. {@value}*/
		@Deprecated public static final String REFSET_UNIT_OF_USE_QUANTITY = "700000131000036101";
		/**@deprecated For NEHTA only. {@value}*/
		@Deprecated public static final String REFSET_UNIT_OF_USE_SIZE = "700000141000036106";
		/**@deprecated For NEHTA only. {@value}*/
		@Deprecated public static final String REFSET_SUBPACK_QUANTITY = "700000121000036103";
		/**@deprecated For NEHTA only. {@value}*/
		@Deprecated public static final String EQUAL_TO = "25311000036102";

		public static final String REFSET_RELATIONSHIP_REFINABILITY = "900000000000488004";
		
		public static final String REFSET_DESCRIPTION_ACCEPTABILITY_ACCEPTABLE = "900000000000549004";
		public static final String REFSET_DESCRIPTION_ACCEPTABILITY_PREFERRED = "900000000000548007";
		
		//for correlation attribute concept imports as it is not in the 0531 NEHTA (AU) RF2 SNOMED CT release
		public static final String REFSET_ATTRIBUTE = "900000000000457003";
		public static final String REFSET_CORRELATION_NOT_SPECIFIED = "447561005";
		
		// Inactivation indicator reference sets
		public static final String REFSET_CONCEPT_INACTIVITY_INDICATOR = "900000000000489007";
		public static final String REFSET_DESCRIPTION_INACTIVITY_INDICATOR = "900000000000490003";
		public static final String REFSET_RELATIONSHIP_INACTIVITY_INDICATOR = "900000000000547002";
		
		//component incativation reasons
		public static final String LIMITED = "900000000000486000";
		public static final String DUPLICATE = "900000000000482003";
		public static final String OUTDATED = "900000000000483008";
		public static final String AMBIGUOUS = "900000000000484002";
		public static final String ERRONEOUS = "900000000000485001";
		public static final String MOVED_ELSEWHERE = "900000000000487009";
		public static final String INAPPROPRIATE = "900000000000494007";
		public static final String PENDING_MOVE = "900000000000492006";
		public static final String CONCEPT_NON_CURRENT = "900000000000495008";
		public static final String NONCONFORMANCE_TO_EDITORIAL_POLICY = "723277005";
		public static final String NOT_SEMANTICALLY_EQUIVALENT = "723278000";
		
		// Historical reference sets
		public static final String REFSET_HISTORICAL_ASSOCIATION = "900000000000522004";
		public static final String REFSET_ALTERNATIVE_ASSOCIATION = "900000000000530003";
		public static final String REFSET_MOVED_FROM_ASSOCIATION = "900000000000525002";
		public static final String REFSET_MOVED_TO_ASSOCIATION = "900000000000524003";
		public static final String REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION = "1186924009";
		public static final String REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION = "900000000000523009";
		public static final String REFSET_POSSIBLY_REPLACED_BY_ASSOCIATION = "1186921001";
		public static final String REFSET_REFERS_TO_ASSOCIATION = "900000000000531004";
		public static final String REFSET_REPLACED_BY_ASSOCIATION = "900000000000526001";
		public static final String REFSET_SAME_AS_ASSOCIATION = "900000000000527005";
		public static final String REFSET_SIMILAR_TO_ASSOCIATION = "900000000000529008";
		public static final String REFSET_WAS_A_ASSOCIATION = "900000000000528000";
		
		public static final Set<String> HISTORICAL_ASSOCIATION_REFSETS = ImmutableSet.of(
				REFSET_HISTORICAL_ASSOCIATION, 
				REFSET_ALTERNATIVE_ASSOCIATION,
				REFSET_MOVED_FROM_ASSOCIATION,
				REFSET_MOVED_TO_ASSOCIATION,
				REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION,
				REFSET_REFERS_TO_ASSOCIATION,
				REFSET_REPLACED_BY_ASSOCIATION,
				REFSET_SAME_AS_ASSOCIATION,
				REFSET_SIMILAR_TO_ASSOCIATION,
				REFSET_WAS_A_ASSOCIATION);
		
		
		//	simple map type refsets
		public static final String CTV3_SIMPLE_MAP_TYPE_REFERENCE_SET_ID = "900000000000497000";
		public static final String SNOMED_RT_SIMPLE_MAP_TYPE_REFERENCE_SET_ID = "900000000000498005";
		public static final String ICD_O_REFERENCE_SET_ID = "446608001";
		public static final String SDD_DRUG_REFERENCE_SET = "940626531000132107";
		public static final String DOSE_FORM_SYNONYM_PLURAL_REFERENCE_SET = "41181011000132103";
		
		//complex map type reference sets
		public static final String ICD_9_CM_REFERENCE_SET_ID = "447563008";
		public static final String ICD_10_REFERENCE_SET_ID = "447562003";
		public static final String ICD_10_CM_COMPLEX_MAP_REFERENCE_SET_ID = "6011000124106";

		// Concept model attribute hierarchy roots
		public static final String CONCEPT_MODEL_ATTRIBUTE = "410662002";
		public static final String SG_CONCRETE_DOMAIN_ATTRIBUTE = "31041000132100";
		
		// Concept model attribute hierarchy roots, starting with INT 20180131
		public static final String CONCEPT_MODEL_OBJECT_ATTRIBUTE = "762705008";
		public static final String CONCEPT_MODEL_DATA_ATTRIBUTE = "762706009";
		
		// Concepts that require special care when classifying
		public static final String ROLE_GROUP = "609096000";
		public static final String PART_OF = "123005000";
		public static final String LATERALITY = "272741003";
		public static final String HAS_ACTIVE_INGREDIENT = "127489000";
		public static final String HAS_DOSE_FORM = "411116001";
		
		//	Australian specific concepts
		public static final String AUSTRALIAN_LANGUAGE_REFERENCE_SET = "32570271000036106";

		//numerical and unit type linkage concepts
		public static final String HAS_STRENGTH = "411117005";
		public static final String HAS_UNITS = "73298004";
		
		//complex map correlation and category
		public static final String MAP_CORRELATION_ROOT = "447247004";
		public static final String MAP_CORRELATION_BROAD_TO_NARROW = "447559001";
		public static final String MAP_CORRELATION_EXACT_MATCH = "447557004";
		public static final String MAP_CORRELATION_NARROW_TO_BROAD = "447558009";
		public static final String MAP_CORRELATION_PARTIAL_OVERLAP = "447560006";
		public static final String MAP_CORRELATION_NOT_MAPPABLE = "447556008";
		public static final String MAP_CORRELATION_NOT_SPECIFIED = "447561005";
		
		public static final String MAP_CATEGORY_ROOT = "447634004";
		public static final String MAP_CATEGORY_NOT_CLASSIFIED = "447638001";
		
		// Modules
		public static final String MODULE_ROOT = "900000000000443000";
		public static final String IHTSDO_MAINTAINED_MODULE = "900000000000445007";
		public static final String MODULE_SCT_CORE = "900000000000207008";
		public static final String MODULE_SCT_MODEL_COMPONENT = "900000000000012004";
		public static final String MODULE_B2I_EXTENSION = "636635721000154103";
		public static final String CORE_NAMESPACE = "373872000";
		public static final String B2I_NAMESPACE = "1000154";
		
		// UK modules
		public static final String UK_MAINTAINED_CLINICAL_MODULE = "999003121000000100";
		public static final String UK_EDITION_MODULE = "999000041000000102";
		public static final String UK_EDITION_REFERENCE_SET_MODULE = "999000031000000106";
		public static final String UK_EDITION_COMPOSITION_MODULE = "83821000000107";
		public static final String UK_CLINICAL_EXTENSION_MODULE = "999000011000000103";
		public static final String UK_CLINICAL_EXTENSION_REFERENCE_SET_MODULE = "999000021000000109";
		public static final String UK_PATHOLOGY_EXTENSION_MODULE = "748131000000104";
		
		public static final String UK_MAINTAINED_PHARMACY_MODULE = "999000871000001102";
		public static final String UK_DRUG_EXTENSION_MODULE = "999000011000001104";
		public static final String UK_DRUG_EXTENSION_REFERENCE_SET_MODULE = "999000021000001108";
		
		public static final String UK_EXCLUDE_FROM_CLINICAL_RELEASE_MODULE = "15211000000101";
		public static final String UK_EXCLUDE_FROM_DRUG_EXTENSION_RELEASE_MODULE = "13088301000001107";
		
		// SG specific concepts
		public static final String GENERATED_SINGAPORE_MEDICINAL_PRODUCT = "551000991000133100";
		public static final String HAS_RELEASE_CHARACTERISTIC = "9141000132106";

		public static final String ABBREVIATION = "9271000132107";
		public static final String ABBREVIATION_PLURAL = "69721000132103";
		public static final String FULL_NAME = "9201000132100";
		public static final String FULL_NAME_PLURAL = "91991000132100";
		public static final String DISPLAY_NAME = "92011000132100";
		public static final String NOTE = "9291000132106";
		public static final String PREFERRED_PLURAL = "9281000132109";
		public static final String PRODUCT_TERM = "9231000132105";
		public static final String PRODUCT_TERM_PLURAL = "69701000132106";
		public static final String SEARCH_TERM = "9221000132108";
		public static final String SHORT_NAME = "9211000132103";
		
		public static final String HAS_PRODUCT_HIERARCHY_LEVEL = "9171000132101";
		public static final String SUBSTANCE = "105590001";
		public static final String HAS_COMPONENT = "246093002";
		public static final String HAS_SDD_CLASS = "8921000132109";

		public static final String NAMESPACE_ROOT = "370136006";
		
		public static final String ACCEPTABILITY = "900000000000511003";
		public static final String REFINABILITY_VALUE = "900000000000226000";
		public static final String DESCRIPTION_INACTIVATION_VALUE = "900000000000493001";
		public static final String CONCEPT_INACTIVATION_VALUE = "900000000000481005";

		// IDs used in RefSet Descriptor RefSet
		// AttributeDescription Field
		public static final String ATTRIBUTE_DESCRIPTION_REFERENCED_COMPONENT = "449608002";
		// AttributeType Field
		// Referenced Components
		public static final String ATTRIBUTE_TYPE_COMPONENT_TYPE = "900000000000460005";
		public static final String ATTRIBUTE_TYPE_CONCEPT_TYPE_COMPONENT = "900000000000461009";
		public static final String ATTRIBUTE_TYPE_DESCRIPTION_TYPE_COMPONENT = "900000000000462002";
		public static final String ATTRIBUTE_TYPE_RELATIONSHIP_TYPE_COMPONENT = "900000000000463007";
		public static final String ATTRIBUTE_TYPE_MEMBER_TYPE_COMPONENT = "900000000000464001";
		
		// Generic types
		public static final String ATTRIBUTE_TYPE_STRING_TYPE = "900000000000465000";
		public static final String ATTRIBUTE_TYPE_INTEGER_TYPE = "900000000000476001";
		public static final String ATTRIBUTE_TYPE_SIGNED_INTEGER_TYPE = "900000000000477005";
		public static final String ATTRIBUTE_TYPE_UNSIGNED_INTEGER_TYPE = "900000000000478000";
		public static final String ATTRIBUTE_TYPE_TIME = "900000000000475002";
		public static final String ATTRIBUTE_TYPE_SNOMEDCT_PARSABLE_STRING = "707000009";
		
		// Language RefSet
		public static final String ATTRIBUTE_TYPE_ACCEPTABILITY = "900000000000511003";
		
		// OWL RefSet
		public static final String ATTRIBUTE_TYPE_OWL_EXPRESSION = "762677007";
		public static final String ATTRIBUTE_TYPE_OWL2_LANG_SYNTAX = "762678002";
		
		// Description Format
		public static final String ATTRIBUTE_TYPE_DESCRIPTION_FORMAT_LENGTH = "900000000000544009";
		
		// Map attributes
		public static final String ATTRIBUTE_TYPE_CORRELATION_VALUE = "447247004";
		public static final String ATTRIBUTE_TYPE_MAP_ADVICE = "900000000000504002";
		public static final String ATTRIBUTE_TYPE_MAP_CATEGORY_VALUE = "609330002";
		public static final String ATTRIBUTE_TYPE_MAP_GROUP = "900000000000501005";
		public static final String ATTRIBUTE_TYPE_MAP_PRIORITY = "900000000000502003";
		public static final String ATTRIBUTE_TYPE_MAP_RULE = "900000000000503008";
		public static final String ATTRIBUTE_TYPE_MAP_TARGET = "900000000000505001";
		
		// Query attributes
		public static final String ATTRIBUTE_TYPE_QUERY = "900000000000515007";
		
		// Attribute attributes
		public static final String ATTRIBUTE_TYPE_ATTRIBUTE_VALUE = "900000000000491004";
		
		// Association attributes
		public static final String ATTRIBUTE_TYPE_ASSOCIATION_TARGET = "900000000000533001";
		public static final String ATTRIBUTE_TYPE_SCHEME_VALUE = "900000000000499002";

		// MRCM Domain
		public static final String ATTRIBUTE_TYPE_DOMAIN_CONSTRAINT = "723565001";
		public static final String ATTRIBUTE_TYPE_PARENT_DOMAIN = "723566000";
		public static final String ATTRIBUTE_TYPE_PROXIMAL_PRIMITIVE_CONSTRAINT = "723567009";
		public static final String ATTRIBUTE_TYPE_PROXIMAL_PRIMITIVE_REFINEMENT = "723568004";
		public static final String ATTRIBUTE_TYPE_DOMAIN_TEMPLATE_FOR_PRECOORDINATION = "723600000";
		public static final String ATTRIBUTE_TYPE_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION = "723601001";
		public static final String ATTRIBUTE_TYPE_GUIDE_URL = "723570008";
		
		// MRCM Attribute Domain
		public static final String ATTRIBUTE_TYPE_DOMAIN = "609431004";
		public static final String ATTRIBUTE_TYPE_GROUPED = "723572000";
		public static final String ATTRIBUTE_TYPE_ATTRIBUTE_CARDINALITY = "723602008";
		public static final String ATTRIBUTE_TYPE_ATTRIBUTE_IN_GROUP_CARDINALITY = "723603003";
		public static final String ATTRIBUTE_TYPE_CONCEPT_MODEL_RULE_STRENGTH = "723573005";
		public static final String ATTRIBUTE_TYPE_CONTENT_TYPE = "723574004";
		
		// MRCM Attribute Range
		public static final String ATTRIBUTE_TYPE_RANGE_CONSTRAINT = "723575003";
		public static final String ATTRIBUTE_TYPE_ATTRIBUTE_RULE = "723576002";

		// MRCM Module Scope
		public static final String ATTRIBUTE_TYPE_RULE_REFSET = "723577006";
		
		// Module dependency
		public static final String ATTRIBUTE_TYPE_SOURCE_EFFECTIVE_TIME = "900000000000536009";
		public static final String ATTRIBUTE_TYPE_TARGET_EFFECTIVE_TIME = "900000000000537000";
	}
	
	// RF2 effective time format
	public static final String RF2_EFFECTIVE_TIME_FORMAT = "yyyyMMdd";
}
