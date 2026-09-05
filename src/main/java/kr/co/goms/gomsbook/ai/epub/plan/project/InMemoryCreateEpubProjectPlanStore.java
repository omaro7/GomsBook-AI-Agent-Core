/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.epub.plan.project;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메모리 기반 신규 EPUB 프로젝트 생성 Plan 저장소.
 *
 * <p>
 * 애플리케이션 실행 중에만 프로젝트 생성 Plan을 유지한다.
 * 서버가 재시작되면 저장된 Plan 정보는 사라진다.
 * </p>
 */
public final class InMemoryCreateEpubProjectPlanStore
        implements CreateEpubProjectPlanStore {

    private final Map<String, CreateEpubProjectPlan> plans =
            new ConcurrentHashMap<>();


    @Override
    public void save(
            CreateEpubProjectPlan plan) {

        if (plan == null) {

            throw new IllegalArgumentException(
                    "plan must not be null.");
        }

        String planId =
                requirePlanId(
                        plan.getPlanId());

        plans.put(
                planId,
                plan);
    }


    @Override
    public Optional<CreateEpubProjectPlan> findById(
            String planId) {

        String normalizedPlanId =
                requirePlanId(
                        planId);

        return Optional.ofNullable(
                plans.get(
                        normalizedPlanId));
    }


    @Override
    public boolean exists(
            String planId) {

        String normalizedPlanId =
                requirePlanId(
                        planId);

        return plans.containsKey(
                normalizedPlanId);
    }


    @Override
    public void delete(
            String planId) {

        String normalizedPlanId =
                requirePlanId(
                        planId);

        plans.remove(
                normalizedPlanId);
    }


    private String requirePlanId(
            String planId) {

        if (planId == null
                || planId.isBlank()) {

            throw new IllegalArgumentException(
                    "planId must not be blank.");
        }

        return planId.trim();
    }
}