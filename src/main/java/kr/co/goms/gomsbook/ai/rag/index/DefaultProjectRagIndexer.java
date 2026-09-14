/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.rag.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.rag.constant.RagConstant;
import kr.co.goms.gomsbook.ai.rag.document.DocumentLoader;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingClient;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingPurpose;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingRequest;
import kr.co.goms.gomsbook.ai.rag.embedding.EmbeddingResponse;
import kr.co.goms.gomsbook.ai.rag.expansion.ChunkContextProvider;
import kr.co.goms.gomsbook.ai.rag.model.DocumentChunk;
import kr.co.goms.gomsbook.ai.rag.model.DocumentSource;
import kr.co.goms.gomsbook.ai.rag.util.RagUtil;
import kr.co.goms.gomsbook.ai.rag.vector.VectorRecord;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStore;
import kr.co.goms.gomsbook.ai.rag.vector.VectorStoreException;
import kr.co.goms.gomsbook.ai.util.GomsStringUtil;

/**
 * 기본 {@link ProjectRagIndexer} 구현체입니다.
 *
 * <p>
 * 현재 EPUB 프로젝트의 TEXT 디렉터리와 VectorStore를 비교하여
 * 증분 방식으로 RAG 인덱스를 동기화합니다.
 * </p>
 *
 * <p>
 * 다음 상태를 처리합니다.
 * </p>
 *
 * <ul>
 *     <li>NEW - 새 XHTML 문서를 인덱싱</li>
 *     <li>CHANGED - 변경된 XHTML 문서를 재인덱싱</li>
 *     <li>UNCHANGED - 변경되지 않은 XHTML 문서를 건너뜀</li>
 *     <li>DELETED - 삭제된 XHTML의 stale vector 제거</li>
 * </ul>
 *
 * <p>
 * Context Expansion을 위해 VectorStore와 ChunkContextProvider도
 * 동일한 DocumentChunk 상태를 유지합니다.
 * </p>
 */
public final class DefaultProjectRagIndexer implements ProjectRagIndexer {

	private static final Set<String> DEFAULT_EXCLUDED_FILES = Set.of("quiz.xhtml");
	
    private final DocumentLoader documentLoader;
    private final DocumentIndexer documentIndexer;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final ChunkContextProvider chunkContextProvider;

    public DefaultProjectRagIndexer(
            DocumentLoader documentLoader,
            DocumentIndexer documentIndexer,
            EmbeddingClient embeddingClient,
            VectorStore vectorStore,
            ChunkContextProvider chunkContextProvider) {

        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader must not be null");
        this.documentIndexer = Objects.requireNonNull(documentIndexer, "documentIndexer must not be null");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.chunkContextProvider = Objects.requireNonNull(chunkContextProvider, "chunkContextProvider must not be null");
    }

