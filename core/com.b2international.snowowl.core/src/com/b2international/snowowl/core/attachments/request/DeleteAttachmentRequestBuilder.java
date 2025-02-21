/*
 * Copyright 2022 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.core.attachments.request;

import java.util.UUID;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.BaseRequestBuilder;
import com.b2international.snowowl.core.request.SystemRequestBuilder;

public class DeleteAttachmentRequestBuilder 
	extends BaseRequestBuilder<DeleteAttachmentRequestBuilder, ServiceProvider, Boolean> 
	implements SystemRequestBuilder<Boolean> {

	private UUID id;

	/*package*/ DeleteAttachmentRequestBuilder() { }
	
	public DeleteAttachmentRequestBuilder setId(final UUID id) {
		this.id = id;
		return getSelf();
	}
	
	@Override
	protected DeleteAttachmentRequest doBuild() {
		return new DeleteAttachmentRequest(id);
	}
}
