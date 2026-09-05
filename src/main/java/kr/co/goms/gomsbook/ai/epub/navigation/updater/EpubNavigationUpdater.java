/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.navigation.updater;

import java.nio.file.Path;
import java.util.List;


public interface EpubNavigationUpdater {

    void addOrUpdateItem(Path navigationPath, EpubNavigationUpdateItem item);

    void removeItem(Path navigationPath, String href);

    boolean containsItem(Path navigationPath, String href);

    void update(Path navigationPath, List<EpubNavigationUpdateItem> items);
}