    @Override
    public ProjectIndexResult synchronize(EpubProjectContext project) throws ProjectIndexException {

        if (project == null) {
            throw new ProjectIndexException("EPUB project must not be null.");
        }

        try {
            String embeddingModel = RagConstant.DEFAULT_EMBEDDING_MODEL;

            Path projectRoot = Objects.requireNonNull(project.getProjectRoot(), "projectRoot must not be null")
                    .toAbsolutePath()
                    .normalize();

            Path textDirectory = Objects.requireNonNull(project.getTextDirectory(), "textDirectory must not be null")
                    .toAbsolutePath()
                    .normalize();

            validateTextDirectory(textDirectory);

            /*
             * Index/Search/Context Expansion에서 동일한 projectId 규칙을 사용한다.
             */
            String projectId = createProjectId(projectRoot);

            /*
             * 기존 VectorStore에 저장된 DocumentChunk를 ContextProvider에 복원한다.
             *
             * 애플리케이션 시작 후 ContextProvider는 비어 있을 수 있으므로
             * UNCHANGED 문서에서도 Context Expansion이 가능하도록 한다.
             */
            synchronizeChunkContext(projectId, embeddingModel);

            List<Path> xhtmlFiles = findXhtmlFiles(textDirectory);

            int processedFiles = 0;
            int newFiles = 0;
            int reindexedFiles = 0;
            int skippedFiles = 0;
            int deletedFiles = 0;

            int createdChunks = 0;
            int createdEmbeddings = 0;
            int storedVectors = 0;

            int staleDeletedVectors = 0;
            int replacedVectors = 0;

            List<String> indexedFiles = new ArrayList<>();
            List<String> skippedFilePaths = new ArrayList<>();
            List<String> deletedFilePaths = new ArrayList<>();

            String textSourcePrefix = createTextSourcePrefix(projectRoot, textDirectory);
            Set<String> currentSourcePaths = createCurrentSourcePaths(projectRoot, xhtmlFiles);

            /*
             * 1. DELETED
             */
            List<VectorRecord> projectRecords = vectorStore.findByProjectAndModel(projectId, embeddingModel);
            Set<String> storedSourcePaths = new LinkedHashSet<>();

            for (VectorRecord record : projectRecords) {

                if (record == null || record.getChunk() == null) {
                    continue;
                }

                String sourcePath = GomsStringUtil.normalizePath(record.getChunk().getSourcePath());

                if (sourcePath.isBlank()) {
                    continue;
                }

                if (!sourcePath.startsWith(textSourcePrefix)) {
                    continue;
                }

                storedSourcePaths.add(sourcePath);
            }

            for (String storedSourcePath : storedSourcePaths) {

                if (currentSourcePaths.contains(storedSourcePath)) {
                    continue;
                }

                int deleted = vectorStore.deleteByProjectAndSourcePath(projectId, storedSourcePath, embeddingModel);

                if (deleted <= 0) {
                    continue;
                }

                /*
                 * VectorStore 삭제와 ContextProvider 삭제를 함께 처리한다.
                 */
                chunkContextProvider.removeBySource(projectId, storedSourcePath);

                deletedFiles++;
                staleDeletedVectors += deleted;
                deletedFilePaths.add(storedSourcePath);

                System.out.println("[RAG][DELETE] " + storedSourcePath + ", deletedVectors=" + deleted);
            }

            /*
             * 2. NEW / CHANGED / UNCHANGED
             */
            for (Path xhtmlFile : xhtmlFiles) {

                Path relativePath = projectRoot.relativize(xhtmlFile);
                String relativeSourcePath = GomsStringUtil.normalizePath(relativePath.toString());

                DocumentSource source = documentLoader.load(projectRoot, relativePath);

                if (!documentIndexer.supports(source)) {
                    System.out.println("[RAG][SKIP] Unsupported source: " + relativeSourcePath);
                    continue;
                }

                String sourceHash = RagUtil.sha256(source.getContent());

                List<VectorRecord> existingRecords = vectorStore.findByProjectAndSourcePath(projectId, relativeSourcePath)
                        .stream()
                        .filter(record -> record != null && record.isModel(embeddingModel))
                        .toList();

                /*
                 * UNCHANGED
                 *
                 * synchronizeChunkContext()에서 이미 기존 VectorStore의 Chunk를
                 * ContextProvider에 복원했으므로 별도 작업이 필요하지 않다.
                 */
                if (isSourceUnchanged(existingRecords, sourceHash)) {
                    skippedFiles++;
                    processedFiles++;
                    skippedFilePaths.add(relativeSourcePath);

                    System.out.println("[RAG][SKIP] " + relativeSourcePath + " - source unchanged");

                    continue;
                }

                boolean reindex = !existingRecords.isEmpty();

                List<DocumentChunk> chunks = documentIndexer.index(source);

                if (chunks == null) {
                    throw new IllegalStateException("DocumentIndexer returned null. source=" + relativeSourcePath);
                }

                createdChunks += chunks.size();

                System.out.println(
                        "[RAG] source=" + relativeSourcePath
                        + ", type=" + source.getType()
                        + ", contentLength=" + source.getContent().length()
                        + ", chunks=" + chunks.size()
                );

                /*
                 * 새 Vector를 먼저 전부 생성한다.
                 *
                 * Embedding 실패 시 기존 정상 Vector가 삭제되지 않도록
                 * CHANGED 삭제는 Embedding 완료 이후에 수행한다.
                 */
                List<VectorRecord> newRecords = new ArrayList<>();

                for (DocumentChunk chunk : chunks) {

                    if (chunk == null) {
                        continue;
                    }

                    String embeddingText = chunk.toEmbeddingText();

                    if (embeddingText == null || embeddingText.isBlank()) {
                        continue;
                    }

                    String contentHash = RagUtil.sha256(embeddingText);

                    EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                            .model(embeddingModel)
                            .input(embeddingText)
                            .purpose(EmbeddingPurpose.DOCUMENT)
                            .build();

                    EmbeddingResponse embeddingResponse = embeddingClient.embed(embeddingRequest);

                    if (embeddingResponse == null) {
                        throw new IllegalStateException("Embedding response is null. chunk=" + chunk.getId());
                    }

                    List<float[]> embeddings = embeddingResponse.getEmbeddings();

                    if (embeddings == null || embeddings.isEmpty()) {
                        throw new IllegalStateException("Embedding vector is empty. chunk=" + chunk.getId());
                    }

                    if (embeddings.size() != 1) {
                        throw new IllegalStateException(
                                "Unexpected embedding count. chunk="
                                + chunk.getId()
                                + ", count="
                                + embeddings.size()
                        );
                    }

                    float[] vector = embeddings.get(0);

                    if (vector == null || vector.length == 0) {
                        throw new IllegalStateException("Embedding vector is empty. chunk=" + chunk.getId());
                    }

                    createdEmbeddings++;

                    VectorRecord record = VectorRecord.builder()
                            .projectId(projectId)
                            .chunk(chunk)
                            .vector(vector)
                            .model(embeddingModel)
                            .contentHash(contentHash)
                            .sourceHash(sourceHash)
                            .normalized(embeddingResponse.isNormalized())
                            .indexedAt(System.currentTimeMillis())
                            .version(1L)
                            .build();

                    newRecords.add(record);
                }

                /*
                 * CHANGED
                 */
                if (reindex) {
                    int deleted = vectorStore.deleteByProjectAndSourcePath(projectId, relativeSourcePath, embeddingModel);

                    chunkContextProvider.removeBySource(projectId, relativeSourcePath);

                    replacedVectors += deleted;
                    reindexedFiles++;

                    System.out.println(
                            "[RAG][REINDEX] "
                            + relativeSourcePath
                            + ", oldVectors="
                            + deleted
                            + ", newVectors="
                            + newRecords.size()
                    );

                } else {
                    /*
                     * NEW
                     */
                    newFiles++;

                    System.out.println(
                            "[RAG][NEW] "
                            + relativeSourcePath
                            + ", vectors="
                            + newRecords.size()
                    );
                }

                /*
                 * VectorStore와 ChunkContextProvider를 같은 시점에 갱신한다.
                 */
                vectorStore.saveAll(newRecords);
                chunkContextProvider.addChunks(projectId, chunks);

                storedVectors += newRecords.size();
                processedFiles++;
                indexedFiles.add(relativeSourcePath);
            }

            long vectorStoreSize = vectorStore.count();

            return ProjectIndexResult.builder()
                    .projectId(projectId)
                    .projectName(project.getProjectName())
                    .textDirectory(textDirectory.toString())
                    .embeddingModel(embeddingModel)
                    .discoveredFiles(xhtmlFiles.size())
                    .processedFiles(processedFiles)
                    .newFiles(newFiles)
                    .reindexedFiles(reindexedFiles)
                    .skippedFiles(skippedFiles)
                    .deletedFiles(deletedFiles)
                    .createdChunks(createdChunks)
                    .createdEmbeddings(createdEmbeddings)
                    .storedVectors(storedVectors)
                    .deletedVectors(staleDeletedVectors + replacedVectors)
                    .vectorStoreSize(vectorStoreSize)
                    .indexedFiles(indexedFiles)
                    .skippedFilePaths(skippedFilePaths)
                    .deletedFilePaths(deletedFilePaths)
                    .build();

        } catch (Exception exception) {
            throw new ProjectIndexException(
                    "Failed to synchronize EPUB project RAG index: " + exception.getMessage(),
                    exception
            );
        }
    }

