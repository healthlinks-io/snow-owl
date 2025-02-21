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
package com.b2international.snowowl.core.jobs;

import static com.b2international.index.query.Expressions.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.time.TimeUtil;
import com.b2international.index.*;
import com.b2international.index.query.Expression;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

@Doc(type = "job")
@JsonDeserialize(builder=RemoteJobEntry.Builder.class)
@Script(name=RemoteJobEntry.WITH_DELETED, script="ctx._source.deleted = true")
@Script(name=RemoteJobEntry.WITH_STATE, script=""
		+ "if (ctx._source.state == params.expectedState) {"
		+ " ctx._source.state = params.newState; "
		+ "}")
@Script(name=RemoteJobEntry.WITH_COMPLETION_LEVEL, script="ctx._source.completionLevel = params.completionLevel")
@Script(name=RemoteJobEntry.WITH_RUNNING, script="ctx._source.state = params.state;ctx._source.startDate = params.startDate")
@Script(name=RemoteJobEntry.WITH_DONE, script="ctx._source.state = params.state;ctx._source.finishDate = params.finishDate;ctx._source.result = params.result")
public final class RemoteJobEntry implements Serializable {

	public static final String WITH_STATE = "withState";
	public static final String WITH_DELETED = "withDeleted";
	public static final String WITH_COMPLETION_LEVEL = "withCompletionLevel";
	public static final String WITH_RUNNING = "withRunning";
	public static final String WITH_DONE = "withDone";

	private static final long serialVersionUID = 1L;
	private static final Set<RemoteJobState> DONE_STATES = ImmutableSet.of(RemoteJobState.FINISHED, RemoteJobState.FAILED, RemoteJobState.CANCELED);

	public static final int MIN_COMPLETION_LEVEL = 0;
	public static final int MAX_COMPLETION_LEVEL = 100;


	public static class Fields {
		public static final String ID = "id";
		public static final String KEY = "key";
		public static final String DELETED = "deleted";
		public static final String USER = "user";
		public static final String STATE = "state";
		public static final String START_DATE = "startDate";
		public static final String FINISH_DATE = "finishDate";
		public static final String SCHEDULE_DATE = "scheduleDate";
		public static final String PARAMETERS = "parameters";
		public static final Set<String> SORT_FIELDS = ImmutableSet.of(
			ID,
			DELETED,
			USER,
			STATE,
			START_DATE,
			SCHEDULE_DATE,
			FINISH_DATE
		);
		public static final String DESCRIPTION = "description";
	}
	
	public static class Expressions {

		public static Expression id(String id) {
			return exactMatch(Fields.ID, id);
		}

		public static Expression ids(Collection<String> ids) {
			return matchAny(Fields.ID, ids);
		}
		
		public static Expression idPrefix(String id) {
			return prefixMatch(Fields.ID, id);
		}
		
		public static Expression key(String key) {
			return exactMatch(Fields.KEY, key);
		}

		public static Expression keys(Collection<String> keys) {
			return matchAny(Fields.KEY, keys);
		}

		public static Expression deleted(boolean deleted) {
			return match(Fields.DELETED, deleted);
		}
		
		public static Expression users(Iterable<String> users) {
			return matchAny(Fields.USER, users);
		}

		public static Expression done() {
			return states(DONE_STATES);
		}
		
		public static Expression state(RemoteJobState state) {
			return states(Collections.singleton(state));
		}
		
		public static Expression states(Iterable<RemoteJobState> states) {
			return matchAny(Fields.STATE, FluentIterable.from(states).transform(Enum::name).toSet());
		}
		
		public static Expression finishDate(long from, long to) {
			return matchRange(Fields.FINISH_DATE, from, to);
		}

		public static Expression matchRequestType(Iterable<String> values) {
			return matchParameter("type", values);
		}
		
		public static Expression matchParameter(String parameterName, Iterable<String> values) {
			return nestedMatch(RemoteJobEntry.Fields.PARAMETERS, matchAny(parameterName, values));
		}
		
	}
	
	public static RemoteJobEntry.Builder from(RemoteJobEntry from) {
		return builder()
				.id(from.getId())
				.key(from.getKey())
				.description(from.getDescription())
				.user(from.getUser())
				.scheduleDate(from.getScheduleDate())
				.startDate(from.getStartDate())
				.finishDate(from.getFinishDate())
				.state(from.getState())
				.completionLevel(from.getCompletionLevel())
				.parameters(from.getParameters())
				.result(from.getResult())
				.deleted(from.isDeleted());
	}
	
	public static RemoteJobEntry.Builder builder() {
		return new Builder();
	}

	@JsonPOJOBuilder(withPrefix="")
	public static class Builder {

		private String id;
		private String key;
		private String description;
		private String user;
		private Date scheduleDate;
		private Date startDate;
		private Date finishDate;
		private RemoteJobState state = RemoteJobState.SCHEDULED;
		private int completionLevel = MIN_COMPLETION_LEVEL;
		private boolean deleted;
		private String parameters;
		private String result;
		
		@JsonCreator
		private Builder() {
		}
		
		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder key(String key) {
			this.key = key;
			return this;
		}
		
		public Builder description(String description) {
			this.description = description;
			return this;
		}
		
		public Builder user(String user) {
			this.user = user;
			return this;
		}
		
		public Builder scheduleDate(Date scheduleDate) {
			this.scheduleDate = scheduleDate;
			return this; 
		}
		
		public Builder startDate(Date startDate) {
			this.startDate = startDate;
			return this;
		}
		
		public Builder finishDate(Date finishDate) {
			this.finishDate = finishDate;
			return this;
		}
		
		public Builder state(RemoteJobState state) {
			this.state = state;
			return this;
		}
		
