/*
 * Copyright 2011-2015 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.metadata;

import java.util.Collection;
import java.util.List;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.pcj.LongSets;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.core.domain.SnomedConcepts;
import com.b2international.snowowl.snomed.core.lang.LanguageSetting;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.google.inject.Provider;

/**
 * @since 4.3
 */
public final class SnomedMetadataImpl implements SnomedMetadata {

	private final Provider<IEventBus> eventBus;
	private final Provider<SnomedTerminologyBrowser> browser;
	private final Provider<LanguageSetting> languageSetting;

	public SnomedMetadataImpl(final Provider<IEventBus> eventBus, final Provider<SnomedTerminologyBrowser> browser, final Provider<LanguageSetting> languageSetting) {
		this.eventBus = eventBus;
		this.browser = browser;
		this.languageSetting = languageSetting;
	}
	
	@Override
	public Collection<String> getCharacteristicTypeIds(IBranchPath branchPath) {
		return LongSets.toStringSet(getTerminologyBrowser().getAllSubTypeIds(branchPath, Long.valueOf(Concepts.CHARACTERISTIC_TYPE)));
	}

	@Override
	public Collection<SnomedConceptIndexEntry> getCharacteristicTypes(IBranchPath branchPath) {
		final SnomedConcepts concepts = SnomedRequests.prepareSearchConcept()
				.setLocales(getLocales())
				.filterByAncestor(Concepts.CHARACTERISTIC_TYPE)
				.setExpand("pt()")
				.build(branchPath.getPath())
				.executeSync(getEventBus());
		
		return SnomedConceptIndexEntry.fromConcepts(concepts);
	}
	
	private final List<ExtendedLocale> getLocales() {
		return languageSetting.get().getLanguagePreference();
	}
	
	private IEventBus getEventBus() {
		return eventBus.get();
	}

	private SnomedTerminologyBrowser getTerminologyBrowser() {
		return browser.get();
	}

}