    @Override
    public ProjectIndexResult synchronize(EpubProjectContext project, ProjectIndexOptions options) throws ProjectIndexException {

        if (project == null) {
            throw new ProjectIndexException("EPUB project must not be null.");
        }

        ProjectIndexOptions resolvedOptions = options == null ? ProjectIndexOptions.defaults() : options;

        try {
            String embeddingModel = RagConstant.DEFAULT_EMBEDDING_MODEL;

            Path projectRoot = Objects.requireNonNull(project.getProjectRoot(), "projectRoot must not be null")
                    .toAbsolutePath()
                    .normalize();

            Path textDirectory = Objects.requireNonNull(project.getTextDirectory(), "textDirectory must not be null")
                    .toAbsolutePath()
                    .normalize();

            validateTextDirectory(textDirectory);

            String projectId = createProjectId(projectRoot);

            synchronizeChunkContext(projectId, embeddingModel);

            Set<String> excludedFiles = createExcludedFiles(resolvedOptions);

            List<Path> xhtmlFiles = findXhtmlFiles(
                    textDirectory,
                    projectRoot,
                    excludedFiles
            );

            int processedFiles = 0;
            int newFiles = 0;
            int reindexedFiles = 0;
            int skippedFiles = 0;
            int deletedFiles = 0;

            int createdChunks = 0;
            int createdEmbeddings = 0;
            int storedVectors = 0;

            int staleDeletedVectors = 0;
            int replacedVectors = 0;

            List<String> indexedFiles = new ArrayList<>();
            List<String> skippedFilePaths = new ArrayList<>();
            List<String> deletedFilePaths = new ArrayList<>();

            String textSourcePrefix = createTextSourcePrefix(projectRoot, textDirectory);
            Set<String> currentSourcePaths = createCurrentSourcePaths(projectRoot, xhtmlFiles);

            /*
             * 1. DELETED
             *
             * 실제 파일이 삭제되었거나,
             * 기본/옵션 제외 정책으로 현재 인덱싱 대상에서 제외된 문서를
             * VectorStore와 ChunkContextProvider에서 제거합니다.
             */
            List<VectorRecord> projectRecords = vectorStore.findByProjectAndModel(projectId, embeddingModel);
            Set<String> storedSourcePaths = new LinkedHashSet<>();

            for (VectorRecord record : projectRecords) {

                if (record == null || record.getChunk() == null) {
                    continue;
                }

                String sourcePath = GomsStringUtil.normalizePath(record.getChunk().getSourcePath());

                if (sourcePath.isBlank()) {
                    continue;
                }

                if (!sourcePath.startsWith(textSourcePrefix)) {
                    continue;
                }

                storedSourcePaths.add(sourcePath);
            }

            for (String storedSourcePath : storedSourcePaths) {

                if (currentSourcePaths.contains(storedSourcePath)) {
                    continue;
                }

                int deleted = vectorStore.deleteByProjectAndSourcePath(projectId, storedSourcePath, embeddingModel);

                if (deleted <= 0) {
                    continue;
                }

                chunkContextProvider.removeBySource(projectId, storedSourcePath);

                deletedFiles++;
                staleDeletedVectors += deleted;
                deletedFilePaths.add(storedSourcePath);

                System.out.println("[RAG][DELETE] " + storedSourcePath + ", deletedVectors=" + deleted);
            }

            /*
             * 2. NEW / CHANGED / UNCHANGED
             */
            for (Path xhtmlFile : xhtmlFiles) {

                Path relativePath = projectRoot.relativize(xhtmlFile);
                String relativeSourcePath = GomsStringUtil.normalizePath(relativePath.toString());

                DocumentSource source = documentLoader.load(projectRoot, relativePath);

                if (!documentIndexer.supports(source)) {
                    System.out.println("[RAG][SKIP] Unsupported source: " + relativeSourcePath);
                    continue;
                }

                String sourceHash = RagUtil.sha256(source.getContent());

                List<VectorRecord> existingRecords = vectorStore.findByProjectAndSourcePath(projectId, relativeSourcePath)
                        .stream()
                        .filter(record -> record != null && record.isModel(embeddingModel))
                        .toList();

                if (isSourceUnchanged(existingRecords, sourceHash)) {
                    skippedFiles++;
                    processedFiles++;
                    skippedFilePaths.add(relativeSourcePath);

                    System.out.println("[RAG][SKIP] " + relativeSourcePath + " - source unchanged");

                    continue;
                }

                boolean reindex = !existingRecords.isEmpty();

                List<DocumentChunk> chunks = documentIndexer.index(source);

                if (chunks == null) {
                    throw new IllegalStateException("DocumentIndexer returned null. source=" + relativeSourcePath);
                }

                createdChunks += chunks.size();

                System.out.println(
                        "[RAG] source=" + relativeSourcePath
                        + ", type=" + source.getType()
                        + ", contentLength=" + source.getContent().length()
                        + ", chunks=" + chunks.size()
                );

                List<VectorRecord> newRecords = new ArrayList<>();

                for (DocumentChunk chunk : chunks) {

                    if (chunk == null) {
                        continue;
                    }

                    String embeddingText = chunk.toEmbeddingText();

                    if (embeddingText == null || embeddingText.isBlank()) {
                        continue;
                    }

                    String contentHash = RagUtil.sha256(embeddingText);

                    EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                            .model(embeddingModel)
                            .input(embeddingText)
                            .purpose(EmbeddingPurpose.DOCUMENT)
                            .build();

                    EmbeddingResponse embeddingResponse = embeddingClient.embed(embeddingRequest);

                    if (embeddingResponse == null) {
                        throw new IllegalStateException("Embedding response is null. chunk=" + chunk.getId());
                    }

                    List<float[]> embeddings = embeddingResponse.getEmbeddings();

                    if (embeddings == null || embeddings.isEmpty()) {
                        throw new IllegalStateException("Embedding vector is empty. chunk=" + chunk.getId());
                    }

                    if (embeddings.size() != 1) {
                        throw new IllegalStateException(
                                "Unexpected embedding count. chunk="
                                + chunk.getId()
                                + ", count="
                                + embeddings.size()
                        );
                    }

                    float[] vector = embeddings.get(0);

                    if (vector == null || vector.length == 0) {
                        throw new IllegalStateException("Embedding vector is empty. chunk=" + chunk.getId());
                    }

                    createdEmbeddings++;

                    VectorRecord record = VectorRecord.builder()
                            .projectId(projectId)
                            .chunk(chunk)
                            .vector(vector)
                            .model(embeddingModel)
                            .contentHash(contentHash)
                            .sourceHash(sourceHash)
                            .normalized(embeddingResponse.isNormalized())
                            .indexedAt(System.currentTimeMillis())
                            .version(1L)
                            .build();

                    newRecords.add(record);
                }

                if (reindex) {

                    int deleted = vectorStore.deleteByProjectAndSourcePath(projectId, relativeSourcePath, embeddingModel);

                    chunkContextProvider.removeBySource(projectId, relativeSourcePath);

                    replacedVectors += deleted;
                    reindexedFiles++;

                    System.out.println(
                            "[RAG][REINDEX] "
                            + relativeSourcePath
                            + ", oldVectors="
                            + deleted
                            + ", newVectors="
                            + newRecords.size()
                    );

                } else {

                    newFiles++;

                    System.out.println(
                            "[RAG][NEW] "
                            + relativeSourcePath
                            + ", vectors="
                            + newRecords.size()
                    );
                }

                vectorStore.saveAll(newRecords);
                chunkContextProvider.addChunks(projectId, chunks);

                storedVectors += newRecords.size();
                processedFiles++;
                indexedFiles.add(relativeSourcePath);
            }

            long vectorStoreSize = vectorStore.count();

            return ProjectIndexResult.builder()
                    .projectId(projectId)
                    .projectName(project.getProjectName())
                    .textDirectory(textDirectory.toString())
                    .embeddingModel(embeddingModel)
                    .discoveredFiles(xhtmlFiles.size())
                    .processedFiles(processedFiles)
                    .newFiles(newFiles)
                    .reindexedFiles(reindexedFiles)
                    .skippedFiles(skippedFiles)
                    .deletedFiles(deletedFiles)
                    .createdChunks(createdChunks)
                    .createdEmbeddings(createdEmbeddings)
                    .storedVectors(storedVectors)
                    .deletedVectors(staleDeletedVectors + replacedVectors)
                    .vectorStoreSize(vectorStoreSize)
                    .indexedFiles(indexedFiles)
                    .skippedFilePaths(skippedFilePaths)
                    .deletedFilePaths(deletedFilePaths)
                    .build();

        } catch (Exception exception) {
            throw new ProjectIndexException(
                    "Failed to synchronize EPUB project RAG index: " + exception.getMessage(),
                    exception
            );
        }
    }
    
