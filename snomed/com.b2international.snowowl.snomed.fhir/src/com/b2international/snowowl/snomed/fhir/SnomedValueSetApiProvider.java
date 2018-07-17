/*
 * Copyright 2018 B2i Healthcare Pte Ltd, http://b2i.sg
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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.fhir.core.CodeSystemApiProvider;
import com.b2international.snowowl.fhir.core.FhirApiProvider;
import com.b2international.snowowl.fhir.core.ICodeSystemApiProvider;
import com.b2international.snowowl.fhir.core.IValueSetApiProvider;
import com.b2international.snowowl.fhir.core.codesystems.IdentifierUse;
import com.b2international.snowowl.fhir.core.codesystems.PublicationStatus;
import com.b2international.snowowl.fhir.core.model.dt.Identifier;
import com.b2international.snowowl.fhir.core.model.dt.Uri;
import com.b2international.snowowl.fhir.core.model.valueset.ValueSet;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSet;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Provider for the SNOMED CT FHIR support
 * @since 6.4
 * @see ICodeSystemApiProvider
 * @see CodeSystemApiProvider
 */
public final class SnomedValueSetApiProvider extends FhirApiProvider implements IValueSetApiProvider {

	private static final String URI_BASE = "http://snomed.info";
	private static final Uri FHIR_URI = new Uri(URI_BASE + "/sct");
	//private static final Path SNOMED_INT_PATH = Paths.get(SnomedDatastoreActivator.REPOSITORY_UUID, SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME);
	private static final Set<String> SUPPORTED_URIS = ImmutableSet.of(
		SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME,
		SnomedTerminologyComponentConstants.SNOMED_INT_LINK,
		FHIR_URI.getUriValue()
	);
	
	private String repositoryId;
	
	public SnomedValueSetApiProvider() {
		this.repositoryId = SnomedDatastoreActivator.REPOSITORY_UUID;
	}
	
	@Override
	protected String getRepositoryId() {
		return repositoryId;
	}
	
	@Override
	public boolean isSupported(Path path) {
		return path.startsWith(SnomedDatastoreActivator.REPOSITORY_UUID);
	}
	
	@Override
	public final boolean isSupported(String uri) {
		if (Strings.isNullOrEmpty(uri)) return false;
		return SUPPORTED_URIS.stream()
			.filter(uri::equalsIgnoreCase)
			.findAny()
			.isPresent();
	}

	@Override
	public Collection<ValueSet> getValueSets() {
		
		//TODO: what to do with the language? Where do i get the locale from the request? 
		String displayLanguage = "en-us";
		String version = null; //what should be the version??
		
		return SnomedRequests.prepareSearchRefSet()
			.all()
			.filterByType(SnomedRefSetType.SIMPLE)
			.build(getRepositoryId(), getBranchPath(version))
			.execute(getBus())
			.then(refsets -> {
				return refsets.stream()
					.map(r -> createValueSetBuilder(r, displayLanguage))
					.map(ValueSet.Builder::build)
					.collect(Collectors.toList());
			})
			.getSync();
	}
	
	@Override
	public ValueSet getValueSet(Path valueSetPath) {
		
		//TODO: what to do with the language? Where do i get the locale from the request? 
		String displayLanguage = "en-us";
		String version = null; //what should be the version?
		
		String referenceSetId = valueSetPath.getFileName().toString();
		
		return SnomedRequests.prepareSearchRefSet()
				.filterById(referenceSetId)
				.filterByType(SnomedRefSetType.SIMPLE)
				.build(getRepositoryId(), getBranchPath(version))
				.execute(getBus())
				.then(refsets -> {
					return refsets.stream()
						.map(r -> createValueSetBuilder(r, displayLanguage))
						.map(ValueSet.Builder::build)
						.collect(Collectors.toList());
				})
				.getSync()
				.stream()
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Active value set", valueSetPath.toString()));
		
	}
	
	private ValueSet.Builder createValueSetBuilder(final SnomedReferenceSet referenceSet, final String displayLanguage) {
		
		String referenceSetId = referenceSet.getId();
		
		Identifier identifier = Identifier.builder()
			.use(IdentifierUse.OFFICIAL)
			.system(getFhirUri())
			.value(referenceSetId)
			.build();
		
		String id = getRepositoryId() + "/" + referenceSetId;
		
		SnomedConcept refsetConcept = SnomedRequests.prepareGetConcept(referenceSetId)
			.setExpand("pt()")
			.setLocales(ImmutableList.of(ExtendedLocale.valueOf(displayLanguage)))
			.build(getRepositoryId(), IBranchPath.MAIN_BRANCH)
			.execute(getBus())
			.getSync();
		
		return ValueSet.builder(id)
			.identifier(identifier)
			.language(displayLanguage)
			.url(getFhirUri())
			.status(referenceSet.isActive() ? PublicationStatus.ACTIVE : PublicationStatus.RETIRED)
			.title(refsetConcept.getPt().getTerm());
	}
	
	protected Uri getFhirUri() {
		return FHIR_URI;
	}

	@Override
	protected String getCodeSystemShortName() {
		return SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME;
	}
	
}
