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
package com.b2international.snowowl.core.repository;

import org.eclipse.emf.common.util.WrappedException;
import org.slf4j.LoggerFactory;

import com.b2international.commons.exceptions.ApiException;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.authorization.AuthorizedRequest;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.events.util.RequestHeaders;
import com.b2international.snowowl.core.events.util.ResponseHeaders;
import com.b2international.snowowl.core.monitoring.MonitoredRequest;
import com.b2international.snowowl.core.rate.RateLimitingRequest;
import com.b2international.snowowl.eventbus.IHandler;
import com.b2international.snowowl.eventbus.IMessage;

/**
 * Generic Request handler class that handles all requests by executing them immediately.
 * 
 * @since 4.5
 */
public final class ApiRequestHandler implements IHandler<IMessage> {

	private final ServiceProvider context;
	
	public ApiRequestHandler(ServiceProvider context) {
		this.context = context;
	}
	
	@Override
	public final void handle(IMessage message) {
		try {
			final Request<ServiceProvider, ?> req = message.body(Request.class);
			
			final ResponseHeaders responseHeaders = new ResponseHeaders();
			final ServiceProvider executionContext = context.inject()
					.bind(RequestHeaders.class, new RequestHeaders(message.headers()))
					.bind(ResponseHeaders.class, responseHeaders)
					.build();
			
			// monitor each request execution
			final Object body = new MonitoredRequest<>(
				// authorize each request execution
				new AuthorizedRequest<>(
					// rate limit all requests
					new RateLimitingRequest<>(
						// actual request
						req
					)
				)
			).execute(executionContext);
			
			if (body == null) {
				LoggerFactory.getLogger(ApiRequestHandler.class).error("No response was returned from request: " + req.getClass());
			}
					
			message.reply(body, responseHeaders.headers());
		} catch (WrappedException e) {
			message.fail(e.getCause());
		} catch (ApiException e) {
			message.fail(e);
		} catch (Throwable e) {
			LoggerFactory.getLogger(ApiRequestHandler.class).error("Unexpected error when executing request:", e);
			message.fail(e);
		}
	}
	
}
