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
package com.b2international.snowowl.snomed.core.rest.io;

import static com.b2international.snowowl.snomed.common.SnomedConstants.Concepts.MODULE_SCT_CORE;
import static com.b2international.snowowl.snomed.common.SnomedConstants.Concepts.ROOT_CONCEPT;
import static com.b2international.snowowl.snomed.core.rest.SnomedApiTestConstants.UK_ACCEPTABLE_MAP;
import static com.b2international.snowowl.snomed.core.rest.SnomedApiTestConstants.UK_PREFERRED_MAP;
import static com.b2international.snowowl.snomed.core.rest.SnomedComponentRestRequests.createComponent;
import static com.b2international.snowowl.snomed.core.rest.SnomedComponentRestRequests.getComponent;
import static com.b2international.snowowl.snomed.core.rest.SnomedComponentRestRequests.updateComponent;
import static com.b2international.snowowl.snomed.core.rest.SnomedExportRestRequests.doExport;
import static com.b2international.snowowl.snomed.core.rest.SnomedExportRestRequests.export;
import static com.b2international.snowowl.snomed.core.rest.SnomedRefSetRestRequests.updateRefSetComponent;
import static com.b2international.snowowl.snomed.core.rest.SnomedRefSetRestRequests.updateRefSetMemberEffectiveTime;
import static com.b2international.snowowl.snomed.core.rest.SnomedRestFixtures.*;
import static com.b2international.snowowl.test.commons.codesystem.CodeSystemRestRequests.createCodeSystem;
import static com.b2international.snowowl.test.commons.codesystem.CodeSystemVersionRestRequests.createVersion;
import static com.b2international.snowowl.test.commons.rest.RestExtensions.assertCreated;
import static com.google.common.collect.Sets.newHashSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.b2international.commons.Pair;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.json.Json;
import com.b2international.index.revision.RevisionIndex;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.attachments.Attachment;
import com.b2international.snowowl.core.attachments.AttachmentRegistry;
import com.b2international.snowowl.core.attachments.InternalAttachmentRegistry;
import com.b2international.snowowl.core.branch.BranchPathUtils;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.request.CommitResult;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.core.domain.*;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedRefSetType;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMembers;
import com.b2international.snowowl.snomed.core.rest.AbstractSnomedApiTest;
import com.b2international.snowowl.snomed.core.rest.SnomedApiTestConstants;
import com.b2international.snowowl.snomed.core.rest.SnomedComponentType;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.test.commons.rest.RestExtensions;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * @since 5.4
 */
public class SnomedExportApiTest extends AbstractSnomedApiTest {

	private static final Joiner TAB_JOINER = Joiner.on('\t');
	private static final List<ExtendedLocale> LOCALES = List.of(ExtendedLocale.valueOf("en-gb"), ExtendedLocale.valueOf("en-us"));
	
	private static void assertArchiveContainsLines(File exportArchive, Multimap<String, Pair<Boolean, String>> fileToLinesMap) throws Exception {
		
		Multimap<String, Pair<Boolean, String>> resultMap = collectLines(exportArchive, fileToLinesMap);
		Set<String> difference = Sets.difference(fileToLinesMap.keySet(), resultMap.keySet());

		// check if complete files are missing from the result archive
		assertTrue(String.format("File(s) starting with <%s> are missing from the export archive", Joiner.on(", ").join(difference)),
				difference.isEmpty());

		for (Entry<String, Collection<Pair<Boolean, String>>> entry : fileToLinesMap.asMap().entrySet()) {
			
			String fileName = entry.getKey();
			Collection<Pair<Boolean, String>> lines = entry.getValue();
			
			for (Pair<Boolean, String> result : resultMap.get(fileName)) {
				
				Pair<Boolean, String> originalLine = lines.stream().filter(pair -> pair.getB().equals(result.getB())).findFirst().get();
				String message = String.format("Line: %s must %sbe contained in %s", originalLine.getB(), originalLine.getA() ? "" : "not ",
						fileName);
				
				assertEquals(message, originalLine.getA(), result.getA());
			}
		}
	}
	
