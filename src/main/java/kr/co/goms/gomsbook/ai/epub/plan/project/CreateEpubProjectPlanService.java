/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.epub.plan.project;

/**
 * 신규 EPUB 프로젝트 생성 Plan Service.
 *
 * <p>
 * 프로젝트 생성 Plan의 생성, 조회, 수정,
 * 승인 및 상태 변경을 담당한다.
 * </p>
 *
 * <p>
 * 실제 프로젝트 폴더나 EPUB 파일 생성은 담당하지 않는다.
 * 실제 파일 시스템 작업은 Tool 계층에서 수행한다.
 * </p>
 */
public interface CreateEpubProjectPlanService {

    CreateEpubProjectPlan create(
            String projectName,
            String folderName);


    CreateEpubProjectPlan get(
            String planId);


    CreateEpubProjectPlan changeProjectName(
            String planId,
            String projectName);


    CreateEpubProjectPlan changeFolderName(
            String planId,
            String folderName);


    CreateEpubProjectPlan changeProject(
            String planId,
            String projectName,
            String folderName);


    CreateEpubProjectPlan approve(
            String planId);


    CreateEpubProjectPlan markCreating(
            String planId);


    CreateEpubProjectPlan markCreated(
            String planId);


    CreateEpubProjectPlan complete(
            String planId);


    CreateEpubProjectPlan cancel(
            String planId);
}