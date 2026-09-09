/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.updater.navigation;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationCleanupResult;


public interface EpubNavigationUpdater {

    void addOrUpdateItem(Path navigationPath, EpubNavigationUpdateItem item);

    void removeItem(Path navigationPath, String href);

    void removeItemIfExists(Path navigationPath, String href);
    
    boolean containsItem(Path navigationPath, String href);

    void update(Path navigationPath, List<EpubNavigationUpdateItem> items);
    
    boolean removeByHrefIfExists(Path navigationPath, String href);

    EpubNavigationCleanupResult cleanup(Path navigationPath);
}