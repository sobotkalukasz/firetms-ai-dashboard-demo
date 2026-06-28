package pl.lsobotka.firetmsdashboard.ai.application;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.OpenAiSqlGenerationException;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.OpenAiVisualizationGenerator;
import pl.lsobotka.firetmsdashboard.ai.query.DynamicSqlQueryResult;
import pl.lsobotka.firetmsdashboard.ai.query.DynamicSqlQueryService;
import pl.lsobotka.firetmsdashboard.ai.query.SqlSafetyValidator;
import pl.lsobotka.firetmsdashboard.ai.query.SqlValidationException;

@Service
public class AiDashboardQueryService {

    private final OpenAiVisualizationGenerator queryGenerator;
    private final DynamicSqlQueryService dynamicSqlQueryService;
    private final SqlSafetyValidator sqlSafetyValidator;
    private final AiQueryHistoryService historyService;

    public AiDashboardQueryService(
            OpenAiVisualizationGenerator queryGenerator,
            DynamicSqlQueryService dynamicSqlQueryService,
            SqlSafetyValidator sqlSafetyValidator,
            AiQueryHistoryService historyService) {
        this.queryGenerator = queryGenerator;
        this.dynamicSqlQueryService = dynamicSqlQueryService;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.historyService = historyService;
    }

    @Transactional
    public AiDashboardExecutionResult execute(String apiKey, String prompt) {
        List<String> secrets = List.of(apiKey);
        long openAiDurationMs = 0L;
        long sqlDurationMs = 0L;
        AiQueryGenerationResult latestGeneration = null;
        String latestValidatedSql = null;

        try {
            long openAiStartedAt = System.nanoTime();
            try {
                latestGeneration = queryGenerator.generate(apiKey, prompt);
            } finally {
                openAiDurationMs += elapsedMs(openAiStartedAt);
            }
            latestValidatedSql = sqlSafetyValidator.validateAndNormalize(latestGeneration.sql());

            long sqlStartedAt = System.nanoTime();
            try {
                DynamicSqlQueryResult queryResult = dynamicSqlQueryService.executeValidatedQuery(latestValidatedSql);
                sqlDurationMs += elapsedMs(sqlStartedAt);
                AiDashboardExecutionResult result = new AiDashboardExecutionResult(
                        latestGeneration,
                        latestValidatedSql,
                        queryResult,
                        openAiDurationMs,
                        sqlDurationMs);
                saveSuccess(prompt, result, secrets);
                return result;
            } catch (DataAccessException exception) {
                sqlDurationMs += elapsedMs(sqlStartedAt);
                String correctionFeedback = extractCorrectionFeedback(exception);
                long correctionStartedAt = System.nanoTime();
                try {
                    latestGeneration = queryGenerator.correct(apiKey, prompt, latestValidatedSql, correctionFeedback);
                } finally {
                    openAiDurationMs += elapsedMs(correctionStartedAt);
                }
                latestValidatedSql = sqlSafetyValidator.validateAndNormalize(latestGeneration.sql());

                long correctedSqlStartedAt = System.nanoTime();
                DynamicSqlQueryResult correctedQueryResult;
                try {
                    correctedQueryResult = dynamicSqlQueryService.executeValidatedQuery(latestValidatedSql);
                } finally {
                    sqlDurationMs += elapsedMs(correctedSqlStartedAt);
                }
                AiDashboardExecutionResult result = new AiDashboardExecutionResult(
                        latestGeneration,
                        latestValidatedSql,
                        correctedQueryResult,
                        openAiDurationMs,
                        sqlDurationMs);
                saveSuccess(prompt, result, secrets);
                return result;
            }
        } catch (OpenAiSqlGenerationException exception) {
            saveFailure(prompt, latestGeneration, latestValidatedSql, openAiDurationMs, sqlDurationMs, exception.getMessage(), secrets);
            throw new AiDashboardQueryException(exception.getMessage(), exception);
        } catch (SqlValidationException exception) {
            String message = "Generated SQL was rejected by the local safety validator. " + exception.getMessage();
            saveFailure(prompt, latestGeneration, latestValidatedSql, openAiDurationMs, sqlDurationMs, message, secrets);
            throw new AiDashboardQueryException(message, exception);
        } catch (DataAccessException exception) {
            String message = "The generated SQL could not be executed against the restricted invoice view. Try a simpler request.";
            saveFailure(prompt, latestGeneration, latestValidatedSql, openAiDurationMs, sqlDurationMs, message, secrets);
            throw new AiDashboardQueryException(message, exception);
        } catch (RuntimeException exception) {
            String message = "The AI dashboard request failed before a safe result could be rendered.";
            saveFailure(prompt, latestGeneration, latestValidatedSql, openAiDurationMs, sqlDurationMs, message, secrets);
            throw new AiDashboardQueryException(message, exception);
        }
    }

    @Transactional(readOnly = true)
    public List<AiQueryHistoryEntry> findRecentHistory() {
        return historyService.findRecent();
    }

    @Transactional(readOnly = true)
    public AiDashboardExecutionResult restoreFromHistory(Long historyId) {
        AiQueryHistoryEntry historyEntry = historyService.getById(historyId);
        if (!StringUtils.hasText(historyEntry.generatedSql())) {
            throw new NoSuchElementException("This history entry does not contain reusable SQL.");
        }

        long sqlStartedAt = System.nanoTime();
        DynamicSqlQueryResult queryResult = dynamicSqlQueryService.executeValidatedQuery(historyEntry.generatedSql());
        long sqlDurationMs = elapsedMs(sqlStartedAt);

        AiQueryGenerationResult generation = new AiQueryGenerationResult(
                historyEntry.generatedSql(),
                fallback(historyEntry.title(), "Restored query"),
                fallback(historyEntry.explanation(), "Restored from saved AI query history."),
                historyService.deserializeVisualization(historyEntry.visualization()));

        return new AiDashboardExecutionResult(
                generation,
                sqlSafetyValidator.validateAndNormalize(historyEntry.generatedSql()),
                queryResult,
                historyEntry.openAiDurationMs() == null ? 0L : historyEntry.openAiDurationMs(),
                sqlDurationMs);
    }

    @Transactional
    public void deleteHistory(Long historyId) {
        historyService.deleteById(historyId);
    }

    private void saveSuccess(String prompt, AiDashboardExecutionResult result, List<String> secrets) {
        historyService.save(new AiQueryHistoryWriteRequest(
                prompt,
                result.validatedSql(),
                result.generation().visualizationSpec(),
                result.generation().title(),
                result.generation().explanation(),
                result.queryResult().rows().size(),
                AiQueryHistoryStatus.SUCCESS,
                null,
                result.openAiDurationMs(),
                result.sqlDurationMs()), secrets);
    }

    private void saveFailure(
            String prompt,
            AiQueryGenerationResult generation,
            String validatedSql,
            long openAiDurationMs,
            long sqlDurationMs,
            String errorMessage,
            List<String> secrets) {
        historyService.save(new AiQueryHistoryWriteRequest(
                prompt,
                validatedSql,
                generation == null ? null : generation.visualizationSpec(),
                generation == null ? null : generation.title(),
                generation == null ? null : generation.explanation(),
                null,
                AiQueryHistoryStatus.FAILED,
                historyService.sanitize(errorMessage, secrets),
                openAiDurationMs,
                sqlDurationMs), secrets);
    }

    private String extractCorrectionFeedback(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause != null && StringUtils.hasText(cause.getMessage())) {
            return cause.getMessage();
        }
        return "The SQL could not be executed by H2. Return corrected H2-compatible SQL.";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String fallback(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
