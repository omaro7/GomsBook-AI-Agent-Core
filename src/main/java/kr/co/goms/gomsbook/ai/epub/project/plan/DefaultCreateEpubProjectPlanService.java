/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.epub.project.plan;

/**
 * 신규 EPUB 프로젝트 생성 Plan Service 기본 구현체.
 *
 * <p>
 * CreateEpubProjectPlan의 생성, 조회, 수정,
 * 승인 및 상태 변경을 담당한다.
 * </p>
 *
 * <p>
 * 변경된 Plan은 CreateEpubProjectPlanStore에 저장한다.
 * 실제 프로젝트 폴더 및 EPUB 파일 생성은 담당하지 않는다.
 * </p>
 */
public final class DefaultCreateEpubProjectPlanService
        implements CreateEpubProjectPlanService {

    private final CreateEpubProjectPlanStore planStore;


    public DefaultCreateEpubProjectPlanService(
            CreateEpubProjectPlanStore planStore) {

        if (planStore == null) {

            throw new IllegalArgumentException(
                    "planStore must not be null.");
        }

        this.planStore =
                planStore;
    }


    @Override
    public CreateEpubProjectPlan create(
            String projectName,
            String folderName) {

        CreateEpubProjectPlan plan =
                new CreateEpubProjectPlan(
                        projectName,
                        folderName);

        planStore.save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan get(
            String planId) {

        return requirePlan(
                planId);
    }


    @Override
    public CreateEpubProjectPlan changeProjectName(
            String planId,
            String projectName) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.changeProjectName(
                projectName);

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan changeFolderName(
            String planId,
            String folderName) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.changeFolderName(
                folderName);

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan changeProject(
            String planId,
            String projectName,
            String folderName) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.changeProject(
                projectName,
                folderName);

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan approve(
            String planId) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.approve();

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan markCreating(
            String planId) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.markCreating();

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan markCreated(
            String planId) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.markCreated();

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan complete(
            String planId) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.complete();

        save(
                plan);

        return plan;
    }


    @Override
    public CreateEpubProjectPlan cancel(
            String planId) {

        CreateEpubProjectPlan plan =
                requirePlan(
                        planId);

        plan.cancel();

        save(
                plan);

        return plan;
    }


    private CreateEpubProjectPlan requirePlan(
            String planId) {

        String normalizedPlanId =
                requirePlanId(
                        planId);

        return planStore
                .findById(
                        normalizedPlanId)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "EPUB project creation plan was not found: "
                                        + normalizedPlanId));
    }


    private void save(
            CreateEpubProjectPlan plan) {

        planStore.save(
                plan);
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