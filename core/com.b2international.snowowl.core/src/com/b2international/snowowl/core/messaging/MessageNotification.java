/*
 * Copyright 2018-2022 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.core.messaging;

import com.b2international.snowowl.eventbus.events.SystemNotification;

/**
 * @since 7.0
 */
public final class MessageNotification extends SystemNotification {

	private final String message;

	public MessageNotification(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
	
}
