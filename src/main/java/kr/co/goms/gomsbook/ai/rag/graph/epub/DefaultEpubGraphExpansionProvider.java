/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.graph.epub;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.reader.pkg.EpubManifestReader;
import kr.co.goms.gomsbook.ai.epub.reader.pkg.EpubSpineReader;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.rag.graph.GraphExpansionProvider;
import kr.co.goms.gomsbook.ai.rag.util.RagUtil;

/**
 * EPUB Manifest와 Spine을 이용하여 Vector 검색 Seed 문서의 인접 문서를 확장한다.
 */
public final class DefaultEpubGraphExpansionProvider implements GraphExpansionProvider {

	private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";
	private static final double ADJACENT_SCORE = 1.0;

	private final CurrentProjectProvider currentProjectProvider;
	private final EpubManifestReader manifestReader;
	private final EpubSpineReader spineReader;
	private final EpubGraphDocumentPolicy documentPolicy;
	
	public DefaultEpubGraphExpansionProvider(CurrentProjectProvider currentProjectProvider, EpubManifestReader manifestReader, EpubSpineReader spineReader, EpubGraphDocumentPolicy documentPolicy) {
		this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
		this.manifestReader = Objects.requireNonNull(manifestReader, "manifestReader must not be null");
		this.spineReader = Objects.requireNonNull(spineReader, "spineReader must not be null");
		this.documentPolicy = Objects.requireNonNull(documentPolicy, "documentPolicy must not be null");
	}

	@Override
	public Map<String, Double> expand(String projectId, String query, Set<String> seedSourcePaths) {

		if (seedSourcePaths == null || seedSourcePaths.isEmpty()) {
			System.out.println("[RAG][EPUB-GRAPH] No seed source paths.");
			return Map.of();
		}

		EpubProjectContext project = requireCurrentProject();
		Path packageDocument = requirePackageDocument(project);
		List<EpubManifestItem> manifestItems = manifestReader.read(packageDocument);
		List<EpubSpineItem> spineItems = spineReader.read(packageDocument);
		List<String> spineSourcePaths = createSpineSourcePaths(project, packageDocument, manifestItems, spineItems);

		System.out.println("[RAG][EPUB-GRAPH] projectId=" + projectId);
		System.out.println("[RAG][EPUB-GRAPH] projectRoot=" + project.getProjectRoot());
		System.out.println("[RAG][EPUB-GRAPH] packageDocument=" + packageDocument);
		System.out.println("[RAG][EPUB-GRAPH] rawSeeds=" + seedSourcePaths);
		System.out.println("[RAG][EPUB-GRAPH] spineSourceCount=" + spineSourcePaths.size());
		System.out.println("[RAG][EPUB-GRAPH] spineSources=" + spineSourcePaths);

		if (spineSourcePaths.isEmpty()) {
			System.out.println("[RAG][EPUB-GRAPH] Spine source paths are empty.");
			return Map.of();
		}

		Set<String> normalizedSeeds = normalizeSeeds(seedSourcePaths);
		Map<String, Integer> spinePositions = createSpinePositions(spineSourcePaths);
		Map<String, Double> expanded = new LinkedHashMap<>();

		System.out.println("[RAG][EPUB-GRAPH] normalizedSeeds=" + normalizedSeeds);

		for (String seedSourcePath : normalizedSeeds) expandAdjacent(seedSourcePath, normalizedSeeds, spineSourcePaths, spinePositions, expanded);

		System.out.println("[RAG][EPUB-GRAPH] expandedSources=" + expanded);
		System.out.println("[RAG][EPUB-GRAPH] seeds=" + normalizedSeeds.size() + ", expanded=" + expanded.size());

		return Collections.unmodifiableMap(expanded);
	}

	@Override
	public boolean isAvailable() {

		try {

			EpubProjectContext project = currentProjectProvider.getCurrentProject();

			return project != null && project.hasPackageDocument();

		} catch (Exception exception) {

			return false;
		}
	}

