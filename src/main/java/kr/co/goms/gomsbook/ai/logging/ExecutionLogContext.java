package kr.co.goms.gomsbook.ai.logging;

import java.time.Instant;

public record ExecutionLogContext(
        long logId,
        Instant startedAt) {
}