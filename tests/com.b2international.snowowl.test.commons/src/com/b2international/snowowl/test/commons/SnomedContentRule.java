/*
 * Copyright 2011-2022 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.test.commons;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.rules.ExternalResource;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.attachments.AttachmentRegistry;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.branch.BranchPathUtils;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystemVersion;
import com.b2international.snowowl.core.codesystem.CodeSystemVersionEntry;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.jobs.JobRequests;
import com.b2international.snowowl.core.repository.RepositoryRequests;
import com.b2international.snowowl.core.request.SearchResourceRequest.SortField;
import com.b2international.snowowl.core.uri.CodeSystemURI;
import com.b2international.snowowl.core.util.PlatformUtil;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.domain.Rf2ReleaseType;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.snomed.datastore.request.rf2.SnomedRf2Requests;
import com.b2international.snowowl.test.commons.rest.RestExtensions;

/**
 * JUnit test rule to import SNOMED CT content during automated tests.
 * 
 * @since 3.6
 */
public class SnomedContentRule extends ExternalResource {

	public static final String SNOMEDCT_COMPLEX_MAP_BLOCK_EXT_ID = "SNOMEDCT-COMPLEX-MAP-BLOCK";
	
	public static final CodeSystemURI SNOMEDCT_COMPLEX_MAP_BLOCK_EXT = new CodeSystemURI(SNOMEDCT_COMPLEX_MAP_BLOCK_EXT_ID);
	
	private final File importArchive;
	private final Rf2ReleaseType contentType;
	private final String codeSystemId;
	private final String codeSystemBranchPath;
	private final Map<String, Object> additionalProperties;
	
	private String importUntil;
	private CodeSystemURI extensionOf;

	public SnomedContentRule(final String codeSystemShortName, final String branchPath, final String importArchivePath, final Rf2ReleaseType contentType) {
		this(codeSystemShortName, branchPath, SnomedContentRule.class, importArchivePath, contentType);
	}
	
	public SnomedContentRule(final String codeSystemShortName, final String branchPath, final Class<?> relativeClass, final String importArchivePath, final Rf2ReleaseType contentType) {
		this(codeSystemShortName, branchPath, relativeClass, false, importArchivePath, contentType);
	}
	
	public SnomedContentRule(final String codeSystemShortName, final String branchPath, final Class<?> relativeClass, final boolean isFragment, final String importArchivePath, final Rf2ReleaseType contentType) {
		this(codeSystemShortName, branchPath, relativeClass, isFragment, importArchivePath, contentType, null);
	}
	
	public SnomedContentRule(final String codeSystemShortName, final String branchPath, final Class<?> relativeClass, final boolean isFragment, 
			final String importArchivePath, final Rf2ReleaseType contentType, final Map<String, Object> additionalProperties) {
		this.codeSystemId = checkNotNull(codeSystemShortName, "codeSystem");
		this.codeSystemBranchPath = checkNotNull(branchPath, "branchPath");
		this.contentType = checkNotNull(contentType, "contentType");
		this.importArchive = isFragment ? PlatformUtil.toAbsolutePath(relativeClass, importArchivePath).toFile() : PlatformUtil.toAbsolutePathBundleEntry(relativeClass, importArchivePath).toFile();
		this.additionalProperties = additionalProperties;
	}

	public SnomedContentRule importUntil(String importUntil) {
		this.importUntil = importUntil;
		return this;
	}

	public SnomedContentRule extensionOf(CodeSystemURI extensionOf) {
		this.extensionOf = extensionOf;
		return this;
	}
	
	@Override
	protected void before() throws Throwable {
		createBranch();
		createCodeSystemIfNotExist();
		UUID rf2ArchiveId = UUID.randomUUID();
		ApplicationContext.getServiceForClass(AttachmentRegistry.class).upload(rf2ArchiveId, new FileInputStream(importArchive));
		String jobId = SnomedRequests.rf2().prepareImport()
			.setRf2ArchiveId(rf2ArchiveId)
			.setReleaseType(contentType)
			.setCreateVersions(true)
			.setImportUntil(importUntil)
			.build(SnomedDatastoreActivator.REPOSITORY_UUID, codeSystemBranchPath)
			.runAsJobWithRestart(SnomedRf2Requests.importJobKey(codeSystemBranchPath), "Importing RF2 content into " + codeSystemId)
			.execute(Services.bus())
			.getSync(1, TimeUnit.MINUTES);
		JobRequests.waitForJob(Services.bus(), jobId, 2000 /* 2 seconds */);
	}
	
	private void createBranch() {
		if (!IBranchPath.MAIN_BRANCH.equals(codeSystemBranchPath)) {
			final IBranchPath csPath = BranchPathUtils.createPath(codeSystemBranchPath);
			RepositoryRequests.branching().prepareCreate()
				.setParent(csPath.getParentPath())
				.setName(csPath.lastSegment())
				.build(SnomedDatastoreActivator.REPOSITORY_UUID)
				.execute(Services.bus())
				.getSync();
		}
	}

	private void createCodeSystemIfNotExist() {
		if (!SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME.equals(codeSystemId)) {
			final IEventBus eventBus = Services.bus();
			final CodeSystems codeSystems = CodeSystemRequests.prepareSearchCodeSystem()
					.setLimit(0)
					.filterById(codeSystemId)
					.build(SnomedDatastoreActivator.REPOSITORY_UUID)
					.execute(eventBus)
					.getSync();
			
			if (codeSystems.getTotal() > 0) {
				return;
			}
			
			final CodeSystemURI extensionOf = Optional.ofNullable(this.extensionOf).or(() -> {
					return CodeSystemRequests.prepareSearchCodeSystemVersion()
						.filterByCodeSystemShortName(SnomedTerminologyComponentConstants.SNOMED_SHORT_NAME)
						.sortBy(SortField.descending(CodeSystemVersionEntry.Fields.EFFECTIVE_DATE))
						.setLimit(1)
						.build(SnomedDatastoreActivator.REPOSITORY_UUID)
						.execute(eventBus)
						.getSync()
						.first()
						.map(CodeSystemVersion::getUri);
				})
				.orElse(null);
				
			CodeSystemRequests.prepareNewCodeSystem()
				.setBranchPath(codeSystemBranchPath)
				.setCitation("citation")
				.setExtensionOf(extensionOf)
				.setIconPath("iconPath")
				.setLanguage("language")
				.setLink("organizationLink")
				.setName("Extension " + codeSystemId)
				.setOid("oid:" + codeSystemId)
				.setRepositoryId(SnomedDatastoreActivator.REPOSITORY_UUID)
				.setShortName(codeSystemId)
				.setAdditionalProperties(additionalProperties)
				.setTerminologyId(SnomedTerminologyComponentConstants.TERMINOLOGY_ID)
				.build(SnomedDatastoreActivator.REPOSITORY_UUID, Branch.MAIN_PATH, RestExtensions.USER, String.format("Create code system %s", codeSystemId))
				.execute(eventBus)
				.getSync();
		}
	}
}