	private static void assertArchiveContainsFiles(File exportArchive, Map<String, Boolean> filePrefixes) throws Exception {
		assertTrue("Exported RF2 ZIP should be present in the local fs: " + exportArchive.toPath(), Files.exists(exportArchive.toPath()));
		
		Set<String> existingFiles = newHashSet();
		
		try (FileSystem fs = FileSystems.newFileSystem(exportArchive.toPath(), (ClassLoader) null)) {
			for (Path path : fs.getRootDirectories()) {
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						for (String filePrefix : filePrefixes.keySet()) {
							/* 
							 * XXX: Need to add underscore to the end of each prefix here, as some file names 
							 * are prefixes of each other.
							 */
							if (file.getFileName().toString().startsWith(filePrefix + "_")) {
								existingFiles.add(filePrefix);
								break;
							}
						}
						return super.visitFile(file, attrs);
					}
				});

			}

		} catch (Exception e) {
			throw e;
		}
		
		for (Entry<String, Boolean> entry : filePrefixes.entrySet()) {
			assertEquals(String.format("File: '%s' must %sbe present in the export archive", entry.getKey(), entry.getValue() ? "" : "not "),
					entry.getValue(), existingFiles.contains(entry.getKey()));
		}
		
	}

	private static Multimap<String, Pair<Boolean, String>> collectLines(File exportArchive, Multimap<String, Pair<Boolean, String>> fileToLinesMap)
			throws Exception {
		assertTrue("Exported RF2 ZIP should be present in the local fs: " + exportArchive.toPath(), Files.exists(exportArchive.toPath()));
		Multimap<String, Pair<Boolean, String>> resultMap = ArrayListMultimap.create();

		try (FileSystem fs = FileSystems.newFileSystem(exportArchive.toPath(), (ClassLoader) null)) {
			for (Path path : fs.getRootDirectories()) {
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						for (String filePrefix : fileToLinesMap.asMap().keySet()) {
							/* 
							 * XXX: Need to add underscore to the end of each prefix here, as some file names 
							 * are prefixes of each other.
							 */
							if (file.getFileName().toString().startsWith(filePrefix + "_")) {
								collectLines(resultMap, file, filePrefix, fileToLinesMap.get(filePrefix));
								break;
							}
						}

						return super.visitFile(file, attrs);
					}
				});

			}

		} catch (Exception e) {
			throw e;
		}

		return resultMap;
	}

	private static void collectLines(Multimap<String, Pair<Boolean, String>> resultMap, Path file, String filePrefix,
			Collection<Pair<Boolean, String>> expectedLines) throws IOException {
		List<String> lines = Files.readAllLines(file);
		for (Pair<Boolean, String> line : expectedLines) {
			resultMap.put(filePrefix, Pair.of(lines.contains(line.getB()), line.getB()));
		}
	}
	
	@Test
	public void incorrectRf2ReleaseType() {
		export(branchPath, ImmutableMap.of("type", "unknown"))
			.then()
			.statusCode(400);
	}

	@Test
	public void exportUnpublishedDeltaRelationships() throws Exception {
		String statedRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.STATED_RELATIONSHIP);
		String inferredRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.INFERRED_RELATIONSHIP);
		String additionalRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.ADDITIONAL_RELATIONSHIP);
		String valueRelationshipId = createNewConcreteValue(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, new RelationshipValue(99));
		
		String transientEffectiveTime = "20170301";

		Map<String, ?> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("transientEffectiveTime", transientEffectiveTime)
				.build();

		File exportArchive = doExport(branchPath, config);

		String statedLine = TAB_JOINER.join(statedRelationshipId, 
				transientEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.STATED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 

		String inferredLine = TAB_JOINER.join(inferredRelationshipId, 
				transientEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.INFERRED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);

		String additionalLine = TAB_JOINER.join(additionalRelationshipId, 
				transientEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.ADDITIONAL_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 
		
		String valueLine = TAB_JOINER.join(valueRelationshipId, 
				transientEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				"#99",
				"0",
				Concepts.PART_OF,
				Concepts.INFERRED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 

		Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();

		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(true, statedLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, inferredLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, additionalLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, valueLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(false, statedLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, inferredLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, additionalLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(false, valueLine));
		fileToLinesMap.put("sct2_RelationshipConcreteValues", Pair.of(false, statedLine));
		fileToLinesMap.put("sct2_RelationshipConcreteValues", Pair.of(false, inferredLine));
		fileToLinesMap.put("sct2_RelationshipConcreteValues", Pair.of(false, additionalLine));
		fileToLinesMap.put("sct2_RelationshipConcreteValues", Pair.of(true, valueLine));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}

	@Test
	public void executeMultipleExportsAtTheSameTime() throws Exception {
		
		Promise<Attachment> first = SnomedRequests.rf2().prepareExport()
			.setReleaseType(Rf2ReleaseType.FULL)
			.setCountryNamespaceElement("INT")
			.setRefSetExportLayout(Rf2RefSetExportLayout.COMBINED)
			.setLocales(LOCALES)
			.build(SnomedDatastoreActivator.REPOSITORY_UUID, branchPath.getPath())
			.execute(getBus());
		
		Promise<Attachment> second = SnomedRequests.rf2().prepareExport()
			.setCountryNamespaceElement("INT")
			.setRefSetExportLayout(Rf2RefSetExportLayout.COMBINED)
			.setReleaseType(Rf2ReleaseType.SNAPSHOT)
			.setLocales(LOCALES)
			.build(SnomedDatastoreActivator.REPOSITORY_UUID, branchPath.getPath())
			.execute(getBus());
		
		String message = Promise.all(first, second)
			.then(input -> {
				Attachment firstResult = (Attachment) input.get(0);
				Attachment secondResult = (Attachment) input.get(1);
				
				InternalAttachmentRegistry fileRegistry = (InternalAttachmentRegistry) ApplicationContext.getServiceForClass(AttachmentRegistry.class);
				
				File firstArchive = fileRegistry.getAttachment(firstResult.getAttachmentId());
				File secondArchive = fileRegistry.getAttachment(secondResult.getAttachmentId());
				
				final Map<String, Boolean> firstArchiveMap = ImmutableMap.<String, Boolean>builder()
						.put("sct2_Concept_Full", true)
						.build();
						
				final Map<String, Boolean> secondArchiveMap = ImmutableMap.<String, Boolean>builder()
						.put("sct2_Concept_Snapshot", true)
						.build();
				
				try {
					assertArchiveContainsFiles(firstArchive, firstArchiveMap);
					assertArchiveContainsFiles(secondArchive, secondArchiveMap);
				} catch (Exception e) {
					return e.getMessage();
				}
				
				fileRegistry.delete(firstResult.getAttachmentId());
				fileRegistry.delete(secondResult.getAttachmentId());
				
				return null;
			})
			.fail(input -> input.getMessage())
			.getSync(2, TimeUnit.MINUTES);
		
		assertNull(message, message);
	}
	
	@Test
	public void exportDeltaInDateRangeFromVersion() throws Exception {
		createCodeSystem(branchPath, "SNOMEDCT-DELTA").statusCode(201);

		String statedRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.STATED_RELATIONSHIP);
		String inferredRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.INFERRED_RELATIONSHIP);
		String additionalRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.ADDITIONAL_RELATIONSHIP);

		String versionEffectiveTime = "20170302";
		createVersion("SNOMEDCT-DELTA", "v1", versionEffectiveTime).statusCode(201);
		IBranchPath versionPath = BranchPathUtils.createPath(branchPath, "v1");

		Map<String, ?> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("startEffectiveTime", versionEffectiveTime)
				.put("endEffectiveTime", versionEffectiveTime)
				.build();

		File exportArchive = doExport(versionPath, config);

		String statedLine = TAB_JOINER.join(statedRelationshipId, 
				versionEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.STATED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 

		String inferredLine = TAB_JOINER.join(inferredRelationshipId, 
				versionEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.INFERRED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);

		String additionalLine = TAB_JOINER.join(additionalRelationshipId, 
				versionEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.ADDITIONAL_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 

		Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();

		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(true, statedLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, inferredLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, additionalLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(false, statedLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, inferredLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, additionalLine));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}

	@Test
	public void exportDeltaInDateRangeAndUnpublishedComponents() throws Exception {
		createCodeSystem(branchPath, "SNOMEDCT-GAMMA").statusCode(201);

		String statedRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.STATED_RELATIONSHIP);
		String inferredRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.INFERRED_RELATIONSHIP);
		String additionalRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.ADDITIONAL_RELATIONSHIP);

		String relationshipEffectiveTime = "20170303";
		createVersion("SNOMEDCT-GAMMA", "v1", relationshipEffectiveTime).statusCode(201);

		String conceptId = createNewConcept(branchPath);
		String conceptEffectiveTime = "20170304";
		createVersion("SNOMEDCT-GAMMA", "v2", conceptEffectiveTime).statusCode(201);

		String descriptionId = createNewDescription(branchPath, conceptId);
		// do not version description

		Map<String, ?> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("startEffectiveTime", relationshipEffectiveTime)
				.put("endEffectiveTime", relationshipEffectiveTime)
				.put("includeUnpublished", true)
				.build();

		File exportArchive = doExport(branchPath, config);

		String statedLine = TAB_JOINER.join(statedRelationshipId, 
				relationshipEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.STATED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 

		String inferredLine = TAB_JOINER.join(inferredRelationshipId, 
				relationshipEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.INFERRED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);

		String additionalLine = TAB_JOINER.join(additionalRelationshipId, 
				relationshipEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.ROOT_CONCEPT, 
				Concepts.NAMESPACE_ROOT,
				"0",
				Concepts.PART_OF,
				Concepts.ADDITIONAL_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER); 

		String conceptLine = TAB_JOINER.join(conceptId, 
				conceptEffectiveTime, 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.PRIMITIVE);

		String descriptionLine = TAB_JOINER.join(descriptionId, 
				"", 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				conceptId, 
				"en",
				Concepts.SYNONYM, 
				"Description term", 
				Concepts.ONLY_INITIAL_CHARACTER_CASE_INSENSITIVE);

		Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();

		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(true, statedLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, inferredLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(false, additionalLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, inferredLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, additionalLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(false, statedLine));

		fileToLinesMap.put("sct2_Concept", Pair.of(false, conceptLine));
		fileToLinesMap.put("sct2_Description", Pair.of(true, descriptionLine));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportContentFromVersionFixerTask() throws Exception {
		String codeSystemShortName = "SNOMEDCT-FIXERTASK";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create a refset, a concept, and reference the concept from the refset
		final String createdRefSetId = createNewRefSet(branchPath, SnomedRefSetType.SIMPLE);
		final String createdConceptId = createNewConcept(branchPath, ROOT_CONCEPT);
		final String memberId = createNewRefSetMember(branchPath, createdConceptId, createdRefSetId);
		
		final String versionEffectiveTime = "20170301";
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);

		IBranchPath versionPath = BranchPathUtils.createPath(branchPath, "v1");
		IBranchPath taskBranch = BranchPathUtils.createPath(versionPath, "Fix01");
		
		// create fixer branch for version branch
		branching.createBranch(taskBranch).statusCode(201);
		
		// change an existing component
		final String newEffectiveTime = "20170302";
		updateRefSetMemberEffectiveTime(taskBranch, memberId, newEffectiveTime);
		
		getComponent(taskBranch, SnomedComponentType.MEMBER, memberId).statusCode(200)
			.body("effectiveTime", equalTo(newEffectiveTime))
			.body("released", equalTo(true));
		
		// add a new component with the same effective time as the version branch
		final String unpublishedMemberId = createNewRefSetMember(taskBranch, createdConceptId, createdRefSetId);
		updateRefSetMemberEffectiveTime(taskBranch, unpublishedMemberId, versionEffectiveTime);
		getComponent(taskBranch, SnomedComponentType.MEMBER, unpublishedMemberId).statusCode(200)
			.body("effectiveTime", equalTo(versionEffectiveTime))
			.body("released", equalTo(true));
		
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.SNAPSHOT.name())
				.put("startEffectiveTime", versionEffectiveTime)
				.build();
			
		final File exportArchive = doExport(taskBranch, config);
		
		String refsetMemberLine = getComponentLine(List.<String>of(memberId, newEffectiveTime, "1", MODULE_SCT_CORE, createdRefSetId, createdConceptId));
		String invalidRefsetMemberLine = getComponentLine(List.<String>of(memberId, versionEffectiveTime, "1", MODULE_SCT_CORE, createdRefSetId, createdConceptId));
		
		String newRefsetMemberLine = getComponentLine(List.<String>of(unpublishedMemberId, versionEffectiveTime, "1", MODULE_SCT_CORE, createdRefSetId, createdConceptId));
		
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
		
		String refsetFileName = "der2_Refset_SimpleSnapshot";
		
		fileToLinesMap.put(refsetFileName, Pair.of(true, refsetMemberLine));
		fileToLinesMap.put(refsetFileName, Pair.of(true, newRefsetMemberLine));
		fileToLinesMap.put(refsetFileName, Pair.of(false, invalidRefsetMemberLine));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportContentFromVersionFixerTaskTransEffTime() throws Exception {
		
		String codeSystemShortName = "SNOMEDCT-FIXERTASK-TRANSIENT";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create a refset, a concept, and reference the concept from the refset
		final String createdRefSetId = createNewRefSet(branchPath, SnomedRefSetType.SIMPLE);
		final String createdConceptId = createNewConcept(branchPath, ROOT_CONCEPT);
		final String memberId = createNewRefSetMember(branchPath, createdConceptId, createdRefSetId);
		
		final String versionEffectiveTime = "20170301";
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);
		
		IBranchPath versionPath = BranchPathUtils.createPath(branchPath, "v1");
		IBranchPath taskBranch = BranchPathUtils.createPath(versionPath, "Fix01");
		
		// create fixer branch for version branch
		branching.createBranch(taskBranch).statusCode(201);
		
		// change an existing component

		Map<?, ?> updateRequest = ImmutableMap.builder()
				.put("active", false)
				.put("commitComment", "Inactivated reference set member")
				.build();

		updateRefSetComponent(taskBranch, SnomedComponentType.MEMBER, memberId, updateRequest, false).statusCode(204);
		
		getComponent(taskBranch, SnomedComponentType.MEMBER, memberId).statusCode(200)
			.body("active", equalTo(false))
			.body("effectiveTime", equalTo(null))
			.body("released", equalTo(true));
		
		// add a new component
		String newMemberId = createNewRefSetMember(taskBranch, createdConceptId, createdRefSetId);
		
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.SNAPSHOT.name())
				.put("startEffectiveTime", versionEffectiveTime)
				.put("transientEffectiveTime", versionEffectiveTime)
				.put("includeUnpublished", true)
				.build();
			
		final File exportArchive = doExport(taskBranch, config);
		
		String refsetMemberLine = getComponentLine(List.<String>of(memberId, versionEffectiveTime, "0", MODULE_SCT_CORE, createdRefSetId, createdConceptId));
		String invalidRefsetMemberLine = getComponentLine(List.<String>of(memberId, versionEffectiveTime, "1", MODULE_SCT_CORE, createdRefSetId, createdConceptId));
		
		String newRefsetMemberLine = getComponentLine(List.<String>of(newMemberId, versionEffectiveTime, "1", MODULE_SCT_CORE, createdRefSetId, createdConceptId));
		
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
		
		String refsetFileName = "der2_Refset_SimpleSnapshot";
		
		fileToLinesMap.put(refsetFileName, Pair.of(true, refsetMemberLine));
		fileToLinesMap.put(refsetFileName, Pair.of(true, newRefsetMemberLine));
		fileToLinesMap.put(refsetFileName, Pair.of(false, invalidRefsetMemberLine));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportPublishedAndUnpublishedTextDef() throws Exception {
		final String codeSystemShortName = "SNOMEDCT-PUB-UNPUB-TEXTDEF";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create new concept
		final String conceptId = createNewConcept(branchPath, ROOT_CONCEPT);
		// create new text definition
		final String textDefinitionId = createNewDescription(branchPath, conceptId, Concepts.TEXT_DEFINITION, UK_ACCEPTABLE_MAP);

		// version new concept
		final String versionEffectiveTime = "20170301";
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);

		// create new text definition
		final String unpublishedTextDefinitionId = createNewDescription(branchPath, conceptId, Concepts.TEXT_DEFINITION, UK_ACCEPTABLE_MAP);
		
		// do not create new version
		
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("startEffectiveTime", versionEffectiveTime)
				.put("endEffectiveTime", versionEffectiveTime)
				.put("includeUnpublished", true)
				.build();
			
		final File exportArchive = doExport(branchPath, config);
		
		String textDefinitionLine = getComponentLine(List.<String> of(textDefinitionId, versionEffectiveTime, "1", MODULE_SCT_CORE, conceptId, "en",
				Concepts.TEXT_DEFINITION, "Description term", Concepts.ONLY_INITIAL_CHARACTER_CASE_INSENSITIVE));
		
		String unpublishedTextDefinitionLine = getComponentLine(List.<String> of(unpublishedTextDefinitionId, "", "1", MODULE_SCT_CORE, conceptId, "en",
				Concepts.TEXT_DEFINITION, "Description term", Concepts.ONLY_INITIAL_CHARACTER_CASE_INSENSITIVE));

		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
				
		fileToLinesMap.put("sct2_Description", Pair.of(false, textDefinitionLine));
		fileToLinesMap.put("sct2_Description", Pair.of(false, unpublishedTextDefinitionLine));
		
		fileToLinesMap.put("sct2_TextDefinition", Pair.of(true, textDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition", Pair.of(true, unpublishedTextDefinitionLine));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportAlwaysCreatesTextDef_DescAndLangRefsetFiles() throws Exception {
		final String codeSystemShortName = "SNOMEDCT-EMPTY-FILES";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);

		final String conceptId = createNewConcept(branchPath);
		final String versionEffectiveTime = "20170301";
		
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);

		getComponent(branchPath, SnomedComponentType.CONCEPT, conceptId).statusCode(200)
			.body("definitionStatusId", equalTo(Concepts.PRIMITIVE));
		
		changeToDefining(branchPath, conceptId);
		
		getComponent(branchPath, SnomedComponentType.CONCEPT, conceptId).statusCode(200)
			.body("definitionStatusId", equalTo(Concepts.FULLY_DEFINED));
		
		// create new version
		final String newVersionEffectiveTime = "20170302";
		createVersion(codeSystemShortName, "v2", newVersionEffectiveTime).statusCode(201);
		
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("startEffectiveTime", newVersionEffectiveTime)
				.put("endEffectiveTime", newVersionEffectiveTime)
				.build();
			
		final File exportArchive = doExport(branchPath, config);
		
		final Map<String, Boolean> files = ImmutableMap.<String, Boolean>builder()
				.put("sct2_Description", true)
				.put("sct2_TextDefinition", true)
				.put("der2_cRefset_LanguageDelta-en", true)
				.build();
				
		assertArchiveContainsFiles(exportArchive, files);
	}
	
	@Test
	public void exportTextDef_DescAndLangRefSetsPerLanguageCode() throws Exception {
		final String codeSystemShortName = "SNOMEDCT-EXPORT-PER-LANGUAGE";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create new concept
		final String conceptId = createNewConcept(branchPath);
		final String englishTextDefinitionId = createNewDescription(branchPath, conceptId, Concepts.TEXT_DEFINITION, UK_ACCEPTABLE_MAP, "en");
		final String danishTextDefinitionId = createNewDescription(branchPath, conceptId, Concepts.TEXT_DEFINITION, UK_ACCEPTABLE_MAP, "da");
		final String englishDescriptionId = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "en");
		final String danishDescriptionId = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "da");
		
		// version new concept
		final String versionEffectiveTime = "20170301";
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);

		final String unpublishedEnglishTextDefinitionId = createNewDescription(branchPath, conceptId, Concepts.TEXT_DEFINITION, UK_ACCEPTABLE_MAP, "en");
		final String unpublishedDanishTextDefinitionId = createNewDescription(branchPath, conceptId, Concepts.TEXT_DEFINITION, UK_ACCEPTABLE_MAP, "da");
		final String unpublishedEnglishDescriptionId = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "en");
		final String unpublishedDanishDescriptionId = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "da");
		
		// do not create new version
		
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("startEffectiveTime", versionEffectiveTime)
				.put("endEffectiveTime", versionEffectiveTime)
				.put("includeUnpublished", true)
				.build();
			
		final File exportArchive = doExport(branchPath, config);
		
		final Map<String, Boolean> files = ImmutableMap.<String, Boolean>builder()
				.put("sct2_Description_Delta-en", true)
				.put("sct2_Description_Delta-da", true)
				.put("sct2_TextDefinition_Delta-en", true)
				.put("sct2_TextDefinition_Delta-da", true)
				.put("der2_cRefset_LanguageDelta-en", true)
				.put("der2_cRefset_LanguageDelta-da", true)
				.build();
				
		assertArchiveContainsFiles(exportArchive, files);
		
		String englishTextDefinitionLine = createDescriptionLine(englishTextDefinitionId, versionEffectiveTime, conceptId, "en", Concepts.TEXT_DEFINITION, DEFAULT_TERM);
		String danishTextDefinitionLine = createDescriptionLine(danishTextDefinitionId, versionEffectiveTime, conceptId, "da", Concepts.TEXT_DEFINITION, DEFAULT_TERM);
		String englishDescriptionLine = createDescriptionLine(englishDescriptionId, versionEffectiveTime, conceptId, "en", Concepts.SYNONYM, DEFAULT_TERM);
		String danishDescriptionLine = createDescriptionLine(danishDescriptionId, versionEffectiveTime, conceptId, "da", Concepts.SYNONYM, DEFAULT_TERM);
		
		String unpublishedEnglishTextDefinitionLine = createDescriptionLine(unpublishedEnglishTextDefinitionId, "", conceptId, "en", Concepts.TEXT_DEFINITION, DEFAULT_TERM);
		String unpublishedDanishTextDefinitionLine = createDescriptionLine(unpublishedDanishTextDefinitionId, "", conceptId, "da", Concepts.TEXT_DEFINITION, DEFAULT_TERM);
		String unpublishedEnglishDescriptionLine = createDescriptionLine(unpublishedEnglishDescriptionId, "", conceptId, "en", Concepts.SYNONYM, DEFAULT_TERM);
		String unpublishedDanishDescriptionLine = createDescriptionLine(unpublishedDanishDescriptionId, "", conceptId, "da", Concepts.SYNONYM, DEFAULT_TERM);
		
		String englishTextDefinitionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, englishTextDefinitionId, versionEffectiveTime);
		String danishTextDefinitionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, danishTextDefinitionId, versionEffectiveTime);
		String englishDescriptionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, englishDescriptionId, versionEffectiveTime);
		String danishDescriptionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, danishDescriptionId, versionEffectiveTime);
		
		String unpublishedEnglishTextDefinitionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, unpublishedEnglishTextDefinitionId, "");
		String unpublishedDanishTextDefinitionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, unpublishedDanishTextDefinitionId, "");
		String unpublishedEnglishDescriptionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, unpublishedEnglishDescriptionId, "");
		String unpublishedDanishDescriptionMemberLine = createAcceptableUKLanguageRefsetMemberLine(branchPath, unpublishedDanishDescriptionId, "");
		
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
				
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, englishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, danishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(true, englishDescriptionLine));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, danishDescriptionLine));
		
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(false, englishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(false, danishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(false, englishDescriptionLine));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(true, danishDescriptionLine));
		
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(true, englishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(false, danishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(false, englishDescriptionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(false, danishDescriptionLine));

		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(false, englishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(true, danishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(false, englishDescriptionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(false, danishDescriptionLine));
		
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, unpublishedEnglishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, unpublishedDanishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(true, unpublishedEnglishDescriptionLine));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, unpublishedDanishDescriptionLine));
		
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(false, unpublishedEnglishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(false, unpublishedDanishTextDefinitionLine));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(false, unpublishedEnglishDescriptionLine));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(true, unpublishedDanishDescriptionLine));
		
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(true, unpublishedEnglishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(false, unpublishedDanishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(false, unpublishedEnglishDescriptionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-en", Pair.of(false, unpublishedDanishDescriptionLine));

		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(false, unpublishedEnglishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(true, unpublishedDanishTextDefinitionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(false, unpublishedEnglishDescriptionLine));
		fileToLinesMap.put("sct2_TextDefinition_Delta-da", Pair.of(false, unpublishedDanishDescriptionLine));
		
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, englishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(false, danishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, englishDescriptionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(false, danishDescriptionMemberLine));
		
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(false, englishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, danishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(false, englishDescriptionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, danishDescriptionMemberLine));
		
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, unpublishedEnglishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(false, unpublishedDanishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, unpublishedEnglishDescriptionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(false, unpublishedDanishDescriptionMemberLine));
		
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(false, unpublishedEnglishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, unpublishedDanishTextDefinitionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(false, unpublishedEnglishDescriptionMemberLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, unpublishedDanishDescriptionMemberLine));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportLangRefset_acceptabilityChangesOnly() throws Exception {
		
		final String codeSystemShortName = "SNOMEDCT-EXPORT-UNPUBLISHED-LANG-REFSET-MEMBERS";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create new concept
		final String conceptId = createNewConcept(branchPath);
		
		Map<String, Acceptability> acceptabilityMap = ImmutableMap.<String, Acceptability>builder()
			.put(Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.PREFERRED)
			.put(Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.ACCEPTABLE)
			.build();
		
		final String descriptionId = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, acceptabilityMap, "en");
		
		// version new concept
		final String versionEffectiveTime = "20170801";
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);
		
		SnomedReferenceSetMembers versionedMembers = getComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionId, "members()")
				.statusCode(200)
				.extract().as(SnomedDescription.class)
				.getMembers();

		assertEquals(2, versionedMembers.getTotal());
		versionedMembers.forEach(m -> assertEquals(EffectiveTimes.parse(versionEffectiveTime, DateFormats.SHORT), m.getEffectiveTime()));

		Map<String, Acceptability> updatedAcceptabilityMap = ImmutableMap.<String, Acceptability>builder()
				.put(Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.ACCEPTABLE)
				.put(Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.PREFERRED)
				.build();
		
		Map<?, ?> requestBody = ImmutableMap.builder()
				.put("acceptability", updatedAcceptabilityMap)
				.put("commitComment", "Updated description acceptability")
				.build();

		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionId, requestBody).statusCode(204);
		
		SnomedReferenceSetMembers unpublishedMembers = getComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionId, "members()")
				.statusCode(200)
				.extract().as(SnomedDescription.class)
				.getMembers();

		assertEquals(2, unpublishedMembers.getTotal());
		unpublishedMembers.forEach(m -> assertNull(m.getEffectiveTime()));

		// do not create new version
		
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("includeUnpublished", true)
				.build();
			
		final File exportArchive = doExport(branchPath, config);
		
		String englishDescriptionLine = createDescriptionLine(descriptionId, versionEffectiveTime, conceptId, "en", Concepts.SYNONYM, DEFAULT_TERM);
		
		String ukMember = createLanguageRefsetMemberLine(branchPath, descriptionId, "", Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.ACCEPTABLE.getConceptId());
		String usMember = createLanguageRefsetMemberLine(branchPath, descriptionId, "", Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.PREFERRED.getConceptId());
		
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
				
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(false, englishDescriptionLine));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, ukMember));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, usMember));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportLangRefset_acceptabilityAndDescChanges() throws Exception {
		
		final String codeSystemShortName = "SNOMEDCT-ACCEPTABILITY-CHANGES";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create new concept
		final String conceptId = createNewConcept(branchPath);
		final String descriptionIdA = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "en");
		final String descriptionIdB = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "da");
		final String descriptionIdC = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "en");
		final String descriptionIdD = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "da");
		final String descriptionIdE = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_PREFERRED_MAP, "en");
		final String descriptionIdF = createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_PREFERRED_MAP, "da");
		
		// version new concept
		final String versionEffectiveTime = "20170801";
		createVersion(codeSystemShortName, "v1", versionEffectiveTime).statusCode(201);
		
		Map<?, ?> caseSignificanceChangeRequestBody = ImmutableMap.builder()
				.put("caseSignificance", Concepts.ENTIRE_TERM_CASE_SENSITIVE)
				.put("commitComment", "Updated description case significance")
				.build();
		Map<?, ?> acceptabilityChangeRequestBody = ImmutableMap.builder()
				.put("acceptability", UK_PREFERRED_MAP)
				.put("commitComment", "Updated description acceptability")
				.build();
		
		// Update description A
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdA, caseSignificanceChangeRequestBody).statusCode(204);
		// Update language refset member of description A
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdA, acceptabilityChangeRequestBody).statusCode(204);
		
		// Update description B
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdB, caseSignificanceChangeRequestBody).statusCode(204);
		// Update language refset member of description B
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdB, acceptabilityChangeRequestBody).statusCode(204);
		
		// Update language refset member of description C
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdC, acceptabilityChangeRequestBody).statusCode(204);
		// Update language refset member of description D
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdD, acceptabilityChangeRequestBody).statusCode(204);
		
		// Update description E
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdE, caseSignificanceChangeRequestBody).statusCode(204);
		// Update description F
		updateComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionIdF, caseSignificanceChangeRequestBody).statusCode(204);
		
		// export delta rf2
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("startEffectiveTime", versionEffectiveTime)
				.put("endEffectiveTime", versionEffectiveTime)
				.put("includeUnpublished", true)
				.build();
		
		final File exportArchive = doExport(branchPath, config);
	
		String descriptionLineA = createDescriptionLine(descriptionIdA, "", conceptId, "en", Concepts.SYNONYM, DEFAULT_TERM,
				Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		String descriptionLineB = createDescriptionLine(descriptionIdB, "", conceptId, "da", Concepts.SYNONYM, DEFAULT_TERM,
				Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		String descriptionLineC = createDescriptionLine(descriptionIdC, versionEffectiveTime, conceptId, "en", Concepts.SYNONYM, DEFAULT_TERM);

		String descriptionLineD = createDescriptionLine(descriptionIdD, versionEffectiveTime, conceptId, "da", Concepts.SYNONYM, DEFAULT_TERM);

		String descriptionLineE = createDescriptionLine(descriptionIdE, "", conceptId, "en", Concepts.SYNONYM, DEFAULT_TERM,
				Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		String descriptionLineF = createDescriptionLine(descriptionIdF, "", conceptId, "da", Concepts.SYNONYM, DEFAULT_TERM,
				Concepts.ENTIRE_TERM_CASE_SENSITIVE);

		String languageMemberLineA = createLanguageRefsetMemberLine(branchPath, descriptionIdA, "", Concepts.REFSET_LANGUAGE_TYPE_UK,
				Acceptability.PREFERRED.getConceptId());
		String languageMemberLineB = createLanguageRefsetMemberLine(branchPath, descriptionIdB, "", Concepts.REFSET_LANGUAGE_TYPE_UK,
				Acceptability.PREFERRED.getConceptId());
		String languageMemberLineC = createLanguageRefsetMemberLine(branchPath, descriptionIdC, "", Concepts.REFSET_LANGUAGE_TYPE_UK,
				Acceptability.PREFERRED.getConceptId());
		String languageMemberLineD = createLanguageRefsetMemberLine(branchPath, descriptionIdD, "", Concepts.REFSET_LANGUAGE_TYPE_UK,
				Acceptability.PREFERRED.getConceptId());
		String languageMemberLineE = createLanguageRefsetMemberLine(branchPath, descriptionIdE, versionEffectiveTime,
				Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.PREFERRED.getConceptId());
		String languageMemberLineF = createLanguageRefsetMemberLine(branchPath, descriptionIdF, versionEffectiveTime,
				Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.PREFERRED.getConceptId());

		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.create();
		
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(true, descriptionLineA));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(true, descriptionLineE));
		fileToLinesMap.put("sct2_Description_Delta-en", Pair.of(true, descriptionLineC));
		
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(true, descriptionLineB));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(true, descriptionLineF));
		fileToLinesMap.put("sct2_Description_Delta-da", Pair.of(true, descriptionLineD));
		
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, languageMemberLineA));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, languageMemberLineC));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-en", Pair.of(true, languageMemberLineE));
		
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, languageMemberLineB));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, languageMemberLineD));
		fileToLinesMap.put("der2_cRefset_LanguageDelta-da", Pair.of(true, languageMemberLineF));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Ignore // until external classification service support is ready 
	@Test
	public void exportConceptsAndRelationshipsOnly() throws Exception {
		
		final String codeSystemShortName = "SNOMEDCT-CONCEPTS-AND-RELATIONSHIPS-ONLY";
		createCodeSystem(branchPath, codeSystemShortName).statusCode(201);
		
		// create new concept
		final String conceptId = createNewConcept(branchPath);
		createNewDescription(branchPath, conceptId, Concepts.SYNONYM, UK_ACCEPTABLE_MAP, "en");
		
		String statedRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.STATED_RELATIONSHIP);
		String inferredRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.INFERRED_RELATIONSHIP);
		String additionalRelationshipId = createNewRelationship(branchPath, Concepts.ROOT_CONCEPT, Concepts.PART_OF, Concepts.NAMESPACE_ROOT, Concepts.ADDITIONAL_RELATIONSHIP);
		
		createNewRefSet(branchPath, SnomedRefSetType.OWL_AXIOM, Concepts.REFSET_OWL_AXIOM);
		
		String owlExpression = "";
		
		Json memberRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_OWL_AXIOM, Concepts.ROOT_CONCEPT)
				.with("owlExpression", owlExpression)
				.with("commitComment", "Created new OWL axiom reference set member");

		String memberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, memberRequestBody));
		
		// export delta rf2
		final Map<String, Object> config = ImmutableMap.<String, Object>builder()
				.put("type", Rf2ReleaseType.DELTA.name())
				.put("conceptsAndRelationshipsOnly", true)
				.put("includeUnpublished", true)
				.build();
		
		final File exportArchive = doExport(branchPath, config);
	
		String conceptLine = TAB_JOINER.join(conceptId, "", "1", Concepts.MODULE_SCT_CORE, Concepts.PRIMITIVE);
		
		String statedLine = TAB_JOINER.join(statedRelationshipId, "", "1", Concepts.MODULE_SCT_CORE, Concepts.ROOT_CONCEPT, Concepts.NAMESPACE_ROOT,
				"0", Concepts.PART_OF, Concepts.STATED_RELATIONSHIP, Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);

		String inferredLine = TAB_JOINER.join(inferredRelationshipId, "", "1", Concepts.MODULE_SCT_CORE, Concepts.ROOT_CONCEPT,
				Concepts.NAMESPACE_ROOT, "0", Concepts.PART_OF, Concepts.INFERRED_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);

		String additionalLine = TAB_JOINER.join(additionalRelationshipId, "", "1", Concepts.MODULE_SCT_CORE, Concepts.ROOT_CONCEPT,
				Concepts.NAMESPACE_ROOT, "0", Concepts.PART_OF, Concepts.ADDITIONAL_RELATIONSHIP,
				Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);
		
		String owlMemberLine = TAB_JOINER.join(memberId, "", "1", Concepts.MODULE_SCT_CORE, Concepts.REFSET_OWL_AXIOM, Concepts.ROOT_CONCEPT,
				owlExpression);

		final Map<String, Boolean> files = ImmutableMap.<String, Boolean>builder()
				.put("sct2_Concept", true)
				.put("sct2_StatedRelationship", true)
				.put("sct2_Relationship", true)
				.put("sct2_Description", false)
				.put("sct2_TextDefinition", false)
				.put("der2_cRefset_LanguageDelta", false)
				.put("der2_ssRefset_ModuleDependency", false)
				.put("sct2_sRefset_OWLExpression", true)
				.build();
				
		assertArchiveContainsFiles(exportArchive, files);
		
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.create();
		
		fileToLinesMap.put("sct2_Concept", Pair.of(true, conceptLine));
		fileToLinesMap.put("sct2_StatedRelationship", Pair.of(true, statedLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(true, inferredLine));
		fileToLinesMap.put("sct2_Relationship", Pair.of(false, additionalLine));
		fileToLinesMap.put("sct2_sRefset_OWLExpression", Pair.of(true, owlMemberLine));
		
		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportUnpublishedMRCMReferenceSetMembers() throws Exception {
		
		Json mrcmDomainRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL, Concepts.ROOT_CONCEPT)
				.with(Json.object(
					SnomedRf2Headers.FIELD_MRCM_DOMAIN_CONSTRAINT, "domainConstraint",
					SnomedRf2Headers.FIELD_MRCM_PARENT_DOMAIN, "parentDomain",
					SnomedRf2Headers.FIELD_MRCM_PROXIMAL_PRIMITIVE_CONSTRAINT, "proximalPrimitiveConstraint",
					SnomedRf2Headers.FIELD_MRCM_PROXIMAL_PRIMITIVE_REFINEMENT, "proximalPrimitiveRefinement",
					SnomedRf2Headers.FIELD_MRCM_DOMAIN_TEMPLATE_FOR_PRECOORDINATION, "domainTemplateForPrecoordination",
					SnomedRf2Headers.FIELD_MRCM_DOMAIN_TEMPLATE_FOR_POSTCOORDINATION, "domainTemplateForPostcoordination",
					SnomedRf2Headers.FIELD_MRCM_EDITORIAL_GUIDE_REFERENCE, "editorialGuideReference",
					"commitComment", "Created new MRCM domain reference set member"
				));

		String mrcmDomainRefsetMemberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, mrcmDomainRequestBody));
		
		Map<?, ?> mrcmAttributeDomainRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, Concepts.ROOT_CONCEPT)
				.with(Json.object(
					SnomedRf2Headers.FIELD_MRCM_DOMAIN_ID, Concepts.ROOT_CONCEPT,
					SnomedRf2Headers.FIELD_MRCM_GROUPED, Boolean.TRUE,
					SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_CARDINALITY, "attributeCardinality",
					SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_IN_GROUP_CARDINALITY, "attributeInGroupCardinality",
					SnomedRf2Headers.FIELD_MRCM_RULE_STRENGTH_ID, Concepts.ROOT_CONCEPT,
					SnomedRf2Headers.FIELD_MRCM_CONTENT_TYPE_ID, Concepts.ROOT_CONCEPT,
					"commitComment", "Created new MRCM attribute domain reference set member"
				));

		String mrcmAttributeDomainRefsetMemberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, mrcmAttributeDomainRequestBody));
		
		Map<?, ?> mrcmAttributeRangeRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, Concepts.ROOT_CONCEPT)
				.with(Json.object(
					SnomedRf2Headers.FIELD_MRCM_RANGE_CONSTRAINT, "rangeConstraint",
					SnomedRf2Headers.FIELD_MRCM_ATTRIBUTE_RULE, "attributeRule",
					SnomedRf2Headers.FIELD_MRCM_RULE_STRENGTH_ID, Concepts.ROOT_CONCEPT,
					SnomedRf2Headers.FIELD_MRCM_CONTENT_TYPE_ID, Concepts.ROOT_CONCEPT,
					"commitComment", "Created new MRCM attribute range reference set member"
				));

		String mrcmAttributeRangeRefsetMemberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, mrcmAttributeRangeRequestBody));
		
		Json mrcmModuleScopeRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_MRCM_MODULE_SCOPE, Concepts.ROOT_CONCEPT)
				.with(SnomedRf2Headers.FIELD_MRCM_RULE_REFSET_ID, Concepts.ROOT_CONCEPT)
				.with("commitComment", "Created new MRCM module scope reference set member");

		String mrcmModuleScopeRefsetMemberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, mrcmModuleScopeRequestBody));

		Json config = Json.object("type", Rf2ReleaseType.DELTA.name());

		File exportArchive = doExport(branchPath, config);

		String mrcmDomainMemberLine = TAB_JOINER.join(mrcmDomainRefsetMemberId, 
				"", 
				"1",
				Concepts.MODULE_SCT_CORE, 
				Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL, 
				Concepts.ROOT_CONCEPT,
				"domainConstraint",
				"parentDomain",
				"proximalPrimitiveConstraint",
				"proximalPrimitiveRefinement",
				"domainTemplateForPrecoordination",
				"domainTemplateForPostcoordination",
				"editorialGuideReference");

		String mrcmAttributeDomainLine = TAB_JOINER.join(mrcmAttributeDomainRefsetMemberId, 
				"", 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, 
				Concepts.ROOT_CONCEPT,
				Concepts.ROOT_CONCEPT,
				"1",
				"attributeCardinality",
				"attributeInGroupCardinality",
				Concepts.ROOT_CONCEPT,
				Concepts.ROOT_CONCEPT);

		String mrcmAttributeRangeLine = TAB_JOINER.join(mrcmAttributeRangeRefsetMemberId, 
				"", 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, 
				Concepts.ROOT_CONCEPT,
				"rangeConstraint",
				"attributeRule",
				Concepts.ROOT_CONCEPT,
				Concepts.ROOT_CONCEPT);
		
		String mrcmModuleScopeLine = TAB_JOINER.join(mrcmModuleScopeRefsetMemberId, 
				"", 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.REFSET_MRCM_MODULE_SCOPE, 
				Concepts.ROOT_CONCEPT,
				Concepts.ROOT_CONCEPT);
		
		final Map<String, Boolean> files = ImmutableMap.<String, Boolean>builder()
				.put("der2_sssssssRefset_MRCMDomainDelta", true)
				.put("der2_cissccRefset_MRCMAttributeDomainDelta", true)
				.put("der2_ssccRefset_MRCMAttributeRangeDelta", true)
				.put("der2_cRefset_MRCMModuleScopeDelta", true)
				.build();
			
		assertArchiveContainsFiles(exportArchive, files);

		Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();

		fileToLinesMap.put("der2_sssssssRefset_MRCMDomainDelta", Pair.of(true, mrcmDomainMemberLine));
		fileToLinesMap.put("der2_cissccRefset_MRCMAttributeDomainDelta", Pair.of(true, mrcmAttributeDomainLine));
		fileToLinesMap.put("der2_ssccRefset_MRCMAttributeRangeDelta", Pair.of(true, mrcmAttributeRangeLine));
		fileToLinesMap.put("der2_cRefset_MRCMModuleScopeDelta", Pair.of(true, mrcmModuleScopeLine));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportUnpublishedOWLExpressionRefsetMembers() throws Exception {
		
		Json owlOntologyRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_OWL_ONTOLOGY, Concepts.ROOT_CONCEPT)
				.with(SnomedRf2Headers.FIELD_OWL_EXPRESSION, SnomedApiTestConstants.OWL_ONTOLOGY_1)
				.with("commitComment", "Created new OWL Ontology reference set member");

		String owlOntologyRefsetMemberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, owlOntologyRequestBody));
		
		Json owlAxiomRequestBody = createRefSetMemberRequestBody(Concepts.REFSET_OWL_AXIOM, Concepts.ROOT_CONCEPT)
				.with(SnomedRf2Headers.FIELD_OWL_EXPRESSION, SnomedApiTestConstants.owlAxiom1(Concepts.ROOT_CONCEPT))
				.with("commitComment", "Created new OWL Axiom reference set member");

		String owlAxiomRefsetMemberId = assertCreated(createComponent(branchPath, SnomedComponentType.MEMBER, owlAxiomRequestBody));
		
		File exportArchive = doExport(branchPath, Json.object("type", Rf2ReleaseType.DELTA.name()));

		String owlOntologyMemberLine = TAB_JOINER.join(owlOntologyRefsetMemberId, 
				"", 
				"1",
				Concepts.MODULE_SCT_CORE, 
				Concepts.REFSET_OWL_ONTOLOGY, 
				Concepts.ROOT_CONCEPT,
				SnomedApiTestConstants.OWL_ONTOLOGY_1); 

		String owlAxiomMemberLine = TAB_JOINER.join(owlAxiomRefsetMemberId, 
				"", 
				"1", 
				Concepts.MODULE_SCT_CORE, 
				Concepts.REFSET_OWL_AXIOM,
				Concepts.ROOT_CONCEPT,
				SnomedApiTestConstants.owlAxiom1(Concepts.ROOT_CONCEPT));

		String expectedOwlExpressionDeltaFile = "sct2_sRefset_OWLExpressionDelta";
		final Map<String, Boolean> files = ImmutableMap.<String, Boolean>builder()
				.put(expectedOwlExpressionDeltaFile, true)
				.build();
			
		assertArchiveContainsFiles(exportArchive, files);

		Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();

		fileToLinesMap.put(expectedOwlExpressionDeltaFile, Pair.of(true, owlOntologyMemberLine));
		fileToLinesMap.put(expectedOwlExpressionDeltaFile, Pair.of(true, owlAxiomMemberLine));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);
	}
	
	@Test
	public void exportDeltaWithBranchPoint() throws Exception {
		final IBranchPath branchPoint = BranchPathUtils.createPath(branchPath.getPath() + RevisionIndex.AT_CHAR + "1234567");
		final Json exportConfig = Json.object("type", Rf2ReleaseType.DELTA.name());
		
		export(branchPoint, exportConfig)
			.then()
			.statusCode(400);
	}

	@Test
	public void exportSnapshotWithBranchPoint() throws Exception {

		final CommitResult commitResult = SnomedRequests.prepareCommit()
			.setBody(SnomedRequests.prepareNewRelationship()
				.setId(new NamespaceIdStrategy(""))
				.setActive(true)
				.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP)
				.setDestinationId(Concepts.ROOT_CONCEPT)
				.setModifierId(Concepts.EXISTENTIAL_RESTRICTION_MODIFIER)
				.setModuleId(Concepts.MODULE_SCT_CORE)
				.setGroup(0)
				.setSourceId(Concepts.ACCEPTABILITY)
				.setTypeId(Concepts.IS_A)
				.setUnionGroup(0))
			.setCommitComment("Created new relationship")
			.setAuthor(RestExtensions.USER)
			.build(SnomedDatastoreActivator.REPOSITORY_UUID, branchPath.getPath())
			.execute(getBus())
			.getSync(1L, TimeUnit.MINUTES);

		final long timestamp = commitResult.getCommitTimestamp() - 1L;
		final String relationshipId = commitResult.getResultAs(String.class);

		// id, effectiveTime, active, moduleId, sourceId, destinationId, relationshipGroup, typeId, characteristicTypeId, modifierId
		final String relationshipLine = getComponentLine(List.of(
			relationshipId, 
			"", 
			"1", 
			Concepts.MODULE_SCT_CORE, 
			Concepts.ACCEPTABILITY, 
			Concepts.ROOT_CONCEPT, 
			"0", 
			Concepts.IS_A, 
			Concepts.INFERRED_RELATIONSHIP, 
			Concepts.EXISTENTIAL_RESTRICTION_MODIFIER));

		final Map<String, Object> config = Map.of(
			"type", Rf2ReleaseType.SNAPSHOT.name(),
			"includeUnpublished", true
		);

		final File exportArchive = doExport(branchPath, config);

		final String relationshipFileName = "sct2_Relationship_Snapshot";
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
		fileToLinesMap.put(relationshipFileName, Pair.of(true, relationshipLine));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);

		/* 
		 * A snapshot export with a timestamp set before creating the relationship should result in the relationship
		 * not appearing in the export file.
		 */
		final IBranchPath branchPoint = BranchPathUtils.createPath(branchPath.getPath() + RevisionIndex.AT_CHAR + timestamp);
		final File exportArchiveWithTimestamp = doExport(branchPoint, config);
		fileToLinesMap.clear();
		fileToLinesMap.put(relationshipFileName, Pair.of(false, relationshipLine));

		assertArchiveContainsLines(exportArchiveWithTimestamp, fileToLinesMap);
	}
	
	@Test
	public void exportSnapshotWithBranchRange() throws Exception {
		final IBranchPath taskBranchPath = BranchPathUtils.createPath(branchPath, "task");
		branching.createBranch(taskBranchPath).statusCode(201);
		
		final IBranchPath branchRange = BranchPathUtils.createPath(branchPath.getPath() + RevisionIndex.REV_RANGE + taskBranchPath.getPath());
		final Json exportConfig = Json.object("type", Rf2ReleaseType.SNAPSHOT.name());
		
		export(branchRange, exportConfig)
			.then()
			.statusCode(400);
	}
	
	@Test
	public void exportDeltaWithBranchRange() throws Exception {
		final String relationshipOnParent = createNewRelationship(branchPath);
		
		final IBranchPath taskBranchPath = BranchPathUtils.createPath(branchPath, "task");
		branching.createBranch(taskBranchPath).statusCode(201);

		final String relationshipOnTask = createNewRelationship(taskBranchPath);
		
		final Map<String, Object> config = Map.of(
			"type", Rf2ReleaseType.DELTA.name(),
			"includeUnpublished", true
		);
		
		// A "standard" delta export should include both unpublished lines
		final File exportArchive = doExport(taskBranchPath, config);
		
		final String relationshipFileName = "sct2_StatedRelationship_Delta";
		final Multimap<String, Pair<Boolean, String>> fileToLinesMap = ArrayListMultimap.<String, Pair<Boolean, String>>create();
		fileToLinesMap.put(relationshipFileName, Pair.of(true, createRelationshipLine(relationshipOnParent)));
		fileToLinesMap.put(relationshipFileName, Pair.of(true, createRelationshipLine(relationshipOnTask)));

		assertArchiveContainsLines(exportArchive, fileToLinesMap);
		
		// Delta export with a path range can restrict the set of visible components
		final IBranchPath branchRange = BranchPathUtils.createPath(branchPath.getPath() + RevisionIndex.REV_RANGE + taskBranchPath.getPath());
		final File exportArchiveWithBranchRange = doExport(branchRange, config);
		
		fileToLinesMap.clear();
		fileToLinesMap.put(relationshipFileName, Pair.of(false, createRelationshipLine(relationshipOnParent)));
		fileToLinesMap.put(relationshipFileName, Pair.of(true, createRelationshipLine(relationshipOnTask)));
		
		assertArchiveContainsLines(exportArchiveWithBranchRange, fileToLinesMap);
	}

	private String createRelationshipLine(final String relationshipId) {
		// id, effectiveTime, active, moduleId, sourceId, destinationId, relationshipGroup, typeId, characteristicTypeId, modifierId
		return getComponentLine(List.of(
			relationshipId, 
			"", 
			"1", 
			Concepts.MODULE_SCT_CORE, 
			Concepts.ROOT_CONCEPT, 
			Concepts.NAMESPACE_ROOT, 
			"0", 
			Concepts.PART_OF, 
			Concepts.STATED_RELATIONSHIP, 
			Concepts.EXISTENTIAL_RESTRICTION_MODIFIER));
	}
	
	private static String getLanguageRefsetMemberId(IBranchPath branchPath, String descriptionId, String languageRefsetId) {
		final Collection<Map<String, Object>> members = getComponent(branchPath, SnomedComponentType.DESCRIPTION, descriptionId, "members()").extract().body().path("members.items");
		return String.valueOf(members.stream().filter(member -> languageRefsetId.equals(member.get("referenceSetId"))).findFirst().get().get("id"));
	}
	
	private static String createDescriptionLine(String id, String effectiveTime, String conceptId, String languageCode, String type, String term) {
		return createDescriptionLine(id, effectiveTime, conceptId, languageCode, type, term, Concepts.ONLY_INITIAL_CHARACTER_CASE_INSENSITIVE);
	}
	
	private static String createDescriptionLine(String id, String effectiveTime, String conceptId, String languageCode, String type, String term, String caseSignificance) {
		return getComponentLine(List.of(id, effectiveTime, "1", MODULE_SCT_CORE, conceptId, languageCode, type, term, caseSignificance));
	}
	
	private static String createLanguageRefsetMemberLine(IBranchPath branchPath, String descriptionId, String effectiveTime, String languageRefsetId, String acceptabilityId) {
		return getComponentLine(List.of(getLanguageRefsetMemberId(branchPath, descriptionId, languageRefsetId), effectiveTime, "1", MODULE_SCT_CORE, languageRefsetId, descriptionId,
				acceptabilityId));
	}
	
	private static String createAcceptableUKLanguageRefsetMemberLine(IBranchPath branchPath, String descriptionId, String effectiveTime) {
		return createLanguageRefsetMemberLine(branchPath, descriptionId, effectiveTime, Concepts.REFSET_LANGUAGE_TYPE_UK, Acceptability.ACCEPTABLE.getConceptId());
	}

	private static String getComponentLine(final List<String> lineElements) {
		return Joiner.on("\t").join(lineElements);
	}
	
}
