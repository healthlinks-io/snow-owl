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
package com.b2international.snowowl.snomed.datastore.server.request;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.b2international.snowowl.core.domain.TransactionContext;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.snomed.core.domain.ISnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedReferenceSet;
import com.b2international.snowowl.snomed.core.store.SnomedComponents;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetUtil;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetType;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRegularRefSet;

/**
 * @since 4.5
 */
public class SnomedRefSetCreateRequest extends SnomedRefSetRequest<TransactionContext, SnomedReferenceSet> {

	@NotNull
	private final SnomedRefSetType type;
	
	@NotNull
	private final String referencedComponentType;
	
	@Valid
	private final SnomedConceptCreateRequest conceptReq;
	
	SnomedRefSetCreateRequest(SnomedRefSetType type, String referencedComponentType, SnomedConceptCreateRequest conceptReq) {
		this.type = type;
		this.referencedComponentType = referencedComponentType;
		this.conceptReq = conceptReq;
		// FIXME create proper refset create request builder which wraps and create a snomed concept req as well
		if (this.conceptReq.getParentId() == null) {
			this.conceptReq.setParentId(SnomedRefSetUtil.getConceptId(type));
		}
	}
	
	@Override
	public SnomedReferenceSet execute(TransactionContext context) {
		RefSetSupport.check(type);
		RefSetSupport.checkType(type, referencedComponentType);
		checkParent(context);
		
		final ISnomedConcept identifierConcept = this.conceptReq.execute(context);
		
		// FIXME due to different resource lists we need access to the specific editing context (which will be removed later)
		final SnomedRefSetEditingContext refSetContext = context.service(SnomedEditingContext.class).getRefSetEditingContext();
		
		final SnomedRegularRefSet refSet = SnomedComponents
			.newReferenceSet()
			.setType(type)
			.setReferencedComponentType(referencedComponentType)
			.setIdentifierConceptId(identifierConcept.getId())
			.build(context);
		
		refSetContext.add(refSet);
		return new SnomedReferenceSetConverter(context).apply(refSet, identifierConcept);
	}
	
	private void checkParent(TransactionContext context) {
		final String refSetTypeRootParent = SnomedRefSetUtil.getConceptId(type);
		final String desiredParent = conceptReq.getParentId();
		if (!refSetTypeRootParent.equals(desiredParent) && !context.service(SnomedTerminologyBrowser.class).isSuperTypeOfById(context.branch().branchPath(), refSetTypeRootParent, desiredParent)) {
			throw new BadRequestException("'%s' type reference sets should be subtype of '%s' concept. Cannot create as subtype of '%s'.", type, refSetTypeRootParent, desiredParent);
		}
	}

	@Override
	protected Class<SnomedReferenceSet> getReturnType() {
		return SnomedReferenceSet.class;
	}
	
}
