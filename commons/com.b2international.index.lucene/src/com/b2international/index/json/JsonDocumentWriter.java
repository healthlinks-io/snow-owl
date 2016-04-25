/*
 * Copyright 2011-2016 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.index.json;

import java.io.IOException;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;

import com.b2international.index.write.Writer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 4.7
 */
public class JsonDocumentWriter implements Writer {

	private final IndexWriter writer;
	private final ReferenceManager<IndexSearcher> searchers;
	private final JsonDocumentMappingStrategy mappingStrategy;

	public JsonDocumentWriter(IndexWriter writer, ReferenceManager<IndexSearcher> searchers, ObjectMapper mapper) {
		this.writer = writer;
		this.searchers = searchers;
		this.mappingStrategy = new JsonDocumentMappingStrategy(mapper);
	}
	
	@Override
	public void close() throws Exception {
		// TODO rollback changes if there were exceptions
		searchers.maybeRefreshBlocking();
	}

	@Override
	public void put(String key, Object object) throws IOException {
		writer.addDocument(mappingStrategy.map(key, object));
	}

	@Override
	public boolean remove(Class<?> type, String key) throws IOException {
		writer.deleteDocuments(JsonDocumentMapping.matchIdAndType(type, key));
		// TODO do we need boolean return value here???
		return true;
	}

}
