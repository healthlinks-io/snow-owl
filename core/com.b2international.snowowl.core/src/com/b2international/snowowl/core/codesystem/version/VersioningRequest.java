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
package com.b2international.snowowl.core.codesystem.version;

import java.util.Date;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.b2international.commons.exceptions.AlreadyExistsException;
import com.b2international.commons.exceptions.ApiException;
import com.b2international.index.revision.Commit;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.authorization.BranchAccessControl;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystemVersion;
import com.b2international.snowowl.core.codesystem.CodeSystemVersionEntry;
import com.b2international.snowowl.core.config.RepositoryConfiguration;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.CappedTransactionContext;
import com.b2international.snowowl.core.domain.TransactionContext;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.id.IDs;
import com.b2international.snowowl.core.identity.Permission;
import com.b2international.snowowl.core.repository.TerminologyRepositoryPlugin;

/**
 * {@link VersioningRequest} that will create a {@link CodeSystemVersionEntry} without modifying any of the available terminology components. 
 * {@link TerminologyRepositoryPlugin}s may extend the versioning functionality via the {@link TerminologyRepositoryPlugin#getVersioningRequestBuilder} method.
 * 
 * @since 7.0
 */
public class VersioningRequest implements Request<TransactionContext, Boolean>, BranchAccessControl {

	private static final long serialVersionUID = 1L;
	private final VersioningConfiguration config;

	public VersioningRequest(VersioningConfiguration config) {
		this.config = config;
	}
	
	protected final VersioningConfiguration config() {
		return config;
	}
	
	@Override
	public final Boolean execute(TransactionContext context) {
		final Logger log = context.log();
		
		CodeSystemVersion version = getVersion(context);
		if (version != null && !config.isForce()) {
			throw new AlreadyExistsException("Version", config.getVersionId());
		}

		log.info("Versioning components of '{}' codesystem...", config.getCodeSystemShortName());
		try {
			// capped context to commit versioned components in the configured low watermark bulks
			try (CappedTransactionContext versioningContext = CappedTransactionContext.create(context, getCommitLimit(context)).onCommit(this::onCommit)) {
				doVersionComponents(versioningContext);
			}

			// FIXME remove when fixing _id field value for version documents
			if (version != null && config.isForce()) {
				context.delete(version);
			}
			
			context.add(createVersion(context, config));
		} catch (Exception e) {
			if (e instanceof ApiException) {
				throw (ApiException) e;
			}
			throw new SnowowlRuntimeException(e);
		}
		return Boolean.TRUE;
	}

	protected final int getCommitLimit(TransactionContext context) {
		return context.service(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class).getIndexConfiguration().getCommitWatermarkLow();
	}
	
	/**
	 * Subclasses may override this method to update versioning properties on terminology components before creating the version. 
	 * @param context
	 * @throws Exception 
	 */
	protected void doVersionComponents(TransactionContext context) throws Exception {
	}
	
	/**
	 * Run additional logic when a successful versioning commit was made by this request.
	 * @param context
	 * @param commit
	 */
	protected void onCommit(TransactionContext context, Commit commit) {
	}

	@Nullable
	private CodeSystemVersion getVersion(TransactionContext context) {
		return CodeSystemRequests
				.prepareSearchCodeSystemVersion()
				.setLimit(2)
				.filterByCodeSystemShortName(config.getCodeSystemShortName())
				.filterByVersionId(config.getVersionId())
				.build()
				.execute(context)
				.first()
				.orElse(null);
	}
	
	private final CodeSystemVersionEntry createVersion(final TransactionContext context, final VersioningConfiguration config) {
		return CodeSystemVersionEntry.builder()
				.id(IDs.base64UUID())
				.description(config.getDescription())
				.effectiveDate(EffectiveTimes.getEffectiveTime(config.getEffectiveTime()))
				.importDate(new Date().getTime())
				.parentBranchPath(context.path())
				.versionId(config.getVersionId())
				.codeSystemShortName(config.getCodeSystemShortName())
				.repositoryUuid(context.id())
				.build();
	}
	
	@Override
	public String getOperation() {
		return Permission.OPERATION_EDIT;
	}
	
}