/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.navigation.updater;

/**
 * EPUB Navigation 항목의 삽입 위치를 정의합니다.
 */
public enum EpubNavigationInsertPosition {

    /**
     * TOC의 첫 번째 위치에 삽입합니다.
     */
    FIRST,

    /**
     * TOC의 마지막 위치에 삽입합니다.
     */
    LAST,

    /**
     * 기준 항목 앞에 삽입합니다.
     */
    BEFORE,

    /**
     * 기준 항목 뒤에 삽입합니다.
     */
    AFTER
}