    @Override
    public ProjectIndexResult synchronize(
            EpubProjectContext project,
            ProjectIndexOptions options,
            ProjectIndexProgressListener progressListener) throws ProjectIndexException {

        if (project == null) throw new ProjectIndexException("EPUB project must not be null.");

        ProjectIndexOptions resolvedOptions = options == null ? ProjectIndexOptions.defaults() : options;
        ProjectIndexProgressListener listener = progressListener == null ? ProjectIndexProgressListener.noop() : progressListener;

        String projectId = null;
        int totalFiles = 0;
        int currentFile = 0;

        try {

            String embeddingModel = RagConstant.DEFAULT_EMBEDDING_MODEL;

            Path projectRoot = Objects.requireNonNull(project.getProjectRoot(), "projectRoot must not be null")
                    .toAbsolutePath()
                    .normalize();

            Path textDirectory = Objects.requireNonNull(project.getTextDirectory(), "textDirectory must not be null")
                    .toAbsolutePath()
                    .normalize();

            validateTextDirectory(textDirectory);

            projectId = createProjectId(projectRoot);

            notifyProgress(
                    listener,
                    projectId,
                    ProjectIndexProgressStage.STARTED,
                    0,
                    0,
                    0,
                    null,
                    "RAG 인덱싱을 시작합니다."
            );

            /*
             * 기존 VectorStore의 Chunk를 ContextProvider에 복원합니다.
             */
            synchronizeChunkContext(projectId, embeddingModel);

            notifyProgress(
                    listener,
                    projectId,
                    ProjectIndexProgressStage.SCANNING,
                    0,
                    0,
                    0,
                    null,
                    "EPUB XHTML 문서를 검색하고 있습니다."
            );

            Set<String> excludedFiles = createExcludedFiles(resolvedOptions);

            List<Path> xhtmlFiles = findXhtmlFiles(
                    textDirectory,
                    projectRoot,
                    excludedFiles
            );

            totalFiles = xhtmlFiles.size();

            notifyProgress(
                    listener,
                    projectId,
                    ProjectIndexProgressStage.SCANNING,
                    0,
                    totalFiles,
                    0,
                    null,
                    totalFiles + "개의 XHTML 문서를 확인했습니다."
            );

            int processedFiles = 0;
            int newFiles = 0;
            int reindexedFiles = 0;
            int skippedFiles = 0;
            int deletedFiles = 0;

            int createdChunks = 0;
            int createdEmbeddings = 0;
            int storedVectors = 0;

            int staleDeletedVectors = 0;
            int replacedVectors = 0;

            List<String> indexedFiles = new ArrayList<>();
            List<String> skippedFilePaths = new ArrayList<>();
            List<String> deletedFilePaths = new ArrayList<>();

            String textSourcePrefix = createTextSourcePrefix(projectRoot, textDirectory);
            Set<String> currentSourcePaths = createCurrentSourcePaths(projectRoot, xhtmlFiles);

            /*
             * 1. DELETED
             *
             * 실제 파일이 삭제되었거나 제외 정책에 의해
             * 현재 인덱싱 대상에서 빠진 문서를 제거합니다.
             */
            List<VectorRecord> projectRecords = vectorStore.findByProjectAndModel(projectId, embeddingModel);
            Set<String> storedSourcePaths = new LinkedHashSet<>();

            for (VectorRecord record : projectRecords) {

                if (record == null || record.getChunk() == null) continue;

                String sourcePath = GomsStringUtil.normalizePath(record.getChunk().getSourcePath());

                if (sourcePath.isBlank()) continue;
                if (!sourcePath.startsWith(textSourcePrefix)) continue;

                storedSourcePaths.add(sourcePath);
            }

            for (String storedSourcePath : storedSourcePaths) {

                if (currentSourcePaths.contains(storedSourcePath)) continue;

                notifyProgress(
                        listener,
                        projectId,
                        ProjectIndexProgressStage.DELETING,
                        0,
                        totalFiles,
                        0,
                        storedSourcePath,
                        storedSourcePath + " 기존 인덱스를 제거하고 있습니다."
                );

                int deleted = vectorStore.deleteByProjectAndSourcePath(
                        projectId,
                        storedSourcePath,
                        embeddingModel
                );

                if (deleted <= 0) continue;

                chunkContextProvider.removeBySource(projectId, storedSourcePath);

                deletedFiles++;
                staleDeletedVectors += deleted;
                deletedFilePaths.add(storedSourcePath);

                System.out.println("[RAG][DELETE] " + storedSourcePath + ", deletedVectors=" + deleted);
            }

            /*
             * 2. NEW / CHANGED / UNCHANGED
             */
            for (Path xhtmlFile : xhtmlFiles) {

                currentFile++;

                Path relativePath = projectRoot.relativize(xhtmlFile);
                String relativeSourcePath = GomsStringUtil.normalizePath(relativePath.toString());
                int percent = calculatePercent(currentFile, totalFiles);

                notifyProgress(
                        listener,
                        projectId,
                        ProjectIndexProgressStage.INDEXING,
                        currentFile,
                        totalFiles,
                        percent,
                        relativeSourcePath,
                        relativeSourcePath + " 문서를 처리하고 있습니다."
                );

                DocumentSource source = documentLoader.load(projectRoot, relativePath);

                if (!documentIndexer.supports(source)) {

                    System.out.println("[RAG][SKIP] Unsupported source: " + relativeSourcePath);

                    continue;
                }

                String sourceHash = RagUtil.sha256(source.getContent());

                List<VectorRecord> existingRecords = vectorStore.findByProjectAndSourcePath(projectId, relativeSourcePath)
                        .stream()
                        .filter(record -> record != null && record.isModel(embeddingModel))
                        .toList();

                /*
                 * UNCHANGED
                 */
                if (isSourceUnchanged(existingRecords, sourceHash)) {

                    skippedFiles++;
                    processedFiles++;
                    skippedFilePaths.add(relativeSourcePath);

                    System.out.println("[RAG][SKIP] " + relativeSourcePath + " - source unchanged");

                    continue;
                }

                boolean reindex = !existingRecords.isEmpty();

                List<DocumentChunk> chunks = documentIndexer.index(source);

                if (chunks == null) {
                    throw new IllegalStateException("DocumentIndexer returned null. source=" + relativeSourcePath);
                }

                createdChunks += chunks.size();

                System.out.println(
                        "[RAG] source=" + relativeSourcePath
                        + ", type=" + source.getType()
                        + ", contentLength=" + source.getContent().length()
                        + ", chunks=" + chunks.size()
                );

                /*
                 * Embedding 완료 전까지 기존 CHANGED Vector는 삭제하지 않습니다.
                 */
                List<VectorRecord> newRecords = new ArrayList<>();

                int currentChunk = 0;
                int totalChunks = chunks.size();

                for (DocumentChunk chunk : chunks) {

                    if (chunk == null) continue;

                    String embeddingText = chunk.toEmbeddingText();

                    if (embeddingText == null || embeddingText.isBlank()) continue;

                    currentChunk++;

                    notifyProgress(
                            listener,
                            projectId,
                            ProjectIndexProgressStage.EMBEDDING,
                            currentFile,
                            totalFiles,
                            percent,
                            relativeSourcePath,
                            createEmbeddingMessage(relativeSourcePath, currentChunk, totalChunks)
                    );

                    String contentHash = RagUtil.sha256(embeddingText);

                    EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                            .model(embeddingModel)
                            .input(embeddingText)
                            .purpose(EmbeddingPurpose.DOCUMENT)
                            .build();

                    EmbeddingResponse embeddingResponse = embeddingClient.embed(embeddingRequest);

                    if (embeddingResponse == null) {
                        throw new IllegalStateException("Embedding response is null. chunk=" + chunk.getId());
                    }

                    List<float[]> embeddings = embeddingResponse.getEmbeddings();

                    if (embeddings == null || embeddings.isEmpty()) {
                        throw new IllegalStateException("Embedding vector is empty. chunk=" + chunk.getId());
                    }

                    if (embeddings.size() != 1) {
                        throw new IllegalStateException(
                                "Unexpected embedding count. chunk="
                                + chunk.getId()
                                + ", count="
                                + embeddings.size()
                        );
                    }

                    float[] vector = embeddings.get(0);

                    if (vector == null || vector.length == 0) {
                        throw new IllegalStateException("Embedding vector is empty. chunk=" + chunk.getId());
                    }

                    createdEmbeddings++;

                    VectorRecord record = VectorRecord.builder()
                            .projectId(projectId)
                            .chunk(chunk)
                            .vector(vector)
                            .model(embeddingModel)
                            .contentHash(contentHash)
                            .sourceHash(sourceHash)
                            .normalized(embeddingResponse.isNormalized())
                            .indexedAt(System.currentTimeMillis())
                            .version(1L)
                            .build();

                    newRecords.add(record);
                }

                /*
                 * CHANGED
                 */
                if (reindex) {

                    int deleted = vectorStore.deleteByProjectAndSourcePath(
                            projectId,
                            relativeSourcePath,
                            embeddingModel
                    );

                    chunkContextProvider.removeBySource(projectId, relativeSourcePath);

                    replacedVectors += deleted;
                    reindexedFiles++;

                    System.out.println(
                            "[RAG][REINDEX] "
                            + relativeSourcePath
                            + ", oldVectors="
                            + deleted
                            + ", newVectors="
                            + newRecords.size()
                    );

                } else {

                    /*
                     * NEW
                     */
                    newFiles++;

                    System.out.println(
                            "[RAG][NEW] "
                            + relativeSourcePath
                            + ", vectors="
                            + newRecords.size()
                    );
                }

                /*
                 * VectorStore와 ChunkContextProvider를 동일 시점에 갱신합니다.
                 */
                vectorStore.saveAll(newRecords);
                chunkContextProvider.addChunks(projectId, chunks);

                storedVectors += newRecords.size();
                processedFiles++;
                indexedFiles.add(relativeSourcePath);
            }

            long vectorStoreSize = vectorStore.count();

            ProjectIndexResult result = ProjectIndexResult.builder()
                    .projectId(projectId)
                    .projectName(project.getProjectName())
                    .textDirectory(textDirectory.toString())
                    .embeddingModel(embeddingModel)
                    .discoveredFiles(xhtmlFiles.size())
                    .processedFiles(processedFiles)
                    .newFiles(newFiles)
                    .reindexedFiles(reindexedFiles)
                    .skippedFiles(skippedFiles)
                    .deletedFiles(deletedFiles)
                    .createdChunks(createdChunks)
                    .createdEmbeddings(createdEmbeddings)
                    .storedVectors(storedVectors)
                    .deletedVectors(staleDeletedVectors + replacedVectors)
                    .vectorStoreSize(vectorStoreSize)
                    .indexedFiles(indexedFiles)
                    .skippedFilePaths(skippedFilePaths)
                    .deletedFilePaths(deletedFilePaths)
                    .build();

            notifyProgress(
                    listener,
                    projectId,
                    ProjectIndexProgressStage.COMPLETED,
                    totalFiles,
                    totalFiles,
                    100,
                    null,
                    "RAG 인덱싱이 완료되었습니다."
            );

            return result;

        } catch (Exception exception) {

            notifyProgress(
                    listener,
                    projectId,
                    ProjectIndexProgressStage.FAILED,
                    currentFile,
                    totalFiles,
                    calculatePercent(currentFile, totalFiles),
                    null,
                    "RAG 인덱싱에 실패했습니다: " + safeMessage(exception)
            );

            if (exception instanceof ProjectIndexException projectIndexException) {
                throw projectIndexException;
            }

            throw new ProjectIndexException(
                    "Failed to synchronize EPUB project RAG index: " + safeMessage(exception),
                    exception
            );
        }
    }

