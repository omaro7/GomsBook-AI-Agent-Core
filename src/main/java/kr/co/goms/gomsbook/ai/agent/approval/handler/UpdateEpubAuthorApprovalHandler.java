/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
 package kr.co.goms.gomsbook.ai.agent.approval.handler;

 import java.nio.file.Files;
 import java.nio.file.Path;

 import com.google.gson.Gson;

 import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
 import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
 import kr.co.goms.gomsbook.ai.agent.approval.payload.EpubAuthorApprovalPayload;
 import kr.co.goms.gomsbook.ai.epub.generation.author.DefaultEpubAuthorXhtmlGenerator;
 import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorPage;
 import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorXhtmlGenerator;
 import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
 import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
 
 /** 
 * 수정 Handler에서는 content.opf, spine, nav를 건드리지 않습니다. Update는 기존 author 페이지의 내용만 바꾸는 작업이므로, 
 * EpubAuthorPageState가 이미 정상임을 Tool 단계에서 확인한 뒤 승인 Handler는 author.xhtml만 덮어쓰는 것이 맞습니다.
 * 
 */
 public class UpdateEpubAuthorApprovalHandler implements AgentApprovalHandler {

     private final CurrentProjectProvider currentProjectProvider;
     private final EpubAuthorXhtmlGenerator xhtmlGenerator;
     private final Gson gson;

     public UpdateEpubAuthorApprovalHandler(CurrentProjectProvider currentProjectProvider) {
         this(currentProjectProvider, new DefaultEpubAuthorXhtmlGenerator(), new Gson());
     }

     public UpdateEpubAuthorApprovalHandler(
             CurrentProjectProvider currentProjectProvider,
             EpubAuthorXhtmlGenerator xhtmlGenerator,
             Gson gson) {

         if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
         if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");
         if (gson == null) throw new IllegalArgumentException("gson must not be null.");

         this.currentProjectProvider = currentProjectProvider;
         this.xhtmlGenerator = xhtmlGenerator;
         this.gson = gson;
     }

     @Override
     public void execute(AgentApproval approval) {

         if (approval == null) throw new IllegalArgumentException("approval must not be null.");

         EpubProjectContext project = requireCurrentProject();
         EpubAuthorApprovalPayload payload = parsePayload(approval);
         EpubAuthorPage page = toPage(payload);

         validatePage(page);

         Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
         Path authorFile = textDirectory.resolve(page.getFileName()).normalize();

         validateTargetPath(textDirectory, authorFile);

         xhtmlGenerator.generate(page, textDirectory);
     }

     private EpubProjectContext requireCurrentProject() {

         EpubProjectContext project = currentProjectProvider.getCurrentProject();

         if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
         if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
         if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

         return project;
     }

     private EpubAuthorApprovalPayload parsePayload(AgentApproval approval) {

         String content = approval.getContent();

         if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is empty.");

         try {

             EpubAuthorApprovalPayload payload = gson.fromJson(content, EpubAuthorApprovalPayload.class);

             if (payload == null) throw new IllegalStateException("Approval payload is empty.");

             return payload;

         } catch (RuntimeException exception) {
             throw new IllegalStateException("Failed to parse EPUB author approval payload.", exception);
         }
     }

     private EpubAuthorPage toPage(EpubAuthorApprovalPayload payload) {

         EpubAuthorPage page = new EpubAuthorPage();

         page.setFileName(payload.getFileName());
         page.setAuthorName(payload.getAuthorName());
         page.setIntroduction(payload.getIntroduction());
         page.setProfile(payload.getProfile());
         page.setCareers(payload.getCareers());
         page.setImageFileName(payload.getImageFileName());
         page.setImageAlt(payload.getImageAlt());

         return page;
     }

     private void validatePage(EpubAuthorPage page) {

         if (page == null) throw new IllegalArgumentException("EpubAuthorPage must not be null.");
         if (isBlank(page.getFileName())) throw new IllegalArgumentException("fileName must not be blank.");
         if (isBlank(page.getAuthorName())) throw new IllegalArgumentException("authorName must not be blank.");
     }

     private void validateTargetPath(
             Path textDirectory,
             Path authorFile) {

         if (!authorFile.startsWith(textDirectory)) {
             throw new IllegalStateException("Author XHTML must be inside the EPUB Text directory.");
         }

         if (!Files.exists(authorFile)) {
             throw new IllegalStateException("Author XHTML does not exist: " + authorFile);
         }

         if (!Files.isRegularFile(authorFile)) {
             throw new IllegalStateException("Author XHTML path is not a file: " + authorFile);
         }
     }

     private boolean isBlank(String value) {
         return value == null || value.trim().isEmpty();
     }
 }