	private EpubProjectContext requireCurrentProject() {

		EpubProjectContext project = currentProjectProvider.getCurrentProject();

		if (project == null) throw new IllegalStateException("Current EPUB project is not available.");

		return project;
	}

	private Path requirePackageDocument(EpubProjectContext project) {

		Path packageDocument = project.getPackageDocument();

		if (packageDocument == null) throw new IllegalStateException("Current EPUB package document is not available.");
		if (!project.hasPackageDocument()) throw new IllegalStateException("Current EPUB package document does not exist: " + packageDocument);

		return packageDocument.toAbsolutePath().normalize();
	}

	private List<String> createSpineSourcePaths(EpubProjectContext project, Path packageDocument, List<EpubManifestItem> manifestItems, List<EpubSpineItem> spineItems) {

		if (manifestItems == null || manifestItems.isEmpty()) {
			System.out.println("[RAG][EPUB-GRAPH] Manifest items are empty.");
			return List.of();
		}

		if (spineItems == null || spineItems.isEmpty()) {
			System.out.println("[RAG][EPUB-GRAPH] Spine items are empty.");
			return List.of();
		}

		Map<String, EpubManifestItem> manifestById = createManifestById(manifestItems);
		List<String> sourcePaths = new ArrayList<>();
		Set<String> discovered = new LinkedHashSet<>();

		System.out.println("[RAG][EPUB-GRAPH] manifestItems=" + manifestItems.size() + ", spineItems=" + spineItems.size());

		for (EpubSpineItem spineItem : spineItems) {

			if (spineItem == null) continue;

			if (!spineItem.isLinear()) {
				System.out.println("[RAG][EPUB-GRAPH] spine skipped - non-linear: " + spineItem.getIdref());
				continue;
			}

			EpubManifestItem manifestItem = manifestById.get(spineItem.getIdref());

			if (manifestItem == null) {
				System.out.println("[RAG][EPUB-GRAPH] manifest not found for idref=" + spineItem.getIdref());
				continue;
			}

			if (!isXhtmlManifestItem(manifestItem)) {
				System.out.println("[RAG][EPUB-GRAPH] manifest skipped - non-xhtml: id=" + manifestItem.getId() + ", href=" + manifestItem.getHref() + ", mediaType=" + manifestItem.getMediaType());
				continue;
			}

			String sourcePath = resolveSourcePath(project.getProjectRoot(), packageDocument.getParent(), manifestItem.getHref());

			if (sourcePath == null) {
				System.out.println("[RAG][EPUB-GRAPH] source path resolve failed: href=" + manifestItem.getHref());
				continue;
			}

			if (RagUtil.isExcludedDocument(sourcePath)) {
				System.out.println("[RAG][EPUB-GRAPH] source skipped - excluded: " + sourcePath);
				continue;
			}

			if (!discovered.add(sourcePath)) {
				System.out.println("[RAG][EPUB-GRAPH] source skipped - duplicate: " + sourcePath);
				continue;
			}

			sourcePaths.add(sourcePath);

			System.out.println("[RAG][EPUB-GRAPH] spine source added: idref=" + spineItem.getIdref() + ", source=" + sourcePath);
		}

		return List.copyOf(sourcePaths);
	}

	private Map<String, EpubManifestItem> createManifestById(List<EpubManifestItem> manifestItems) {

		Map<String, EpubManifestItem> manifestById = new LinkedHashMap<>();

		for (EpubManifestItem manifestItem : manifestItems) {

			if (manifestItem == null) continue;

			manifestById.put(manifestItem.getId(), manifestItem);
		}

		return manifestById;
	}

	private boolean isXhtmlManifestItem(EpubManifestItem manifestItem) {
		return manifestItem != null && XHTML_MEDIA_TYPE.equalsIgnoreCase(manifestItem.getMediaType());
	}