    @Override
    public boolean isAvailable() {
        return embeddingClient.isAvailable(RagConstant.DEFAULT_EMBEDDING_MODEL) && vectorStore.isAvailable();
    }
    
    private void notifyProgress(
            ProjectIndexProgressListener listener,
            String projectId,
            ProjectIndexProgressStage stage,
            int current,
            int total,
            int percent,
            String sourcePath,
            String message) {

        if (listener == null) return;

        try {

            listener.onProgress(
                    ProjectIndexProgress.builder()
                            .projectId(projectId)
                            .stage(stage)
                            .current(current)
                            .total(total)
                            .percent(percent)
                            .sourcePath(sourcePath)
                            .message(message)
                            .build()
            );

        } catch (RuntimeException exception) {

            /*
             * 진행 상태 전송 실패가 실제 EPUB RAG 인덱싱을
             * 실패시키지 않도록 Listener 예외는 격리합니다.
             */
            System.err.println(
                    "[RAG][PROGRESS][WARN] Failed to notify progress: "
                    + safeMessage(exception)
            );
        }
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error";

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();

        return message;
    }
    
    private int calculatePercent(int current, int total) {

        if (total <= 0) return 0;
        if (current <= 0) return 0;
        if (current >= total) return 100;

        return (int) Math.round(current * 100.0 / total);
    }

