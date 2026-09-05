/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.pkg.updater;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;


public interface EpubPackageUpdater {

    void addOrUpdateManifestItem(Path packagePath, EpubManifestItem resource);

    void removeManifestItem(Path packagePath, String resourceId);

    void addOrUpdateSpineItem(Path packagePath, EpubSpineItem item);

    void removeSpineItem(Path packagePath, String idref);

    boolean containsManifestItem(Path packagePath, String resourceId);

    boolean containsSpineItem(Path packagePath, String idref);

    void update(Path packagePath, List<EpubManifestItem> resources, List<EpubSpineItem> spineItems);
}