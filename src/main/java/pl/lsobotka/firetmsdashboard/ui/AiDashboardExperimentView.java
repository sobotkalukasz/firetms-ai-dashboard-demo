package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ai.AiSqlGenerationResult;
import pl.lsobotka.firetmsdashboard.ai.DynamicSqlQueryResult;
import pl.lsobotka.firetmsdashboard.ai.DynamicSqlQueryService;
import pl.lsobotka.firetmsdashboard.ai.OpenAiSqlGenerationException;
import pl.lsobotka.firetmsdashboard.ai.OpenAiSqlGenerator;
import pl.lsobotka.firetmsdashboard.ai.SqlSafetyValidator;
import pl.lsobotka.firetmsdashboard.ai.SqlValidationException;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final String OPENAI_API_KEY_REQUIRED_MESSAGE = "OpenAI API key is required for this AI experiment.";
    private static final String PROMPT_REQUIRED_MESSAGE = "Prompt is required for this AI experiment.";
    private static final String OPENAI_SOURCE_MESSAGE = "Generated using OpenAI SQL generation";
    private static final String EMPTY_RESULTS_MESSAGE = "The generated query returned no rows.";

    private final PasswordField openAiApiKeyField = new PasswordField("OpenAI API key");
    private final TextArea promptField = new TextArea("Prompt");
    private final Div status = new Div();
    private final H3 resultTitle = new H3();
    private final Paragraph resultExplanation = new Paragraph();
    private final Div source = new Div();
    private final Grid<Map<String, Object>> resultGrid = new Grid<>();
    private final TextArea generatedSqlArea = new TextArea();
    private final Details generatedSqlDetails = new Details("Generated SQL", generatedSqlArea);
    private final OpenAiSqlGenerator sqlGenerator;
    private final DynamicSqlQueryService dynamicSqlQueryService;
    private final SqlSafetyValidator sqlSafetyValidator;

    public AiDashboardExperimentView(
            OpenAiSqlGenerator sqlGenerator,
            DynamicSqlQueryService dynamicSqlQueryService,
            SqlSafetyValidator sqlSafetyValidator) {
        this.sqlGenerator = sqlGenerator;
        this.dynamicSqlQueryService = dynamicSqlQueryService;
        this.sqlSafetyValidator = sqlSafetyValidator;
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1200px");

        H2 heading = new H2("AI Dashboard Experiment");
        Paragraph description = new Paragraph(
                "This page uses OpenAI to generate SQL against a restricted invoice view. "
                        + "Only the prompt, safe schema, and SQL rules are sent to OpenAI. Invoice rows stay local.");

        configureOpenAiApiKeyField();
        configurePromptField();
        configureSource();
        configureGrid();
        configureGeneratedSqlArea();
        configureResultSummary();

        Button generateButton = new Button("Generate", event -> generateDashboard());

        add(
                heading,
                description,
                createPromptExamples(),
                openAiApiKeyField,
                promptField,
                generateButton,
                status,
                resultTitle,
                resultExplanation,
                source,
                createSection("Result Grid", resultGrid),
                generatedSqlDetails,
                new Details("Restricted AI schema", new Text(sqlSafetyValidator.schemaDescription())));

        showInitialStatus();
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

    private void configureGrid() {
        resultGrid.setWidthFull();
        resultGrid.setMinHeight("20rem");
        resultGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        resultGrid.setEmptyStateText("No generated SQL result has been applied yet.");
    }

    private void configureSource() {
        source.getStyle().set("white-space", "pre-wrap");
        source.getStyle().set("font-weight", "600");
        source.setVisible(false);
    }

    private void configureGeneratedSqlArea() {
        generatedSqlArea.setWidthFull();
        generatedSqlArea.setReadOnly(true);
        generatedSqlArea.setMinHeight("12rem");
        generatedSqlDetails.setOpened(false);
        generatedSqlDetails.setVisible(false);
    }

    private void configureResultSummary() {
        resultTitle.setVisible(false);
        resultExplanation.setVisible(false);
    }

    private VerticalLayout createSection(String title, com.vaadin.flow.component.Component content) {
        H3 heading = new H3(title);
        VerticalLayout section = new VerticalLayout(heading, content);
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private Paragraph createPromptExamples() {
        return new Paragraph("""
                Prompt examples:
                Show the 25 most recent invoices
                Show total gross amount by contractor for paid invoices
                Show invoice count by month for EUR invoices
                """);
    }

    private void showInitialStatus() {
        showStatus("""
                Enter an OpenAI API key and a prompt to run this experiment.

                OpenAI generates SQL only from your prompt plus the restricted schema. Invoice rows are never sent to OpenAI.
                """);
        clearResults();
    }

    private void generateDashboard() {
        String apiKey = openAiApiKeyField.getValue() == null ? "" : openAiApiKeyField.getValue().trim();
        String prompt = promptField.getValue() == null ? "" : promptField.getValue().trim();
        openAiApiKeyField.setInvalid(false);
        promptField.setInvalid(false);

        if (apiKey.isEmpty()) {
            openAiApiKeyField.setInvalid(true);
            showStatus(OPENAI_API_KEY_REQUIRED_MESSAGE);
            clearResults();
            return;
        }

        if (prompt.isEmpty()) {
            promptField.setInvalid(true);
            showStatus(PROMPT_REQUIRED_MESSAGE);
            clearResults();
            return;
        }

        try {
            showStatus("Submitting prompt to OpenAI for SQL generation.");
            AiSqlGenerationResult generation = sqlGenerator.generate(apiKey, prompt);
            QueryExecution execution = executeQueryWithCorrection(apiKey, prompt, generation);

            renderQueryResult(execution.generation(), execution.validatedSql(), execution.queryResult());
            source.removeAll();
            source.add(new Text(OPENAI_SOURCE_MESSAGE));
            source.setVisible(true);
            showStatus(execution.queryResult().rows().isEmpty()
                    ? EMPTY_RESULTS_MESSAGE
                    : "OpenAI generated SQL successfully and the query was executed locally.");
        } catch (OpenAiSqlGenerationException | SqlValidationException exception) {
            clearResults();
            showStatus(exception.getMessage());
        } catch (RuntimeException exception) {
            clearResults();
            showStatus("The SQL query could not be executed safely. Try refining the prompt.");
        }
    }

    private void renderQueryResult(
            AiSqlGenerationResult generation,
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
    }

    private void clearResults() {
        source.removeAll();
        source.setVisible(false);
        resultTitle.setVisible(false);
        resultExplanation.setVisible(false);
        resultTitle.setText("");
        resultExplanation.setText("");
        generatedSqlArea.clear();
        generatedSqlDetails.setVisible(false);
        resultGrid.removeAllColumns();
        resultGrid.setItems(List.of());
    }

    private void showStatus(String message) {
        status.removeAll();
        status.getStyle().set("white-space", "pre-wrap");
        status.add(new Text(message));
    }

    private void configureDynamicColumns(List<String> columns) {
        resultGrid.removeAllColumns();
        for (String column : columns) {
            resultGrid.addColumn(row -> formatValue(row.get(column)))
                    .setHeader(column)
                    .setKey(column)
                    .setAutoWidth(true)
                    .setSortable(false);
        }
    }

    private QueryExecution executeQueryWithCorrection(String apiKey, String prompt, AiSqlGenerationResult generation) {
        String validatedSql = sqlSafetyValidator.validateAndNormalize(generation.sql());
        try {
            DynamicSqlQueryResult queryResult = dynamicSqlQueryService.executeValidatedQuery(validatedSql);
            return new QueryExecution(generation, validatedSql, queryResult);
        } catch (DataAccessException exception) {
            String correctionFeedback = extractCorrectionFeedback(exception);
            AiSqlGenerationResult correctedGeneration = sqlGenerator.correct(apiKey, prompt, validatedSql, correctionFeedback);
            String correctedSql = sqlSafetyValidator.validateAndNormalize(correctedGeneration.sql());
            DynamicSqlQueryResult correctedResult = dynamicSqlQueryService.executeValidatedQuery(correctedSql);
            return new QueryExecution(correctedGeneration, correctedSql, correctedResult);
        }
    }

    private String extractCorrectionFeedback(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return "The SQL could not be executed by H2. Return corrected H2-compatible SQL.";
    }

    private record QueryExecution(
            AiSqlGenerationResult generation,
            String validatedSql,
            DynamicSqlQueryResult queryResult) {
    }

    private String formatValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