    private String createEmbeddingMessage(
            String sourcePath,
            int currentChunk,
            int totalChunks) {

        if (totalChunks <= 0) return sourcePath + " Embedding을 생성하고 있습니다.";

        return sourcePath
                + " Embedding 생성 중 "
                + currentChunk
                + "/"
                + totalChunks;
    }
    
    private Set<String> createExcludedFiles(ProjectIndexOptions options) {

        Set<String> excludedFiles = new LinkedHashSet<>();

        for (String file : DEFAULT_EXCLUDED_FILES) {
            addExcludedFile(excludedFiles, file);
        }

        if (options != null) {

            for (String file : options.getExcludeFiles()) {
                addExcludedFile(excludedFiles, file);
            }
        }

        return excludedFiles;
    }
    
    private void addExcludedFile(Set<String> excludedFiles, String file) {

        if (excludedFiles == null || file == null || file.isBlank()) {
            return;
        }

        excludedFiles.add(
                GomsStringUtil.normalizePath(file.trim()).toLowerCase(Locale.ROOT)
        );
    }

    private List<Path> findXhtmlFiles(Path textDirectory, Path projectRoot, Set<String> excludedFiles) {

        try (Stream<Path> stream = Files.walk(textDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isXhtmlFile)
                    .filter(path -> !isExcluded(path, projectRoot, excludedFiles))
                    .sorted(
                            Comparator.comparing(
                                    path -> path.toAbsolutePath().normalize().toString()
                            )
                    )
                    .toList();

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to scan TEXT directory: " + textDirectory, exception);
        }
    }
    
