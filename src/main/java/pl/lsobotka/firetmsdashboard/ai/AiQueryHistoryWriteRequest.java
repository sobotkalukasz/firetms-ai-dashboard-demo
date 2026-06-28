package pl.lsobotka.firetmsdashboard.ai;

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
