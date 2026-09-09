/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.updater.pkg;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadataItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;

/**
 * EPUB package document(content.opf)의 metadata / manifest / spine 갱신 기능을 정의합니다.
 */
public interface EpubPackageUpdater {

    void addManifestItem(Path packagePath, EpubManifestItem resource);

    void addOrUpdateManifestItem(Path packagePath, EpubManifestItem resource);

    void removeManifestItem(Path packagePath, String resourceId);

    void removeByHrefIfExists(Path packagePath, String href);

    void addMetadata(Path packagePath, EpubMetadataItem item);

    boolean updateMetadata(
            Path packagePath,
            String name,
            String targetValue,
            String id,
            String property,
            String refines,
            String scheme,
            EpubMetadataItem replacement);

    boolean removeMetadataIfExists(
            Path packagePath,
            String name,
            String targetValue,
            String id,
            String property,
            String refines,
            String scheme);

    void addOrUpdateSpineItem(Path packagePath, EpubSpineItem item);

    void removeSpineItem(Path packagePath, String idref);

    void addSpineItemref(Path packagePath, String idref, int targetIndex);

    boolean removeSpineItemrefIfExists(Path packagePath, String idref);

    void moveSpineItemref(Path packagePath, String idref, int targetIndex);

    boolean containsManifestItem(Path packagePath, String resourceId);

    boolean containsSpineItem(Path packagePath, String idref);

    void update(Path packagePath, List<EpubManifestItem> resources, List<EpubSpineItem> spineItems);
}