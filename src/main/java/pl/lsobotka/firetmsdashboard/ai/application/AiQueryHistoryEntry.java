package pl.lsobotka.firetmsdashboard.ai.application;

import java.time.LocalDateTime;

public record AiQueryHistoryEntry(
        Long id,
        String prompt,
        String generatedSql,
        String visualization,
        String title,
        String explanation,
        Integer rowCount,
        AiQueryHistoryStatus status,
        String sanitizedErrorMessage,
        Long openAiDurationMs,
        Long sqlDurationMs,
        LocalDateTime createdAt) {
}
