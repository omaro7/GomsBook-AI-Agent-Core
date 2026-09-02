/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.epub.project.plan;

import java.util.Optional;

/**
 * 신규 EPUB 프로젝트 생성 Plan 저장소.
 *
 * <p>
 * Plan의 저장, 조회, 존재 여부 확인, 삭제만 담당한다.
 * Plan 상태 변경 로직은 담당하지 않는다.
 * </p>
 */
public interface CreateEpubProjectPlanStore {

    void save(
            CreateEpubProjectPlan plan);


    Optional<CreateEpubProjectPlan> findById(
            String planId);


    boolean exists(
            String planId);


    void delete(
            String planId);
}