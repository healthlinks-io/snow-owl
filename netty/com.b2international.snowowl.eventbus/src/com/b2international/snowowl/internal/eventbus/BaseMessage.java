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
package com.b2international.snowowl.internal.eventbus;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.*;
import java.util.Collections;
import java.util.Map;

import com.b2international.snowowl.eventbus.IMessage;
import com.google.common.collect.ImmutableMap;

/**
 * @since 3.1
 */
/*package*/ class BaseMessage implements IMessage, Serializable {

	private Map<String, String> headers;
	private String tag;
	private Object body;
	private String address;

	/*package*/ boolean succeeded = true;
	/*package*/ boolean send;
	/*package*/ String replyAddress;
	/*package*/ EventBus bus;

	/*package*/ BaseMessage(String address, Object body, String tag, final Map<String, String> headers) {
		this(address, null, true, body, tag, headers);
	}

	/*package*/ BaseMessage(String address, String replyAddress, boolean send, Object body, String tag, final Map<String, String> headers) {
		this.address = address;
		this.send = send;
		this.replyAddress = replyAddress;
		this.body = body;
		this.tag = tag;
		this.headers = headers == null ? Collections.emptyMap() : ImmutableMap.copyOf(headers);
	}

	@Override
	public String replyAddress() {
		return replyAddress;
	}

	@Override
	public String address() {
		return address;
	}

	@Override
	public boolean isSend() {
		return send;
	}

	@Override
	public Object body() {
		return body;
	}
	
	@Override
	public String tag() {
		return tag;
	}
	
	@Override
	public Map<String, String> headers() {
		return headers;
	}

	@Override
	public <T> T body(Class<T> type) {
		checkNotNull(body, "Body should not be null.");
		
		if (type.isInstance(body)) {
			return type.cast(body);
		}
		
		throw new IllegalArgumentException("Could not resolve message body with class: " + type + " body: " + body);
	}

	@Override
	public void reply(Object message) {
		reply(message, Collections.emptyMap());
	}
	
	@Override
	public void reply(Object message, Map<String, String> headers) {
		if (message != null) {
			sendReply(new BaseMessage(replyAddress, message, IMessage.TAG_REPLY, headers));
		}
	}

	@Override
	public void fail(Object failure) {
		if (failure != null) {
			final BaseMessage reply = new BaseMessage(replyAddress, failure, tag, Collections.emptyMap());
			reply.succeeded = false;
			sendReply(reply);
		}
	}

	@Override
	public boolean isSucceeded() {
		return succeeded;
	}

	private void sendReply(BaseMessage reply) {
		if (bus != null && !MessageFactory.isNullOrEmpty(reply.address)) {
			bus.sendMessage(true, reply, null);
		}
	}
	
	@Override
	public String toString() {
		return String.format("Address: %s, message: %s, tag: %s", address, body, tag);
	}
}
