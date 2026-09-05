/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.conversation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.goms.gomsbook.ai.conversation.model.AiConversation;
import kr.co.goms.gomsbook.ai.conversation.model.AiConversationMessage;
import kr.co.goms.gomsbook.ai.conversation.model.AiConversationMessageRole;
import kr.co.goms.gomsbook.ai.conversation.model.ConversationHistoryMessage;
import kr.co.goms.gomsbook.ai.conversation.repository.AiConversationMessageRepository;
import kr.co.goms.gomsbook.ai.conversation.repository.AiConversationRepository;

@Service
public class ConversationService {

    private final AiConversationRepository conversationRepository;

    private final AiConversationMessageRepository messageRepository;

    public ConversationService(
            AiConversationRepository conversationRepository,
            AiConversationMessageRepository messageRepository) {

        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public AiConversation getOrCreateConversation(
            String projectId,
            String conversationId) {

        validateProjectId(projectId);
        validateConversationId(conversationId);

        AiConversation conversation =
                conversationRepository
                        .findById(conversationId)
                        .orElse(null);

        if (conversation != null) {

            validateConversationProject(
                    conversation,
                    projectId
            );

            conversation.setUpdatedAt(
                    LocalDateTime.now()
            );

            conversationRepository.update(
                    conversation
            );

            return conversation;
        }

        LocalDateTime now =
                LocalDateTime.now();

        AiConversation newConversation =
                new AiConversation(
                        conversationId,
                        projectId,
                        null,
                        "ACTIVE",
                        now,
                        now
                );

        conversationRepository.save(
                newConversation
        );

        return newConversation;
    }

    @Transactional
    public AiConversationMessage addUserMessage(
            String conversationId,
            String runId,
            String content) {

        return addMessage(
                conversationId,
                runId,
                AiConversationMessageRole.USER,
                content
        );
    }

    @Transactional
    public AiConversationMessage addAssistantMessage(
            String conversationId,
            String runId,
            String content) {

        return addMessage(
                conversationId,
                runId,
                AiConversationMessageRole.ASSISTANT,
                content
        );
    }

    @Transactional
    public AiConversationMessage addSystemMessage(
            String conversationId,
            String runId,
            String content) {

        return addMessage(
                conversationId,
                runId,
                AiConversationMessageRole.SYSTEM,
                content
        );
    }

    @Transactional
    public AiConversationMessage addToolMessage(
            String conversationId,
            String runId,
            String content) {

        return addMessage(
                conversationId,
                runId,
                AiConversationMessageRole.TOOL,
                content
        );
    }

    public List<ConversationHistoryMessage> getConversationHistory(
            String conversationId,
            String currentRunId) {

        validateConversationId(conversationId);

        return messageRepository
                .findByConversationId(conversationId)
                .stream()
                .filter(message -> !isCurrentRunMessage(message, currentRunId))
                .filter(this::isLlmHistoryMessage)
                .map(message -> new ConversationHistoryMessage(
                        message.getRole(),
                        message.getContent()
                ))
                .toList();
    }

    private boolean isCurrentRunMessage(
            AiConversationMessage message,
            String currentRunId) {

        if (currentRunId == null || currentRunId.isBlank()) return false;

        return currentRunId.equals(message.getRunId());
    }

    private boolean isLlmHistoryMessage(
            AiConversationMessage message) {

        return message.getRole() == AiConversationMessageRole.USER
                || message.getRole() == AiConversationMessageRole.ASSISTANT;
    }
    
    private AiConversationMessage addMessage(
            String conversationId,
            String runId,
            AiConversationMessageRole role,
            String content) {

        validateConversationId(
                conversationId
        );

        validateContent(
                content
        );

        long sequenceNo =
                messageRepository
                        .findNextSequenceNo(
                                conversationId
                        );

        AiConversationMessage message =
                new AiConversationMessage(
                        UUID.randomUUID().toString(),
                        conversationId,
                        normalizeRunId(runId),
                        role,
                        content.trim(),
                        sequenceNo,
                        LocalDateTime.now()
                );

        messageRepository.save(
                message
        );

        touchConversation(
                conversationId
        );

        return message;
    }

    private void touchConversation(
            String conversationId) {

        AiConversation conversation =
                conversationRepository
                        .findById(
                                conversationId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Conversation not found: "
                                                + conversationId
                                )
                        );

        conversation.setUpdatedAt(
                LocalDateTime.now()
        );

        conversationRepository.update(
                conversation
        );
    }

    private void validateConversationProject(
            AiConversation conversation,
            String projectId) {

        if (
                conversation
                        .getProjectId()
                        .equals(projectId)
        ) {
            return;
        }

        throw new IllegalArgumentException(
                "Conversation project does not match."
                        + " conversationId="
                        + conversation.getConversationId()
                        + ", conversationProjectId="
                        + conversation.getProjectId()
                        + ", requestProjectId="
                        + projectId
        );
    }

    private void validateProjectId(
            String projectId) {

        if (
                projectId == null
                        || projectId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "projectId must not be blank."
            );
        }
    }

    private void validateConversationId(
            String conversationId) {

        if (
                conversationId == null
                        || conversationId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "conversationId must not be blank."
            );
        }
    }

    private void validateContent(
            String content) {

        if (
                content == null
                        || content.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "message content must not be blank."
            );
        }
    }

    private String normalizeRunId(
            String runId) {

        if (
                runId == null
                        || runId.isBlank()
        ) {
            return null;
        }

        return runId.trim();
    }
}