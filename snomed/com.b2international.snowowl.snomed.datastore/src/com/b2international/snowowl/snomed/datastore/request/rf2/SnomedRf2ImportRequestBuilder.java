/*
 * Copyright 2017-2022 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.snomed.datastore.request.rf2;

import java.time.LocalDate;
import java.util.UUID;

import com.b2international.commons.collections.Collections3;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.core.events.BaseRequestBuilder;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.request.RevisionIndexRequestBuilder;
import com.b2international.snowowl.core.request.io.ImportResponse;
import com.b2international.snowowl.snomed.core.domain.Rf2ReleaseType;
import com.google.common.annotations.VisibleForTesting;

/**
 * @since 6.0.0
 */
public final class SnomedRf2ImportRequestBuilder 
		extends BaseRequestBuilder<SnomedRf2ImportRequestBuilder, BranchContext, ImportResponse> 
		implements RevisionIndexRequestBuilder<ImportResponse> {

	private UUID rf2ArchiveId;
	private Rf2ReleaseType releaseType = Rf2ReleaseType.DELTA;
	private boolean createVersions = true;
	private Iterable<String> ignoreMissingReferencesIn;
	private boolean dryRun = false;
	private LocalDate importUntil;
	
	SnomedRf2ImportRequestBuilder() {
	}
	
	public SnomedRf2ImportRequestBuilder setRf2ArchiveId(UUID rf2ArchiveId) {
		this.rf2ArchiveId = rf2ArchiveId;
		return getSelf();
	}

	public SnomedRf2ImportRequestBuilder setReleaseType(Rf2ReleaseType releaseType) {
		this.releaseType = releaseType;
		return getSelf();
	}
	
	public SnomedRf2ImportRequestBuilder setCreateVersions(boolean createVersions) {
		this.createVersions = createVersions;
		return getSelf();
	}
	
	public SnomedRf2ImportRequestBuilder setIgnoreMissingReferencesIn(Iterable<String> ignoreMissingReferencesIn) {
		this.ignoreMissingReferencesIn = ignoreMissingReferencesIn;
		return getSelf();
	}
	
	public SnomedRf2ImportRequestBuilder setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
		return getSelf();
	}
	
	public SnomedRf2ImportRequestBuilder setImportUntil(String importUntil) {
		return setImportUntil(importUntil == null ? null : EffectiveTimes.parse(importUntil, DateFormats.SHORT));
	}
	
	public SnomedRf2ImportRequestBuilder setImportUntil(LocalDate importUntil) {
		this.importUntil = importUntil;
		return getSelf();
	}
	
	@Override
	protected Request<BranchContext, ImportResponse> doBuild() {
		final SnomedRf2ImportRequest req = new SnomedRf2ImportRequest(rf2ArchiveId);
		req.setReleaseType(releaseType);
		req.setCreateVersions(createVersions);
		req.setIgnoreMissingReferencesIn(Collections3.toImmutableSet(ignoreMissingReferencesIn));
		req.setDryRun(dryRun);
		req.setImportUntil(importUntil);
		return req;
	}

	@Override
	public boolean snapshot() {
		return false;
	}

	@VisibleForTesting
	public static void enableVersionsOnChildBranches() {
		SnomedRf2ImportRequest.disableVersionsOnChildBranches.set(false);
	}
	
	@VisibleForTesting
	public static void disableVersionsOnChildBranches() {
		SnomedRf2ImportRequest.disableVersionsOnChildBranches.set(true);
	}

}
