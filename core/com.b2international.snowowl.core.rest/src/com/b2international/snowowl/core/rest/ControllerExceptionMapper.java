/*
 * Copyright 2011-2020 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.core.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.b2international.commons.exceptions.*;
import com.b2international.snowowl.core.util.PlatformUtil;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;

/**
 * @since 4.1
 */
@RestControllerAdvice
public class ControllerExceptionMapper {

	private static final Logger LOG = LoggerFactory.getLogger(ControllerExceptionMapper.class);
	private static final String GENERIC_USER_MESSAGE = "Something went wrong during the processing of your request.";
	
	/**
	 * Generic <b>Internal Server Error</b> exception handler, serving as a fallback for RESTful client calls.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public RestApiError handle(final Exception ex) {
		final String message = Throwables.getRootCause(ex).getMessage();
		if (!Strings.isNullOrEmpty(message) && message.toLowerCase().contains("broken pipe")) {
	        return null; // socket is closed, cannot return any response    
	    } else {
    		LOG.error("Exception during request processing", ex);
	    	return RestApiError.of(ApiError.builder(GENERIC_USER_MESSAGE).build()).build(HttpStatus.INTERNAL_SERVER_ERROR.value());
	    }
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
	public RestApiError handle(final AsyncRequestTimeoutException e) {
		return RestApiError.of(ApiError.builder("Request is taking longer than expected to complete. Retry again in a few minutes.").build()).build(HttpStatus.GATEWAY_TIMEOUT.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final MaxUploadSizeExceededException e) {
		return RestApiError.of(ApiError.builder(e.getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final MultipartException e) {
		return RestApiError.of(ApiError.builder("Couldn't process multipart request: " + e.getMostSpecificCause().getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	public RestApiError handle(final HttpMediaTypeNotSupportedException e) {
		return RestApiError.of(ApiError.builder("HTTP Media Type " + e.getContentType() + " is not supported. Supported media types are: " + e.getSupportedMediaTypes()).build()).build(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	public RestApiError handle(final HttpRequestMethodNotSupportedException e) {
		return RestApiError.of(ApiError.builder("Method " + e.getMethod() + " is not allowed").build()).build(HttpStatus.METHOD_NOT_ALLOWED.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final BindException e) {
		return RestApiError.of(ApiError.builder("Invalid  parameter: '" + e.getMessage() + "'.").build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final MissingPathVariableException e) {
		return RestApiError.of(ApiError.builder("Missing path parameter: '" + e.getVariableName() + "'.").build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public RestApiError handle(final UnauthorizedException ex) {
		final ApiError err = ex.toApiError();
		return RestApiError.of(err).build(HttpStatus.UNAUTHORIZED.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public RestApiError handle(final ForbiddenException ex) {
		final ApiError err = ex.toApiError();
		return RestApiError.of(err).build(HttpStatus.FORBIDDEN.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
	public RestApiError handle(final RequestTimeoutException ex) {
		if (PlatformUtil.isDevVersion()) {
    		LOG.error("Timeout during request processing", ex);
    	} else {
    		LOG.trace("Timeout during request processing", ex);
    	}
		return RestApiError.of(ApiError.builder(GENERIC_USER_MESSAGE).build()).build(HttpStatus.REQUEST_TIMEOUT.value());
	}
	
	/**
	 * Exception handler converting any {@link JsonMappingException} to an <em>HTTP 400</em>.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final HttpMessageNotReadableException ex) {
		LOG.trace("Exception during processing of a JSON document", ex);
		return RestApiError.of(ApiError.builder("Invalid JSON representation").developerMessage(ex.getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	@ExceptionHandler
	public ResponseEntity<RestApiError> handle(final ApiErrorException ex) {
		final ApiError error = ex.toApiError();
		return new ResponseEntity<>(RestApiError.of(error).build(error.getStatus()), HttpStatus.valueOf(error.getStatus()));
	}

	/**
	 * <b>Not Found</b> exception handler. All {@link NotFoundException not found exception}s are mapped to {@link HttpStatus#NOT_FOUND
	 * <em>404 Not Found</em>} in case of the absence of an instance resource.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public RestApiError handle(final NotFoundException ex) {
		return RestApiError.of(ex.toApiError()).build(HttpStatus.NOT_FOUND.value());
	}

	/**
	 * Exception handler to return <b>Not Implemented</b> when an {@link UnsupportedOperationException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
	public RestApiError handle(final NotImplementedException ex) {
		return RestApiError.of(ex.toApiError()).build(HttpStatus.NOT_IMPLEMENTED.value());
	}

	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final BadRequestException ex) {
		return RestApiError.of(ex.toApiError()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final IllegalArgumentException ex) {
		ex.printStackTrace();
		return RestApiError.of(ApiError.builder(ex.getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.CONFLICT)
	public RestApiError handle(final ConflictException ex) {
		if (ex.getCause() != null) {
			LOG.info("Conflict with cause", ex);
		}
		return RestApiError.of(ex.toApiError()).build(HttpStatus.CONFLICT.value());
	}
	
	@ExceptionHandler
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public ResponseEntity<RestApiError> handle(final TooManyRequestsException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("X-Rate-Limit-Retry-After-Seconds", Long.toString(ex.getSecondsToWait()))
				.body(RestApiError.of(ex.toApiError()).build(HttpStatus.TOO_MANY_REQUESTS.value()));
	}
	
	/**
	 * Exception handler for exceptions thrown by conversions performed by {@link Converter}
	 * implementations.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestApiError handle(final ConversionFailedException ex) {
		return RestApiError.of(ApiError.builder(ex.getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
    }

	/**
	 * Exception handler for exceptions thrown due to incorrect arguments.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final MethodArgumentTypeMismatchException ex) {
		return RestApiError.of(ApiError.builder(ex.getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
	/**
	 * Exception handler for exceptions thrown due to missing multipart files.
	 * @param ex
	 * @return
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public RestApiError handle(final MissingServletRequestPartException ex) {
		return RestApiError.of(ApiError.builder(ex.getMessage()).build()).build(HttpStatus.BAD_REQUEST.value());
	}
	
}
