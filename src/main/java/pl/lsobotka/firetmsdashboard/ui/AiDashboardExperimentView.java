package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Component;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ai.AiDashboardIntent;
import pl.lsobotka.firetmsdashboard.ai.AiIntentClassificationResult;
import pl.lsobotka.firetmsdashboard.ai.OpenAiIntentClassificationException;
import pl.lsobotka.firetmsdashboard.ai.OpenAiIntentClassifier;
import pl.lsobotka.firetmsdashboard.ai.RestrictedAiSalesInvoiceDatabaseProvider;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.ContractorGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.Money;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.MonthlyGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsService;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsSnapshot;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.StatusInvoiceCount;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String OPENAI_API_KEY_REQUIRED_MESSAGE = "OpenAI API key is required for this AI experiment.";
    private static final String PROMPT_REQUIRED_MESSAGE = "Prompt is required for this AI experiment.";
    private static final String OPENAI_SOURCE_MESSAGE = "Generated using OpenAI intent classification";
    private static final String UNKNOWN_EXAMPLES = """
            OpenAI could not match that prompt to one of the supported dashboard intents.

            Try one of these examples:
            - Show gross sales by month
            - Show top 10 contractors by gross amount
            - Show invoice count by status
            """;

    private final PasswordField openAiApiKeyField = new PasswordField("OpenAI API key");
    private final TextArea promptField = new TextArea("Prompt");
    private final Div status = new Div();
    private final Div source = new Div();
    private final Grid<AiDashboardResultRow> resultGrid = new Grid<>(AiDashboardResultRow.class, false);
    private final Div chartContainer = new Div();
    private final OpenAiIntentClassifier intentClassifier;
    private final SalesInvoiceAnalyticsService analyticsService;
    private final DashboardChartsFactory chartsFactory;

    public AiDashboardExperimentView(
            OpenAiIntentClassifier intentClassifier,
            SalesInvoiceAnalyticsService analyticsService,
            DashboardChartsFactory chartsFactory,
            RestrictedAiSalesInvoiceDatabaseProvider databaseProvider) {
        this.intentClassifier = intentClassifier;
        this.analyticsService = analyticsService;
        this.chartsFactory = chartsFactory;
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1200px");

        H2 heading = new H2("AI Dashboard Experiment");
        Paragraph description = new Paragraph(
                "This page uses OpenAI only to classify the prompt into a supported dashboard intent. "
                        + "The app then runs the matching analytics locally over persisted invoice data.");

        configureOpenAiApiKeyField();
        configurePromptField();
        configureSource();
        configureGrid();
        configureChartContainer();

        Button generateButton = new Button("Generate", event -> generateDashboard());

        add(
                heading,
                description,
                createPromptExamples(),
                openAiApiKeyField,
                promptField,
                generateButton,
                status,
                source,
                createSection("Result Grid", resultGrid),
                createSection("Result Chart", chartContainer),
                new Details("Restricted AI schema", new Text(databaseProvider.getSchema())));

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
                Show gross sales by month
                Show top 10 contractors by gross amount
                Show invoice count by status
                """);
        promptField.setHelperText("Only the prompt, allowed intents, and JSON output schema are sent to OpenAI. "
                + "Invoice data stays local.");
        promptField.setErrorMessage(PROMPT_REQUIRED_MESSAGE);
    }

    private void configureGrid() {
        resultGrid.setWidthFull();
        resultGrid.setMinHeight("20rem");
        resultGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        resultGrid.addColumn(AiDashboardResultRow::label).setHeader("Label").setFlexGrow(1);
        resultGrid.addColumn(AiDashboardResultRow::value).setHeader("Value").setAutoWidth(true);
        resultGrid.setEmptyStateText("No OpenAI-classified dashboard result has been applied yet.");
    }

    private void configureSource() {
        source.getStyle().set("white-space", "pre-wrap");
        source.getStyle().set("font-weight", "600");
        source.setVisible(false);
    }

    private void configureChartContainer() {
        chartContainer.setWidthFull();
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

                OpenAI classifies the prompt only. SQL and data aggregation remain local and controlled by the app.
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
            showStatus("Submitting prompt to OpenAI for intent classification.");
            AiIntentClassificationResult classification = intentClassifier.classify(apiKey, prompt);
            source.removeAll();
            source.add(new Text(OPENAI_SOURCE_MESSAGE));
            source.setVisible(true);

            if (classification.intent() == AiDashboardIntent.UNKNOWN) {
                clearVisualResults();
                showStatus(UNKNOWN_EXAMPLES + "\nReason: " + classification.reason());
                return;
            }

            SalesInvoiceAnalyticsSnapshot analytics = analyticsService.loadAnalytics();
            renderIntent(classification, analytics);
            showStatus("""
                    OpenAI classified the prompt successfully.

                    Intent: %s
                    Reason: %s
                    """.formatted(classification.intent(), classification.reason()));
        } catch (OpenAiIntentClassificationException exception) {
            clearResults();
            showStatus(exception.getMessage());
        }
    }

    private void renderIntent(AiIntentClassificationResult classification, SalesInvoiceAnalyticsSnapshot analytics) {
        switch (classification.intent()) {
        case GROSS_SALES_BY_MONTH -> renderGrossSalesByMonth(classification.limit(), analytics.monthlyGrossSales());
        case TOP_CONTRACTORS_BY_GROSS_AMOUNT -> renderTopContractors(classification.limit(), analytics.contractorGrossSales());
        case INVOICE_COUNT_BY_STATUS -> renderInvoiceCountByStatus(classification.limit(), analytics.statusInvoiceCounts());
        case UNKNOWN -> throw new IllegalStateException("UNKNOWN must be handled before rendering");
        }
    }

    private void renderGrossSalesByMonth(int limit, List<MonthlyGrossSales> rows) {
        List<MonthlyGrossSales> limitedRows = rows.stream().limit(limit).toList();
        resultGrid.setItems(limitedRows.stream()
                .map(row -> new AiDashboardResultRow(formatMonth(row.month()), formatMonthlyAmounts(row)))
                .toList());
        setChart(chartsFactory.createGrossAmountByMonthChart(limitedRows));
    }

    private void renderTopContractors(int limit, List<ContractorGrossSales> rows) {
        List<ContractorGrossSales> limitedRows = rows.stream().limit(limit).toList();
        resultGrid.setItems(limitedRows.stream()
                .map(row -> new AiDashboardResultRow(row.contractorName(), formatAmounts(row.grossAmounts())))
                .toList());
        setChart(chartsFactory.createGrossAmountByContractorChart(limitedRows));
    }

    private void renderInvoiceCountByStatus(int limit, List<StatusInvoiceCount> rows) {
        List<StatusInvoiceCount> limitedRows = rows.stream().limit(limit).toList();
        resultGrid.setItems(limitedRows.stream()
                .map(row -> new AiDashboardResultRow(row.status(), String.valueOf(row.invoiceCount())))
                .toList());
        setChart(chartsFactory.createInvoiceCountByStatusChart(limitedRows));
    }

    private void clearResults() {
        source.removeAll();
        source.setVisible(false);
        clearVisualResults();
    }

    private void clearVisualResults() {
        resultGrid.setItems(List.of());
        chartContainer.removeAll();
    }

    private void setChart(Component chart) {
        chartContainer.removeAll();
        chartContainer.add(chart);
    }

    private String formatMonth(YearMonth month) {
        return month == null ? "Unknown" : month.format(MONTH_FORMATTER);
    }

    private String formatMonthlyAmounts(MonthlyGrossSales row) {
        return formatAmounts(row.grossAmounts());
    }

    private String formatAmounts(List<Money> amounts) {
        return amounts.stream()
                .map(amount -> amount.amount().toPlainString() + " " + amount.currency())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private void showStatus(String message) {
        status.removeAll();
        status.getStyle().set("white-space", "pre-wrap");
        status.add(new Text(message));
    }

    private record AiDashboardResultRow(String label, String value) {
    }
}
