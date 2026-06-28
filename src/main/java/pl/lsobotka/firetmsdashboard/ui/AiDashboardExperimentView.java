package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.grid.AIDataRow;
import com.vaadin.flow.component.ai.grid.GridAIController;
import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.Title;
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
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ai.RestrictedAiSalesInvoiceDatabaseProvider;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final String OPENAI_API_KEY_REQUIRED_MESSAGE = "OpenAI API key is required for this AI experiment.";
    private static final String PROVIDER_PLACEHOLDER_MESSAGE = """
            Safe placeholder mode.

            The restricted DatabaseProvider, GridAIController, and ChartAIController are wired,
            but this experiment does not yet create a Vaadin AI LLM provider from the
            user-entered OpenAI API key on each request.

            TODO:
            Wire a per-request OpenAI-backed provider so the UI key stays in session memory only.

            The FireTMS API key is never shared with AI, and raw_json remains unavailable.
            """;
    private static final String GENERIC_AI_ERROR_MESSAGE = """
            AI request could not be completed.

            Check the OpenAI API key and try again later.
            """;

    private final PasswordField openAiApiKeyField = new PasswordField("OpenAI API key");
    private final TextArea promptField = new TextArea("Prompt");
    private final Div status = new Div();
    private final Grid<AIDataRow> aiGrid = new Grid<>(AIDataRow.class, false);
    private final Chart aiChart = new Chart();
    private final AIOrchestrator orchestrator = null;

    public AiDashboardExperimentView(RestrictedAiSalesInvoiceDatabaseProvider databaseProvider) {
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1200px");

        H2 heading = new H2("AI Dashboard Experiment");
        Paragraph description = new Paragraph(
                "This page experiments with Vaadin AI-generated grids and charts over a restricted analytics view.");

        configureOpenAiApiKeyField();
        configurePromptField();
        configureGrid();
        configureChart();

        new GridAIController(aiGrid, databaseProvider);
        new ChartAIController(aiChart, databaseProvider);

        Button generateButton = new Button("Generate", event -> generateDashboard());

        add(
                heading,
                description,
                createPromptExamples(),
                openAiApiKeyField,
                promptField,
                generateButton,
                status,
                createSection("AI Grid", aiGrid),
                createSection("AI Chart", aiChart),
                new Details("Restricted AI schema", new Text(databaseProvider.getSchema())));

        showInitialStatus();
    }

    private void configureOpenAiApiKeyField() {
        openAiApiKeyField.setWidthFull();
        openAiApiKeyField.setRevealButtonVisible(false);
        openAiApiKeyField.setHelperText("Used only for AI dashboard requests in the current UI session. It is not persisted.");
    }

    private void configurePromptField() {
        promptField.setWidthFull();
        promptField.setMinHeight("180px");
        promptField.setPlaceholder("""
                Show gross sales by month
                Show top 10 contractors by gross amount
                Show invoice count by status
                """);
        promptField.setHelperText("Read-only experiment backed only by ai_sales_invoice_view.");
    }

    private void configureGrid() {
        aiGrid.setWidthFull();
        aiGrid.setMinHeight("20rem");
        aiGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        aiGrid.setEmptyStateText("No AI-generated grid query has been applied yet.");
    }

    private void configureChart() {
        aiChart.setWidthFull();
        aiChart.setHeight("24rem");

        Configuration configuration = aiChart.getConfiguration();
        configuration.getChart().setType(ChartType.COLUMN);
        configuration.setTitle(new Title("AI-generated chart preview"));
        configuration.setSubTitle("Awaiting a prompt and per-request OpenAI provider wiring.");
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
                Show gross sales by month
                Show top 10 contractors by gross amount
                Show invoice count by status
                """);
    }

    private void showInitialStatus() {
        showStatus("""
                Enter an OpenAI API key and a prompt to run this experiment.

                The AI surface is read-only and restricted to ai_sales_invoice_view.
                """);
    }

    private void generateDashboard() {
        String apiKey = openAiApiKeyField.getValue() == null ? "" : openAiApiKeyField.getValue().trim();
        String prompt = promptField.getValue() == null ? "" : promptField.getValue().trim();
        if (apiKey.isEmpty()) {
            showStatus(OPENAI_API_KEY_REQUIRED_MESSAGE);
            return;
        }

        if (prompt.isEmpty()) {
            showStatus("Enter a prompt to prepare the AI dashboard experiment.");
            return;
        }

        if (orchestrator == null) {
            showStatus(PROVIDER_PLACEHOLDER_MESSAGE);
            return;
        }

        try {
            showStatus("AI request submitted. The grid and chart will update if the generated SQL passes validation.");
            orchestrator.prompt(prompt);
        } catch (RuntimeException exception) {
            showStatus(GENERIC_AI_ERROR_MESSAGE);
        }
    }

    private void showStatus(String message) {
        status.removeAll();
        status.getStyle().set("white-space", "pre-wrap");
        status.add(new Text(message));
    }
}