    private boolean isExcluded(Path path, Path projectRoot, Set<String> excludedFiles) {

        if (path == null || path.getFileName() == null || excludedFiles == null || excludedFiles.isEmpty()) {
            return false;
        }

        String fileName = path.getFileName().toString().trim().toLowerCase(Locale.ROOT);

        String relativePath = GomsStringUtil.normalizePath(
                projectRoot.relativize(path.toAbsolutePath().normalize()).toString()
        ).toLowerCase(Locale.ROOT);

        return excludedFiles.contains(fileName) || excludedFiles.contains(relativePath);
    }
    
    /**
     * 기존 VectorStore의 DocumentChunk를 Context Expansion Provider에 복원합니다.
     *
     * 애플리케이션 재시작 또는 ContextProvider 재생성 이후에도
     * 기존 VectorStore에 저장된 Chunk를 이용해 Context Expansion이
     * 정상적으로 동작하도록 합니다.
     * @throws VectorStoreException 
     */
    private void synchronizeChunkContext(String projectId, String embeddingModel) throws VectorStoreException {

        List<VectorRecord> records = vectorStore.findByProjectAndModel(projectId, embeddingModel);

        if (records == null || records.isEmpty()) {
            chunkContextProvider.removeProject(projectId);
            return;
        }

        List<DocumentChunk> chunks = new ArrayList<>();

        for (VectorRecord record : records) {

            if (record == null || record.getChunk() == null) {
                continue;
            }

            chunks.add(record.getChunk());
        }

        chunkContextProvider.replaceProjectChunks(projectId, chunks);

        System.out.println("[RAG][CONTEXT] projectId=" + projectId + ", chunks=" + chunks.size());
    }

