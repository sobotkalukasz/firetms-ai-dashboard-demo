package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ai.application.AiDashboardExecutionResult;
import pl.lsobotka.firetmsdashboard.ai.application.AiDashboardQueryException;
import pl.lsobotka.firetmsdashboard.ai.application.AiDashboardQueryService;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryGenerationResult;
import pl.lsobotka.firetmsdashboard.ai.application.AiQueryHistoryEntry;
import pl.lsobotka.firetmsdashboard.ai.application.AiVisualizationValidationException;
import pl.lsobotka.firetmsdashboard.ai.application.AiVisualizationValidator;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec;
import pl.lsobotka.firetmsdashboard.ai.query.DynamicSqlQueryResult;
import pl.lsobotka.firetmsdashboard.ai.query.SqlSafetyValidator;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final String OPENAI_API_KEY_REQUIRED_MESSAGE = "OpenAI API key is required for this AI experiment.";
    private static final String PROMPT_REQUIRED_MESSAGE = "Prompt is required for this AI experiment.";
    private static final String EMPTY_RESULTS_MESSAGE = "The generated query ran successfully but returned no rows.";
    private static final String CHART_VALIDATION_PREFIX = "Visualization was skipped: ";
    private static final DateTimeFormatter HISTORY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PasswordField openAiApiKeyField = new PasswordField("OpenAI API key");
    private final TextArea promptField = new TextArea("Prompt");
    private final Button generateButton = new Button("Generate");
    private final ProgressBar progressBar = new ProgressBar();
    private final Div status = new Div();
    private final H3 resultTitle = new H3();
    private final Paragraph resultExplanation = new Paragraph();
    private final HorizontalLayout metadataBar = new HorizontalLayout();
    private final Span rowCountBadge = createMetricBadge();
    private final Span openAiDurationBadge = createMetricBadge();
    private final Span sqlDurationBadge = createMetricBadge();
    private final Div visualizationMessage = new Div();
    private final Div chartContainer = new Div();
    private final Grid<Map<String, Object>> resultGrid = new Grid<>();
    private final TextArea generatedSqlArea = new TextArea();
    private final Details generatedSqlDetails = new Details("Generated SQL", generatedSqlArea);
    private final Grid<AiQueryHistoryEntry> historyGrid = new Grid<>(AiQueryHistoryEntry.class, false);
    private final AiDashboardQueryService aiDashboardQueryService;
    private final AiVisualizationValidator visualizationValidator;
    private final AiVisualizationChartFactory visualizationChartFactory;
    private final ExecutorService executorService;

    public AiDashboardExperimentView(
            AiDashboardQueryService aiDashboardQueryService,
            SqlSafetyValidator sqlSafetyValidator,
            AiVisualizationValidator visualizationValidator,
            AiVisualizationChartFactory visualizationChartFactory,
            ExecutorService aiDashboardExecutor) {
        this.aiDashboardQueryService = aiDashboardQueryService;
        this.visualizationValidator = visualizationValidator;
        this.visualizationChartFactory = visualizationChartFactory;
        this.executorService = aiDashboardExecutor;
        setPadding(true);
        setSpacing(true);
        setWidthFull();
        addClassName("ai-dashboard-view");

        H2 heading = new H2("AI Dashboard Experiment");
        heading.addClassName("page-hero-title");
        Paragraph description = new Paragraph(
                "This page uses OpenAI to generate SQL and a visualization spec against a restricted invoice view. "
                        + "Only the prompt, safe schema, and SQL rules are sent to OpenAI. Invoice rows stay local, and "
                        + "history stores only sanitized metadata.");
        description.addClassName("page-hero-description");

        configureOpenAiApiKeyField();
        configurePromptField();
        configureProgressBar();
        configureGrid();
        configureChartContainer();
        configureVisualizationMessage();
        configureGeneratedSqlArea();
        configureResultSummary();
        configureMetadataBar();
        configureHistoryGrid();

        generateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generateButton.setDisableOnClick(true);
        generateButton.addClickListener(event -> generateDashboard(promptField.getValue()));

        HorizontalLayout actionBar = new HorizontalLayout(generateButton, progressBar);
        actionBar.setAlignItems(Alignment.CENTER);
        actionBar.setWidthFull();
        actionBar.expand(progressBar);
        actionBar.addClassName("query-action-bar");

        Details restrictedSchemaDetails = new Details("Restricted AI schema", new Text(sqlSafetyValidator.schemaDescription()));
        restrictedSchemaDetails.setWidthFull();

        VerticalLayout heroCard = createSurfaceCard("surface-card", "surface-card-hero");
        heroCard.add(heading, description, createPromptExamples());

        VerticalLayout queryCard = createSurfaceCard("surface-card", "query-form-card");
        queryCard.add(openAiApiKeyField, promptField, actionBar, status);

        VerticalLayout resultSummaryCard = createSurfaceCard("surface-card", "result-summary-card");
        resultSummaryCard.add(resultTitle, resultExplanation, metadataBar, visualizationMessage);

        add(
                heroCard,
                queryCard,
                resultSummaryCard,
                createSection("Visualization", chartContainer),
                createSection("Result Grid", resultGrid),
                createSurfaceCard(generatedSqlDetails, "surface-card", "surface-card-section"),
                createSection("Recent Query History", historyGrid),
                createSurfaceCard(restrictedSchemaDetails, "surface-card", "surface-card-section"));

        showInitialStatus();
        refreshHistory();
    }

    private void configureOpenAiApiKeyField() {
        openAiApiKeyField.setWidthFull();
        openAiApiKeyField.setRevealButtonVisible(false);
        openAiApiKeyField.setHelperText("Used only for AI dashboard requests in the current UI session. It is not persisted.");
        openAiApiKeyField.setErrorMessage(OPENAI_API_KEY_REQUIRED_MESSAGE);
    }

    private void configurePromptField() {
        promptField.setWidthFull();
        promptField.setMinHeight("180px");
        promptField.setPlaceholder("""
                Show the latest 20 invoices with the highest gross amount
                Show invoice count by status for the last 90 days
                Show monthly gross amount totals by currency
                """);
        promptField.setHelperText("Only the prompt, safe schema, SQL rules, and JSON output schema are sent to OpenAI. "
                + "Invoice data stays local.");
        promptField.setErrorMessage(PROMPT_REQUIRED_MESSAGE);
    }

    private void configureProgressBar() {
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidthFull();
    }

    private void configureGrid() {
        resultGrid.setWidthFull();
        resultGrid.setMinHeight("20rem");
        resultGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        resultGrid.setEmptyStateText("No AI query has been run yet. Generate a prompt to inspect local invoice data.");
    }

    private void configureChartContainer() {
        chartContainer.setWidthFull();
        chartContainer.setVisible(false);
    }

    private void configureVisualizationMessage() {
        visualizationMessage.addClassName("ai-dashboard-message");
        visualizationMessage.setVisible(false);
    }

    private void configureGeneratedSqlArea() {
        generatedSqlArea.setWidthFull();
        generatedSqlArea.setReadOnly(true);
        generatedSqlArea.setMinHeight("12rem");
        generatedSqlDetails.setOpened(false);
        generatedSqlDetails.setVisible(false);
        generatedSqlDetails.setWidthFull();
    }

    private void configureResultSummary() {
        resultTitle.setVisible(false);
        resultExplanation.setVisible(false);
        resultTitle.addClassName("result-title");
        resultExplanation.addClassName("result-explanation");
    }

    private void configureMetadataBar() {
        metadataBar.addClassName("ai-dashboard-metadata");
        metadataBar.setSpacing(true);
        metadataBar.setVisible(false);
        metadataBar.add(rowCountBadge, openAiDurationBadge, sqlDurationBadge);
    }

    private void configureHistoryGrid() {
        historyGrid.setWidthFull();
        historyGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        historyGrid.setEmptyStateText("No AI history yet. Run a prompt to store sanitized metadata only.");
        historyGrid.addColumn(entry -> formatTimestamp(entry.createdAt())).setHeader("Created").setAutoWidth(true).setFlexGrow(0);
        historyGrid.addColumn(entry -> summarize(entry.prompt(), 96)).setHeader("Prompt").setAutoWidth(true).setFlexGrow(1);
        historyGrid.addColumn(entry -> entry.status().name()).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        historyGrid.addColumn(entry -> summarize(entry.title(), 48)).setHeader("Title").setAutoWidth(true).setFlexGrow(1);
        historyGrid.addColumn(entry -> entry.rowCount() == null ? "-" : Integer.toString(entry.rowCount())).setHeader("Rows")
                .setAutoWidth(true)
                .setFlexGrow(0);
        historyGrid.addColumn(entry -> formatDuration(entry.openAiDurationMs())).setHeader("OpenAI").setAutoWidth(true)
                .setFlexGrow(0);
        historyGrid.addColumn(entry -> formatDuration(entry.sqlDurationMs())).setHeader("SQL").setAutoWidth(true).setFlexGrow(0);
        historyGrid.addColumn(entry -> summarize(entry.sanitizedErrorMessage(), 80)).setHeader("Error").setAutoWidth(true)
                .setFlexGrow(1);
        historyGrid.addColumn(new ComponentRenderer<>(this::createHistoryAction)).setHeader("Action").setAutoWidth(true)
                .setFlexGrow(0);
    }

    private VerticalLayout createSection(String title, com.vaadin.flow.component.Component content) {
        H3 heading = new H3(title);
        heading.addClassName("section-title");
        VerticalLayout section = new VerticalLayout(heading, content);
        section.addClassNames("surface-card", "surface-card-section");
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();
        return section;
    }

    private VerticalLayout createSurfaceCard(Component content, String... classNames) {
        VerticalLayout card = createSurfaceCard(classNames);
        card.add(content);
        return card;
    }

    private VerticalLayout createSurfaceCard(String... classNames) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(false);
        card.setSpacing(true);
        card.addClassNames(classNames);
        return card;
    }

    private Paragraph createPromptExamples() {
        Paragraph examples = new Paragraph("""
                Prompt examples:
                Show the 25 most recent invoices
                Show total gross amount by contractor for paid invoices
                Show invoice count by month for EUR invoices
                """);
        examples.addClassName("prompt-examples");
        return examples;
    }

    private void showInitialStatus() {
        showStatus("""
                Enter an OpenAI API key and a prompt to run this experiment.

                OpenAI generates SQL and a visualization spec from your prompt plus the restricted schema. Invoice rows are never sent to OpenAI.
                Query history stores only the prompt and sanitized metadata, not secrets or result rows.
                """, StatusTone.INFO);
        clearResults();
    }

    private void generateDashboard(String requestedPrompt) {
        String apiKey = openAiApiKeyField.getValue() == null ? "" : openAiApiKeyField.getValue().trim();
        String prompt = requestedPrompt == null ? "" : requestedPrompt.trim();
        openAiApiKeyField.setInvalid(false);
        promptField.setInvalid(false);

        if (apiKey.isEmpty()) {
            openAiApiKeyField.setInvalid(true);
            showStatus(OPENAI_API_KEY_REQUIRED_MESSAGE, StatusTone.ERROR);
            clearResults();
            generateButton.setEnabled(true);
            return;
        }

        if (prompt.isEmpty()) {
            promptField.setInvalid(true);
            showStatus(PROMPT_REQUIRED_MESSAGE, StatusTone.ERROR);
            clearResults();
            generateButton.setEnabled(true);
            return;
        }

        promptField.setValue(prompt);
        setRunningState(true);
        showStatus("Generating SQL and visualization with OpenAI, then validating and executing the query locally.",
                StatusTone.INFO);
        UI ui = UI.getCurrent();

        executorService.execute(() -> {
            try {
                AiDashboardExecutionResult result = aiDashboardQueryService.execute(apiKey, prompt);
                if (ui != null && ui.isAttached()) {
                    ui.access(() -> {
                        renderQueryResult(result);
                        showStatus(buildSuccessStatus(result), StatusTone.SUCCESS);
                        setRunningState(false);
                        refreshHistory();
                    });
                }
            } catch (AiDashboardQueryException exception) {
                if (ui != null && ui.isAttached()) {
                    ui.access(() -> {
                        clearResults();
                        showStatus(exception.getMessage(), StatusTone.ERROR);
                        setRunningState(false);
                        refreshHistory();
                    });
                }
            } catch (RuntimeException exception) {
                if (ui != null && ui.isAttached()) {
                    ui.access(() -> {
                        clearResults();
                        showStatus("The AI dashboard request failed before a safe result could be rendered.", StatusTone.ERROR);
                        setRunningState(false);
                        refreshHistory();
                    });
                }
            }
        });
    }

    private void setRunningState(boolean running) {
        generateButton.setEnabled(!running);
        progressBar.setVisible(running);
        openAiApiKeyField.setEnabled(!running);
        promptField.setEnabled(!running);
    }

    private void restoreHistory(Long historyId) {
        try {
            AiDashboardExecutionResult result = aiDashboardQueryService.restoreFromHistory(historyId);
            renderQueryResult(result);
            showStatus("Restored the saved SQL and visualization metadata locally without calling OpenAI.", StatusTone.SUCCESS);
            refreshHistory();
        } catch (NoSuchElementException exception) {
            clearResults();
            showStatus(exception.getMessage(), StatusTone.ERROR);
            refreshHistory();
        } catch (RuntimeException exception) {
            clearResults();
            showStatus("The saved history entry could not be restored safely against the restricted invoice view.", StatusTone.ERROR);
            refreshHistory();
        }
    }

    private void deleteHistory(Long historyId) {
        aiDashboardQueryService.deleteHistory(historyId);
        showStatus("Deleted the selected AI query history entry.", StatusTone.SUCCESS);
        refreshHistory();
    }

    private void renderQueryResult(AiDashboardExecutionResult result) {
        renderQueryResult(result.generation(), result.validatedSql(), result.queryResult());
        rowCountBadge.setText("Rows: " + result.queryResult().rows().size());
        openAiDurationBadge.setText("OpenAI: " + result.openAiDurationMs() + " ms");
        sqlDurationBadge.setText("SQL: " + result.sqlDurationMs() + " ms");
        metadataBar.setVisible(true);
    }

    private Component createHistoryAction(AiQueryHistoryEntry entry) {
        Button restoreButton = new Button("Restore", event -> {
            promptField.setValue(entry.prompt());
            restoreHistory(entry.id());
        });
        restoreButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        restoreButton.setEnabled(entry.generatedSql() != null && !entry.generatedSql().isBlank());

        Button regenerateButton = new Button("Regenerate", event -> {
            promptField.setValue(entry.prompt());
            generateDashboard(entry.prompt());
        });
        regenerateButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteButton = new Button("Delete", event -> deleteHistory(entry.id()));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        HorizontalLayout actions = new HorizontalLayout(restoreButton, regenerateButton, deleteButton);
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private void refreshHistory() {
        historyGrid.setItems(aiDashboardQueryService.findRecentHistory());
    }

    private Span createMetricBadge() {
        Span badge = new Span();
        badge.addClassName("ai-dashboard-badge");
        return badge;
    }

    private void renderQueryResult(
            AiQueryGenerationResult generation,
            String validatedSql,
            DynamicSqlQueryResult queryResult) {
        resultTitle.setText(generation.title());
        resultTitle.setVisible(true);
        resultExplanation.setText(generation.explanation());
        resultExplanation.setVisible(true);
        generatedSqlArea.setValue(validatedSql);
        generatedSqlDetails.setVisible(true);
        configureDynamicColumns(queryResult.columns());
        resultGrid.setItems(queryResult.rows());
        resultGrid.setEmptyStateText(EMPTY_RESULTS_MESSAGE);
        renderVisualization(generation, queryResult);
    }

    private void clearResults() {
        metadataBar.setVisible(false);
        resultTitle.setVisible(false);
        resultExplanation.setVisible(false);
        resultTitle.setText("");
        resultExplanation.setText("");
        visualizationMessage.removeAll();
        visualizationMessage.setVisible(false);
        chartContainer.removeAll();
        chartContainer.setVisible(false);
        generatedSqlArea.clear();
        generatedSqlDetails.setVisible(false);
        resultGrid.removeAllColumns();
        configureDynamicColumns(List.of());
        resultGrid.setItems(List.of());
        resultGrid.setEmptyStateText("No AI query has been run yet. Generate a prompt to inspect local invoice data.");
    }

    private void showStatus(String message, StatusTone tone) {
        status.removeAll();
        status.setClassName("ai-dashboard-status");
        status.getElement().setAttribute("data-state", tone.name().toLowerCase(Locale.ROOT));
        status.getStyle().set("white-space", "pre-wrap");
        status.add(new Text(message));
    }

    private void renderVisualization(AiQueryGenerationResult generation, DynamicSqlQueryResult queryResult) {
        visualizationMessage.removeAll();
        visualizationMessage.setVisible(false);
        chartContainer.removeAll();
        chartContainer.setVisible(false);

        try {
            AiVisualizationSpec validatedSpec = visualizationValidator.validate(generation.visualizationSpec(), queryResult);
            if (validatedSpec.visualization() == AiVisualizationSpec.VisualizationType.TABLE) {
                showVisualizationMessage("The generated result is best represented as a table, so no chart was rendered.");
                return;
            }
            chartContainer.add(visualizationChartFactory.createChart(generation.title(), validatedSpec, queryResult));
            chartContainer.setVisible(true);
        } catch (AiVisualizationValidationException exception) {
            showVisualizationMessage(CHART_VALIDATION_PREFIX + exception.getMessage());
        }
    }

    private void showVisualizationMessage(String message) {
        visualizationMessage.removeAll();
        visualizationMessage.add(new Text(message));
        visualizationMessage.setVisible(true);
    }

    private void configureDynamicColumns(List<String> columns) {
        resultGrid.removeAllColumns();
        for (String column : columns) {
            resultGrid.addColumn(row -> formatValue(row.get(column)))
                    .setHeader(formatColumnHeader(column))
                    .setKey(column)
                    .setAutoWidth(true)
                    .setSortable(false);
        }
    }

    private String buildSuccessStatus(AiDashboardExecutionResult result) {
        if (result.queryResult().rows().isEmpty()) {
            return EMPTY_RESULTS_MESSAGE;
        }
        return "OpenAI generated SQL successfully. The query was validated locally, executed against the restricted invoice view, "
                + "and returned " + result.queryResult().rows().size() + " row(s).";
    }

    private String summarize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private String formatDuration(Long durationMs) {
        return durationMs == null ? "-" : durationMs + " ms";
    }

    private String formatTimestamp(java.time.LocalDateTime createdAt) {
        return createdAt == null ? "-" : createdAt.format(HISTORY_TIMESTAMP);
    }

    private String formatValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String formatColumnHeader(String column) {
        return Arrays.stream(column.split("_"))
                .filter(part -> !part.isBlank())
                .map(this::formatColumnWord)
                .collect(Collectors.joining(" "));
    }

    private String formatColumnWord(String word) {
        String normalized = word.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private enum StatusTone {
        INFO,
        SUCCESS,
        ERROR
    }
}