		public Builder completionLevel(int completionLevel) {
			this.completionLevel = completionLevel;
			return this;
		}

		public Builder deleted(boolean deleted) {
			this.deleted = deleted;
			return this;
		}
		
		public Builder result(String result) {
			this.result = result;
			return this;
		}
		
		public Builder parameters(String parameters) {
			this.parameters = parameters;
			return this;
		}
		
		public RemoteJobEntry build() {
			return new RemoteJobEntry(id, key, description, user, scheduleDate, startDate, finishDate, state, completionLevel, deleted, result, parameters);
		}
		
	}
	
	@ID
	private final String id;
	private final String key;
	
	@Text(analyzer = Analyzers.TOKENIZED)
	@Text(alias="prefix", analyzer = Analyzers.PREFIX, searchAnalyzer = Analyzers.TOKENIZED)
	@Keyword(alias="original")
	private final String description;	
	private final String user;
	private final Date scheduleDate;
	private final Date startDate;
	private final Date finishDate;
	private final RemoteJobState state;
	private final int completionLevel;
	private final boolean deleted;
	
	@Keyword(index=false)
	private final String result;
	
	@Keyword(index=false)
	private final String parameters;

	private RemoteJobEntry(
			final String id,
			final String key,
			final String description, 
			final String user, 
			final Date scheduleDate, 
			final Date startDate,
			final Date finishDate, 
			final RemoteJobState state, 
			final int completionLevel,
			final boolean deleted,
			final String result,
			final String parameters) {
		this.id = id;
		this.key = key;
		this.description = description;
		this.user = user;
		this.scheduleDate = scheduleDate;
		this.startDate = startDate;
		this.finishDate = finishDate;
		this.state = state;
		this.completionLevel = completionLevel;
		this.deleted = deleted;
		this.result = result;
		this.parameters = parameters;
	}

	public String getId() {
		return id;
	}

	public String getKey() {
		return key;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getUser() {
		return user;
	}

	public Date getScheduleDate() {
		return scheduleDate;
	}
	
	public Date getStartDate() {
		return startDate;
	}
	
	public Date getFinishDate() {
		return finishDate;
	}
	
	public RemoteJobState getState() {
		return state;
	}
	
	public int getCompletionLevel() {
		return completionLevel;
	}

	public boolean isDeleted() {
		return deleted;
	}
	
	/**
	 * @return the elapsed time since this job has been scheduled for execution in human-readable format, or <code>null</code> if the job is not finished.
	 */
	public String getElapsedTime() {
		if (finishDate != null && scheduleDate != null) {
			return TimeUtil.milliToReadableString(finishDate.getTime() - scheduleDate.getTime());
		} else {
			return null;
		}
	}
	
	/**
	 * Returns a JSON serialized parameters object.
	 * @return
	 */
	public String getParameters() {
		return parameters;
	}
	
	/**
	 * Returns a deserialized parameters {@link Map} using the given {@link ObjectMapper} to deserialize the {@link #getParameters()} JSON string.
	 * @param mapper
	 * @return
	 */
	public Map<String, Object> getParameters(ObjectMapper mapper) {
		return getParametersAs(mapper, Map.class);
	}
	
	/**
	 * Returns a deserialized parameters object using the given {@link ObjectMapper} and type to deserialize the {@link #getParameters()} JSON string.
	 * @param mapper
	 * @return
	 */
	public <T> T getParametersAs(ObjectMapper mapper, Class<T> type) {
		try {
			return mapper.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(getParameters());
		} catch (IOException e) {
			throw new SnowowlRuntimeException(e);
		}
	}
	
	/**
	 * Returns a JSON serialized result object or <code>null</code> if there is no result. 
	 * @return
	 */
	public String getResult() {
		return result;
	}
	
	/**
	 * Returns a deserialized result {@link Map} using the given {@link ObjectMapper} to deserialize the {@link #getResult()} JSON string.
	 * @param mapper
	 * @return
	 */
	public Map<String, Object> getResult(ObjectMapper mapper) {
		return getResultAs(mapper, Map.class);
	}
	
	/**
	 * Returns a deserialized result using the given {@link ObjectMapper} and type to deserialize the {@link #getResult()} JSON string.
	 * @param mapper
	 * @param type
	 * @return
	 */
	public <T> T getResultAs(ObjectMapper mapper, Class<T> type) {
		try {
			if (CompareUtils.isEmpty(getResult())) {
				return null;
			}
			return mapper.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(getResult());
		} catch (IOException e) {
			throw new SnowowlRuntimeException(e);
		}
	}
	
	// Frequently used domain specific methods

	@JsonIgnore
	public boolean isDone() {
		return DONE_STATES.contains(getState());
	}
	
	@JsonIgnore
	public boolean isRunning() {
		return !isDone();
	}
	
	@JsonIgnore
	public boolean isCancelled() {
		return getState().oneOf(RemoteJobState.CANCELED, RemoteJobState.CANCEL_REQUESTED);
	}
	
	@JsonIgnore
	public boolean isSuccessful() {
		return RemoteJobState.FINISHED == getState();
	}
	
	@Override
	public int hashCode() {
		return 31 + id.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		
		if (obj == null) {
			return false;
		}

		if (!(obj instanceof RemoteJobEntry)) {
			return false;
		}
		
		final RemoteJobEntry other = (RemoteJobEntry) obj;
		return id.equals(other.id);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("id", id)
				.add("description", description)
				.add("user", user)
				.add("scheduleDate", scheduleDate)
				.add("startDate", startDate)
				.add("finishDate", finishDate)
				.add("state", state)
				.add("deleted", deleted)
				.add("completionLevel", completionLevel)
				.add("result", result)
				.add("parameters", parameters)
				.toString();
	}

}