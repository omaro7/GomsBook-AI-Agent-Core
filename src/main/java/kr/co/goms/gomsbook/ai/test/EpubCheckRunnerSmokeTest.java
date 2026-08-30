package kr.co.goms.gomsbook.ai.test;

import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckMessage;
import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;
import kr.co.goms.gomsbook.ai.epub.service.EpubCheckRunner;

public final class EpubCheckRunnerSmokeTest {

    public static void main(String[] args) {

        System.out.println("[GomsBook AI Core] EpubCheckRunner smoke test start");

        Path epubCheckDirectory = Path.of("D:/14.EPub/lib/epubcheck-5.3.0");
        Path epubFile = Path.of("C:/1004.GomsBook/02.Publish/lunchwork_seoul/lunchwork_seoul-202608163712.epub");

        EpubCheckRunner runner = new EpubCheckRunner(epubCheckDirectory, "5.3.0");
        EpubCheckResult result = runner.run(epubFile);

        System.out.println("[GomsBook AI Core] -------------------------");
        System.out.println("[GomsBook AI Core] Target = " + result.getTarget());
        System.out.println("[GomsBook AI Core] EPUBCheck Version = " + result.getEpubCheckVersion());
        System.out.println("[GomsBook AI Core] Valid = " + result.isValid());
        System.out.println("[GomsBook AI Core] Fatal Count = " + result.getFatalCount());
        System.out.println("[GomsBook AI Core] Error Count = " + result.getErrorCount());
        System.out.println("[GomsBook AI Core] Warning Count = " + result.getWarningCount());
        System.out.println("[GomsBook AI Core] Usage Count = " + result.getUsageCount());
        System.out.println("[GomsBook AI Core] Info Count = " + result.getInfoCount());
        System.out.println("[GomsBook AI Core] Message Count = " + result.getMessageCount());
        System.out.println("[GomsBook AI Core] Summary = " + result.createSummary());
        System.out.println("[GomsBook AI Core] -------------------------");

        for (EpubCheckMessage message : result.getMessages()) {
            System.out.println(
                    "[GomsBook AI Core] "
                            + message.getSeverity()
                            + " | id=" + message.getId()
                            + " | location=" + message.getLocation().orElse("")
                            + " | message=" + message.getMessage());
        }

        if (result.getTarget() == null || result.getTarget().isBlank()) throw new IllegalStateException("EPUBCheck target is missing.");
        if (result.getEpubCheckVersion() == null) throw new IllegalStateException("EPUBCheck version is missing.");
        if (result.getMessageCount() < 0) throw new IllegalStateException("EPUBCheck message count is invalid.");

        System.out.println("[GomsBook AI Core] EpubCheckRunnerSmokeTest success");
    }

    private EpubCheckRunnerSmokeTest() {
    }
}