/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * EPUBCheck 실행 결과를 표현합니다.
 *
 * <p>EPUBCheck가 반환한 개별 메시지를 보관하고,
 * severity별 개수와 최종 valid 여부를 제공합니다.</p>
 */
public final class EpubCheckResult {


    private final String target;

    private final String epubCheckVersion;

    private final List<EpubCheckMessage> messages;


    public EpubCheckResult(
            String target,
            String epubCheckVersion,
            List<EpubCheckMessage> messages) {

        this.target = trimToNull(target);

        this.epubCheckVersion = trimToNull(epubCheckVersion);

        this.messages = immutableMessages(messages);
    }


    public String getTarget() {

        return target;
    }


    public String getEpubCheckVersion() {

        return epubCheckVersion;
    }


    public List<EpubCheckMessage> getMessages() {

        return messages;
    }


    public int getMessageCount() {

        return messages.size();
    }


    public int getFatalCount() {

        int count = 0;

        for (EpubCheckMessage message : messages) {

            if (message.isFatal()) {

                count++;
            }
        }

        return count;
    }


    public int getErrorCount() {

        int count = 0;

        for (EpubCheckMessage message : messages) {

            if (message.isError()) {

                count++;
            }
        }

        return count;
    }


    public int getWarningCount() {

        int count = 0;

        for (EpubCheckMessage message : messages) {

            if (message.isWarning()) {

                count++;
            }
        }

        return count;
    }


    public int getUsageCount() {

        int count = 0;

        for (EpubCheckMessage message : messages) {

            if (message.isUsage()) {

                count++;
            }
        }

        return count;
    }


    public int getInfoCount() {

        int count = 0;

        for (EpubCheckMessage message : messages) {

            if (message.isInfo()) {

                count++;
            }
        }

        return count;
    }


    public boolean isValid() {

        return getFatalCount() == 0 && getErrorCount() == 0;
    }


    public boolean hasMessages() {

        return !messages.isEmpty();
    }


    public boolean hasFatalErrors() {

        return getFatalCount() > 0;
    }


    public boolean hasErrors() {

        return getErrorCount() > 0;
    }


    public boolean hasWarnings() {

        return getWarningCount() > 0;
    }


    public boolean hasUsageMessages() {

        return getUsageCount() > 0;
    }


    public String createSummary() {

        return "EPUBCheck validation: "
                + (isValid() ? "VALID" : "INVALID")
                + ", fatal=" + getFatalCount()
                + ", errors=" + getErrorCount()
                + ", warnings=" + getWarningCount()
                + ", usage=" + getUsageCount()
                + ", info=" + getInfoCount();
    }


    @Override
    public String toString() {

        return "EpubCheckResult{"
                + "target='" + target + '\''
                + ", epubCheckVersion='" + epubCheckVersion + '\''
                + ", valid=" + isValid()
                + ", fatalCount=" + getFatalCount()
                + ", errorCount=" + getErrorCount()
                + ", warningCount=" + getWarningCount()
                + ", usageCount=" + getUsageCount()
                + ", infoCount=" + getInfoCount()
                + ", messageCount=" + getMessageCount()
                + '}';
    }


    private static List<EpubCheckMessage> immutableMessages(
            List<EpubCheckMessage> messages) {

        if (messages == null || messages.isEmpty()) {

            return Collections.emptyList();
        }

        List<EpubCheckMessage> result = new ArrayList<>();

        for (EpubCheckMessage message : messages) {

            if (message != null) {

                result.add(message);
            }
        }

        return Collections.unmodifiableList(result);
    }


    private static String trimToNull(
            String value) {

        if (value == null) {

            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}