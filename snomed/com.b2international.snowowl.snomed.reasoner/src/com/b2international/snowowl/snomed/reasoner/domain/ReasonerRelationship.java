/*
 * Copyright 2018-2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.reasoner.domain;

import java.io.Serializable;
import java.util.function.Function;

import com.b2international.snowowl.snomed.core.domain.RelationshipValue;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

/**
 * @since 7.0
 */
public final class ReasonerRelationship implements Serializable {

	private String originId;

	/*
	 * Note that the rest of the values below can be completely different (or even
	 * absent) when compared to the "origin" relationship, especially if the change
	 * is a new inference!
	 */
	private Boolean released;
	private Boolean destinationNegated;
	private Integer group;
	private Integer unionGroup;
	private String characteristicTypeId;
	private String modifierId;
	private SnomedConcept source;
	private SnomedConcept destination;
	private RelationshipValue value;
	private SnomedConcept type;

	private static <T, U> U ifNotNull(final T value, final Function<T, U> mapper) {
		if (value != null) {
			return mapper.apply(value);
		} else {
			return null;
		}
	}
	
	// Default constructor is used in JSON de-serialization
	public ReasonerRelationship() {	}
	
	/**
	 * Creates a new instance of a reasoner preview of a relationship.
	 * 
	 * @param originId the SCTID of the relationship this preview is based on (can be <code>null</code>)
	 */
	public ReasonerRelationship(final String originId) {
		setOriginId(originId);
	}
	
	public String getOriginId() {
		return originId;
	}
	
	private void setOriginId(final String originId) {
		this.originId = originId;
	}
	
	public Boolean isReleased() {
		return released;
	}
	
	public void setReleased(final Boolean released) {
		this.released = released;
	}
	
	@JsonProperty
	public String getSourceId() {
		return ifNotNull(getSource(), SnomedConcept::getId);
	}
	
	/**
	 * @return the source concept of this relationship
	 */
	public SnomedConcept getSource() {
		return source;
	}

	@JsonProperty
	public String getDestinationId() {
		return ifNotNull(getDestination(), SnomedConcept::getId);
	}

	/**
	 * @return the destination concept of this relationship
	 */
	public SnomedConcept getDestination() {
		return destination;
	}
	
	@JsonProperty("value")
	public RelationshipValue getValueAsObject() {
		return value;
	}
	
	@JsonIgnore
	public String getValue() {
		return ifNotNull(getValueAsObject(), RelationshipValue::toLiteral);
	}

	@JsonIgnore
	public boolean hasValue() {
		return (value != null);
	}

	/**
	 * Checks whether the destination concept's meaning should be negated ({@code ObjectComplementOf} semantics in OWL2).
	 * 
	 * @return {@code true} if the destination concept is negated, {@code false} if it should be interpreted normally
	 */
	public Boolean isDestinationNegated() {
		return destinationNegated;
	}

	/**
	 * @return the type identifier of this relationship
	 */
	@JsonProperty
	public String getTypeId() {
		return ifNotNull(getType(), SnomedConcept::getId);
	}

	/**
	 * @return the type concept of this relationship
	 */
	public SnomedConcept getType() {
		return type;
	}

	/**
	 * Returns the relationship group number.
	 * 
	 * @return the relationship group, or 0 if this relationship can not be grouped, or is in an unnumbered, singleton group
	 */
	public Integer getGroup() {
		return group;
	}

	/**
	 * If multiple relationship destinations are to be taken as a disjunction, the relationships are assigned a common, positive union group number.
	 * 
	 * @return the relationship union group, or 0 if this relationship is not part of a disjunction
	 */
	public Integer getUnionGroup() {
		return unionGroup;
	}

	/**
	 * Returns the characteristic type of the relationship.
	 * 
	 * @return the relationship's characteristic type
	 */
	public String getCharacteristicTypeId() {
		return characteristicTypeId;
	}

	/**
	 * Returns the relationship's modifier value.
	 * 
	 * @return the modifier of this relationship
	 */
	public String getModifierId() {
		return modifierId;
	}

	public void setSource(final SnomedConcept source) {
		this.source = source;
	}
	
	@JsonIgnore
	public void setSourceId(final String sourceId) {
		setSource(ifNotNull(sourceId, SnomedConcept::new));
	}

	public void setDestination(final SnomedConcept destination) {
		this.destination = destination;
	}
	
	@JsonIgnore
	public void setDestinationId(final String destinationId) {
		setDestination(ifNotNull(destinationId, SnomedConcept::new));
	}
	
	@JsonProperty("value")
	public void setValueAsObject(final RelationshipValue value) {
		this.value = value;
	}

	public void setType(final SnomedConcept type) {
		this.type = type;
	}
	
	@JsonIgnore
	public void setTypeId(final String typeId) {
		setType(ifNotNull(typeId, SnomedConcept::new));
	}
	
	public void setDestinationNegated(final Boolean destinationNegated) {
		this.destinationNegated = destinationNegated;
	}

	public void setGroup(final Integer group) {
		this.group = group;
	}

	public void setUnionGroup(final Integer unionGroup) {
		this.unionGroup = unionGroup;
	}

	public void setCharacteristicTypeId(final String characteristicTypeId) {
		this.characteristicTypeId = characteristicTypeId;
	}

	public void setModifierId(final String modifierId) {
		this.modifierId = modifierId;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("originId", originId)
			.add("released", released)
			.add("destinationNegated", destinationNegated)
			.add("group", group)
			.add("unionGroup", unionGroup)
			.add("characteristicTypeId", characteristicTypeId)
			.add("modifierId", modifierId)
			.add("sourceId", getSourceId())
			.add("typeId", getTypeId())
			.add("destinationId", getDestinationId())
			.add("value", getValue())
			.toString();
	}
}
