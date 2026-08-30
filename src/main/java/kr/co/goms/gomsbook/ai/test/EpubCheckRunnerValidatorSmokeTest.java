package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.model.EpubGenerationOptions;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;
import kr.co.goms.gomsbook.ai.epub.validation.EpubCheckRunnerValidator;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationIssue;
import kr.co.goms.gomsbook.ai.epub.validation.EpubValidationResult;

public final class EpubCheckRunnerValidatorSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] EpubCheckRunnerValidator smoke test start");

        Path epubCheckDirectory = Path.of("D:/14.EPub/lib/epubcheck-5.3.0");
        Path projectRoot = Path.of("C:/1004.GomsBook/03.Project/lunchwork_seoul");
        Path epubFile = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul/lunchwork_seoul-202608163712.epub");

        EpubCheckRunner runner = new EpubCheckRunner(epubCheckDirectory, "5.3.0");
        EpubCheckRunnerValidator validator = new EpubCheckRunnerValidator(runner, "5.3.0");
        EpubGenerationOptions options = EpubGenerationOptions.defaultOptions();

        EpubValidationResult result = validator.validate(projectRoot, epubFile, options);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Validator Name = " + result.getValidatorName());
        System.out.println("[GomsBook AI Core] Validator Version = " + result.getValidatorVersion());
        System.out.println("[GomsBook AI Core] Status = " + result.getStatus());
        System.out.println("[GomsBook AI Core] Performed = " + result.isPerformed());
        System.out.println("[GomsBook AI Core] Fatal Count = " + result.getFatalCount());
        System.out.println("[GomsBook AI Core] Error Count = " + result.getErrorCount());
        System.out.println("[GomsBook AI Core] Warning Count = " + result.getWarningCount());
        System.out.println("[GomsBook AI Core] Info Count = " + result.getInfoCount());
        System.out.println("[GomsBook AI Core] Issue Count = " + result.getIssueCount());
        System.out.println("[GomsBook AI Core] Message = " + result.getMessage().orElse(""));
        System.out.println("[GomsBook AI Core] -------------------------");

        for (EpubValidationIssue issue : result.getIssues()) System.out.println("[GomsBook AI Core] " + issue.getSeverity() + " | code=" + issue.getCode() + " | message=" + issue.getMessage());

        if (!result.isPerformed()) throw new IllegalStateException("EPUBCheck validation was not performed.");
        if (result.getValidatorName() == null || result.getValidatorName().isEmpty()) throw new IllegalStateException("Validator name is missing.");
        if (result.getValidatorVersion() == null || result.getValidatorVersion().isEmpty()) throw new IllegalStateException("Validator version is missing.");
        if (result.getIssueCount() < 0) throw new IllegalStateException("Validation issue count is invalid.");

        System.out.println("[GomsBook AI Core] EpubCheckRunnerValidatorSmokeTest success");
    }

    private EpubCheckRunnerValidatorSmokeTest() {
    }
}