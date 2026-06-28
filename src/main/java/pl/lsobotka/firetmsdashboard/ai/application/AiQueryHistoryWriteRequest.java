package pl.lsobotka.firetmsdashboard.ai.application;

import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec;

public record AiQueryHistoryWriteRequest(
        String prompt,
        String generatedSql,
        AiVisualizationSpec visualizationSpec,
        String title,
        String explanation,
        Integer rowCount,
        AiQueryHistoryStatus status,
        String sanitizedErrorMessage,
        Long openAiDurationMs,
        Long sqlDurationMs) {
}
