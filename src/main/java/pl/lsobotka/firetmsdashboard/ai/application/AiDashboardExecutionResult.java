package pl.lsobotka.firetmsdashboard.ai.application;

import pl.lsobotka.firetmsdashboard.ai.query.DynamicSqlQueryResult;

public record AiDashboardExecutionResult(
        AiQueryGenerationResult generation,
        String validatedSql,
        DynamicSqlQueryResult queryResult,
        long openAiDurationMs,
        long sqlDurationMs) {
}