	private String resolveSourcePath(Path projectRoot, Path packageDirectory, String href) {

		if (projectRoot == null || packageDirectory == null || href == null || href.isBlank()) return null;

		String normalizedHref = stripQueryAndFragment(href);

		if (normalizedHref == null || normalizedHref.isBlank()) return null;

		Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
		Path resourcePath = packageDirectory.resolve(normalizedHref).normalize();

		if (!resourcePath.startsWith(normalizedProjectRoot)) {
			System.out.println("[RAG][EPUB-GRAPH] resource outside project root: " + resourcePath);
			return null;
		}

		String sourcePath = RagUtil.normalizeDocumentPath(normalizedProjectRoot.relativize(resourcePath).toString());

		System.out.println("[RAG][EPUB-GRAPH] href=" + href + " -> source=" + sourcePath);

		return sourcePath;
	}

	private Set<String> normalizeSeeds(Set<String> seedSourcePaths) {

		Set<String> normalizedSeeds = new LinkedHashSet<>();

		for (String seedSourcePath : seedSourcePaths) {

			String normalized = RagUtil.normalizeDocumentPath(seedSourcePath);

			if (normalized == null || RagUtil.isExcludedDocument(normalized)) continue;

			if (!documentPolicy.isEligible(normalized)) {
				System.out.println("[RAG][EPUB-GRAPH] seed skipped - graph policy: " + normalized);
				continue;
			}

			normalizedSeeds.add(normalized);
		}

		return Collections.unmodifiableSet(normalizedSeeds);
	}

	private Map<String, Integer> createSpinePositions(List<String> spineSourcePaths) {

		Map<String, Integer> positions = new LinkedHashMap<>();

		for (int index = 0; index < spineSourcePaths.size(); index++) positions.put(spineSourcePaths.get(index), index);

		return positions;
	}

	private void expandAdjacent(String seedSourcePath, Set<String> seedSourcePaths, List<String> spineSourcePaths, Map<String, Integer> spinePositions, Map<String, Double> expanded) {

		Integer position = spinePositions.get(seedSourcePath);

		if (position == null) {
			System.out.println("[RAG][EPUB-GRAPH] seed not found in spine: " + seedSourcePath);
			return;
		}

		System.out.println("[RAG][EPUB-GRAPH] seed found: source=" + seedSourcePath + ", position=" + position);

		addCandidate("PREVIOUS", position - 1, seedSourcePaths, spineSourcePaths, expanded);
		addCandidate("NEXT", position + 1, seedSourcePaths, spineSourcePaths, expanded);
	}

	private void addCandidate(String relation, int position, Set<String> seedSourcePaths, List<String> spineSourcePaths, Map<String, Double> expanded) {

		if (position < 0 || position >= spineSourcePaths.size()) {
			System.out.println("[RAG][EPUB-GRAPH] candidate skipped - " + relation + " out of range: position=" + position);
			return;
		}

		String sourcePath = spineSourcePaths.get(position);

		if (sourcePath == null) return;

		if (seedSourcePaths.contains(sourcePath)) {
			System.out.println("[RAG][EPUB-GRAPH] candidate skipped - " + relation + " already seed: " + sourcePath);
			return;
		}

		if (RagUtil.isExcludedDocument(sourcePath)) {
			System.out.println("[RAG][EPUB-GRAPH] candidate skipped - " + relation + " excluded: " + sourcePath);
			return;
		}

		if (!documentPolicy.isEligible(sourcePath)) {
			System.out.println("[RAG][EPUB-GRAPH] candidate skipped - " + relation + " graph policy: " + sourcePath);
			return;
		}

		expanded.merge(sourcePath, ADJACENT_SCORE, Math::max);

		System.out.println("[RAG][EPUB-GRAPH] candidate added - " + relation + ": " + sourcePath + ", score=" + ADJACENT_SCORE);
	}

	private String stripQueryAndFragment(String value) {

		if (value == null || value.isBlank()) return null;

		int queryIndex = value.indexOf('?');
		int fragmentIndex = value.indexOf('#');
		int endIndex = value.length();

		if (queryIndex >= 0) endIndex = Math.min(endIndex, queryIndex);
		if (fragmentIndex >= 0) endIndex = Math.min(endIndex, fragmentIndex);

		return value.substring(0, endIndex);
	}
}