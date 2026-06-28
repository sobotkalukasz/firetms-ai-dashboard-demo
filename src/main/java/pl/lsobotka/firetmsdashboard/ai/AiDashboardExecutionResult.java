package pl.lsobotka.firetmsdashboard.ai;

public record AiDashboardExecutionResult(
        AiQueryGenerationResult generation,
        String validatedSql,
        DynamicSqlQueryResult queryResult,
        long openAiDurationMs,
        long sqlDurationMs) {
}
