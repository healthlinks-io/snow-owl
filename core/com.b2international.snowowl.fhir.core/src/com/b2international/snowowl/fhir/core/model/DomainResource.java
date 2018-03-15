/*
 * Copyright 2011-2018 B2i Healthcare Pte Ltd, http://b2i.sg
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
package com.b2international.snowowl.fhir.core.model;

import com.b2international.snowowl.fhir.core.model.dt.Narrative;

/**
 * "resourceType" : "[name]",
  // from Resource: id, meta, implicitRules, and language
  "text" : { Narrative }, // C? Text summary of the resource, for human interpretation 0..1
  "contained" : [{ Resource }], // Contained, inline Resources 0..*
  (Extensions - see JSON page) 0..*
  (Modifier Extensions - see JSON page)
  
 * @since 6.3
 */
public class DomainResource extends FhirResource {
	
	//Text summary of the resource, for human interpretation 0..1
	private Narrative text;

}
