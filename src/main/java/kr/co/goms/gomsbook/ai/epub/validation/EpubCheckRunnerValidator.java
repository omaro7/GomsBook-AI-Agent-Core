/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckMessage;
import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;

/**
 * {@link EpubCheckRunner}를 이용하여 EPUBCheck를 외부 Java Process로 실행하고,
 * 실행 결과를 {@link EpubValidationResult}로 변환하는 Validator입니다.
 */
public final class EpubCheckRunnerValidator implements EpubCheckValidator {

    private static final String VALIDATOR_NAME = "EPUBCheck";

    private final EpubCheckRunner epubCheckRunner;
    private final String epubCheckVersion;

    public EpubCheckRunnerValidator(EpubCheckRunner epubCheckRunner, String epubCheckVersion) {
        this.epubCheckRunner = Objects.requireNonNull(epubCheckRunner, "epubCheckRunner must not be null");
        this.epubCheckVersion = normalizeVersion(epubCheckVersion);
    }

    @Override
    public EpubValidationResult validate(Path projectRoot, Path epubFile, EpubGenerationOptions options) {

        Instant startedAt = Instant.now();

        EpubValidationResult.Builder result = EpubValidationResult.builder()
                .performed(true)
                .validatorName(getName())
                .validatorVersion(getVersion())
                .startedAt(startedAt);

        if (epubFile != null) result.target(epubFile.toAbsolutePath().normalize().toString());

        try {

            validateInput(epubFile, options);

            EpubCheckResult checkResult = epubCheckRunner.run(epubFile);

            for (EpubCheckMessage message : checkResult.getMessages()) result.issue(toValidationIssue(message));

            result.completedAt(Instant.now()).message(checkResult.createSummary());

            return result.build();

        } catch (RuntimeException exception) {

            return result
                    .cause(exception)
                    .completedAt(Instant.now())
                    .message("EPUBCheck validation could not be completed: " + safeMessage(exception))
                    .build();
        }
    }

    @Override
    public boolean isAvailable() {
        return epubCheckRunner != null;
    }

    @Override
    public Availability getAvailability() {
        return isAvailable() ? Availability.available(getVersion()) : Availability.unavailable("EPUBCheck external JAR runner is not available.");
    }

    @Override
    public String getName() {
        return VALIDATOR_NAME;
    }

    @Override
    public String getVersion() {
        return epubCheckVersion;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.EXTERNAL_JAR;
    }

    private EpubValidationIssue toValidationIssue(EpubCheckMessage message) {

        Objects.requireNonNull(message, "EPUBCheck message must not be null");

        Level level = Level.from(message.getSeverity());

        EpubValidationIssue.Builder builder = EpubValidationIssue.builder(
                resolveValidationCode(message.getId()),
                level.toSeverity(),
                message.getMessage())
                .category(EpubValidationIssue.Category.EPUB_CHECK)
                .validator(getName())
                .detail("epubCheckLevel", level.name());

        if (message.getId() != null && !message.getId().isBlank()) builder.detail("epubCheckCode", message.getId());
        message.getLocation().ifPresent(location -> builder.detail("location", location));

        return builder.build();
    }

    private String resolveValidationCode(String code) {

        if (code == null || code.isBlank()) return "EPUBCHECK-UNKNOWN";

        String normalized = code.trim().replaceAll("[^A-Za-z0-9_.-]", "-");

        return "EPUBCHECK-" + normalized;
    }

    private static String normalizeVersion(String value) {

        if (value == null || value.isBlank()) return "unknown";

        String normalized = value.trim();

        if (normalized.toLowerCase(java.util.Locale.ROOT).startsWith("epubcheck-")) normalized = normalized.substring("epubcheck-".length());

        return normalized;
    }

    private static String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown EPUBCheck error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
    }
}