    private String createProjectId(Path projectRoot) {
        return RagUtil.sha256(
                GomsStringUtil.normalizePath(
                        projectRoot.toAbsolutePath().normalize().toString()
                )
        );
    }

    private String createTextSourcePrefix(Path projectRoot, Path textDirectory) {

        String prefix = GomsStringUtil.normalizePath(
                projectRoot.relativize(textDirectory).toString()
        );

        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        return prefix;
    }

    private Set<String> createCurrentSourcePaths(Path projectRoot, List<Path> xhtmlFiles) {

        Set<String> result = new LinkedHashSet<>();

        for (Path xhtmlFile : xhtmlFiles) {
            Path relativePath = projectRoot.relativize(xhtmlFile);
            result.add(GomsStringUtil.normalizePath(relativePath.toString()));
        }

        return result;
    }

    private boolean isSourceUnchanged(List<VectorRecord> records, String sourceHash) {

        if (records == null || records.isEmpty()) {
            return false;
        }

        if (sourceHash == null || sourceHash.isBlank()) {
            return false;
        }

        for (VectorRecord record : records) {

            if (record == null) {
                return false;
            }

            if (!record.hasSourceHash()) {
                return false;
            }

            if (!sourceHash.equals(record.getSourceHash())) {
                return false;
            }
        }

        return true;
    }

    private void validateTextDirectory(Path textDirectory) {

        if (!Files.exists(textDirectory)) {
            throw new IllegalStateException("TEXT directory does not exist: " + textDirectory);
        }

        if (!Files.isDirectory(textDirectory)) {
            throw new IllegalStateException("TEXT path is not a directory: " + textDirectory);
        }
    }

    private List<Path> findXhtmlFiles(Path textDirectory) {

    	try (Stream<Path> stream = Files.walk(textDirectory)) {
    		return stream
    				.filter(Files::isRegularFile)
    				.filter(this::isXhtmlFile)
    				.filter(path -> !RagUtil.isExcludedDocument(path.toString()))
    				.sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
    				.toList();

    	} catch (Exception exception) {
    		throw new IllegalStateException("Failed to scan TEXT directory: " + textDirectory, exception);
    	}
    }
    
    private boolean isXhtmlFile(Path path) {

        if (path == null || path.getFileName() == null) {
            return false;
        }

        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);

        return fileName.endsWith(".xhtml");
    }
}