package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.grid.AIDataRow;
import com.vaadin.flow.component.ai.grid.GridAIController;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.charts.Chart;
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
import pl.lsobotka.firetmsdashboard.ai.AiDashboardPromptService;
import pl.lsobotka.firetmsdashboard.ai.RestrictedAiSalesInvoiceDatabaseProvider;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.ContractorGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.Money;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.MonthlyGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.StatusInvoiceCount;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String OPENAI_API_KEY_REQUIRED_MESSAGE = "OpenAI API key is required for this AI experiment.";
    private static final String PROMPT_REQUIRED_MESSAGE = "Enter a prompt to prepare the AI dashboard experiment.";
    private static final String OPENAI_NOT_WIRED_MESSAGE =
            "OpenAI key was provided, but real Vaadin AI integration is not wired yet.";

    private final PasswordField openAiApiKeyField = new PasswordField("OpenAI API key");
    private final TextArea promptField = new TextArea("Prompt");
    private final Div status = new Div();
    private final Div resultArea = new Div();
    private final Grid<AIDataRow> aiGrid = new Grid<>(AIDataRow.class, false);
    private final Chart aiChart = new Chart();
    private final AiDashboardPromptService promptService;
    private final DashboardChartsFactory chartsFactory;

    public AiDashboardExperimentView(
            RestrictedAiSalesInvoiceDatabaseProvider databaseProvider,
            AiDashboardPromptService promptService,
            DashboardChartsFactory chartsFactory) {
        this.promptService = promptService;
        this.chartsFactory = chartsFactory;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1200px");

        H2 heading = new H2("AI Dashboard Experiment");
        Paragraph description = new Paragraph(
                "This page experiments with Vaadin AI-generated grids and charts over a restricted analytics view.");

        configureOpenAiApiKeyField();
        configurePromptField();
        configureResultArea();
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
                resultArea,
                createExperimentalAiSection(databaseProvider));

        showInitialStatus();
        showEmptyResultState();
    }

    private void configureOpenAiApiKeyField() {
        openAiApiKeyField.setWidthFull();
        openAiApiKeyField.setRevealButtonVisible(false);
        openAiApiKeyField.setHelperText("Used only for AI dashboard requests in the current UI session. It is not persisted.");
    }

    private void configurePromptField() {
        promptField.setWidthFull();
        promptField.setMinHeight("10rem");
        promptField.setPlaceholder("""
                Show gross sales by month
                Show top 10 contractors by gross amount
                Show invoice count by status
                """);
        promptField.setHelperText("Read-only experiment backed only by ai_sales_invoice_view.");
    }

    private void configureResultArea() {
        resultArea.setWidthFull();
    }

    private void configureGrid() {
        aiGrid.setWidthFull();
        aiGrid.setMinHeight("16rem");
        aiGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        aiGrid.setEmptyStateText("Experimental AI grid wiring is present, but prompt execution is not enabled.");
    }

    private void configureChart() {
        aiChart.setWidthFull();
        aiChart.setHeight("24rem");
        aiChart.getConfiguration().setTitle("Experimental AI chart preview");
        aiChart.getConfiguration().setSubTitle("Prompt execution remains on the local safe fallback path.");
    }

    private VerticalLayout createSection(String title, com.vaadin.flow.component.Component content) {
        H3 heading = new H3(title);
        VerticalLayout section = new VerticalLayout(heading, content);
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private Details createExperimentalAiSection(RestrictedAiSalesInvoiceDatabaseProvider databaseProvider) {
        Paragraph note = new Paragraph(
                "The restricted DatabaseProvider, GridAIController, and ChartAIController remain wired, "
                        + "but the UI-entered OpenAI key is not used for live AI requests yet.");
        VerticalLayout content = new VerticalLayout(
                note,
                createSection("Experimental AI Grid", aiGrid),
                createSection("Experimental AI Chart", aiChart),
                new Details("Restricted AI schema", new Text(databaseProvider.getSchema())));
        content.setPadding(false);
        content.setSpacing(true);
        return new Details("Experimental Vaadin AI wiring", content);
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
            showEmptyResultState();
            return;
        }

        if (prompt.isEmpty()) {
            showStatus(PROMPT_REQUIRED_MESSAGE);
            showEmptyResultState();
            return;
        }

        AiDashboardPromptService.PromptResult result = promptService.handlePrompt(prompt);
        if (!result.recognized()) {
            showStatus(result.message() + "\n" + OPENAI_NOT_WIRED_MESSAGE);
            renderUnknownPromptResult(result);
            return;
        }

        showStatus(result.message() + "\n" + OPENAI_NOT_WIRED_MESSAGE);
        renderRecognizedPromptResult(result);
    }

    private void showStatus(String message) {
        status.removeAll();
        status.getStyle().set("white-space", "pre-wrap");
        status.add(new Text(message));
    }

    private void showEmptyResultState() {
        resultArea.removeAll();
        resultArea.add(createResultSection(
                "Result",
                new Paragraph("No generated analytics yet. Enter a supported prompt to render a safe local result.")));
    }

    private void renderUnknownPromptResult(AiDashboardPromptService.PromptResult result) {
        Paragraph help = new Paragraph(result.message());
        help.getStyle().set("white-space", "pre-wrap");
        resultArea.removeAll();
        resultArea.add(createResultSection(result.title(), help));
    }

    private void renderRecognizedPromptResult(AiDashboardPromptService.PromptResult result) {
        com.vaadin.flow.component.Component chart = switch (result.intent()) {
            case GROSS_SALES_BY_MONTH -> chartsFactory.createGrossAmountByMonthChart(result.monthlyGrossSales());
            case TOP_CONTRACTORS_BY_GROSS_AMOUNT -> chartsFactory.createGrossAmountByContractorChart(
                    result.contractorGrossSales());
            case INVOICE_COUNT_BY_STATUS -> chartsFactory.createInvoiceCountByStatusChart(result.statusInvoiceCounts());
            case UNKNOWN -> new Paragraph("No chart available.");
        };

        Grid<?> grid = switch (result.intent()) {
            case GROSS_SALES_BY_MONTH -> createMonthlyGrid(result.monthlyGrossSales());
            case TOP_CONTRACTORS_BY_GROSS_AMOUNT -> createContractorGrid(result.contractorGrossSales());
            case INVOICE_COUNT_BY_STATUS -> createStatusGrid(result.statusInvoiceCounts());
            case UNKNOWN -> new Grid<>();
        };

        Paragraph fallbackMessage = new Paragraph(result.message());
        resultArea.removeAll();
        resultArea.add(createResultSection(result.title(), fallbackMessage, chart, grid));
    }

    private VerticalLayout createResultSection(String title, com.vaadin.flow.component.Component... content) {
        H3 heading = new H3(title);
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setPadding(false);
        section.setSpacing(true);
        section.add(heading);
        section.add(content);
        return section;
    }

    private Grid<MonthlyGrossSales> createMonthlyGrid(List<MonthlyGrossSales> rows) {
        Grid<MonthlyGrossSales> grid = new Grid<>(MonthlyGrossSales.class, false);
        configureResultGrid(grid, "No monthly analytics available.");
        grid.addColumn(this::formatMonth).setHeader("Month").setAutoWidth(true).setSortable(true);
        grid.addColumn(row -> formatMoney(row.grossAmounts())).setHeader("Gross amount").setAutoWidth(true);
        grid.setItems(rows);
        return grid;
    }

    private Grid<ContractorGrossSales> createContractorGrid(List<ContractorGrossSales> rows) {
        Grid<ContractorGrossSales> grid = new Grid<>(ContractorGrossSales.class, false);
        configureResultGrid(grid, "No contractor analytics available.");
        grid.addColumn(ContractorGrossSales::contractorName).setHeader("Contractor").setFlexGrow(1);
        grid.addColumn(row -> formatMoney(row.grossAmounts())).setHeader("Gross amount").setAutoWidth(true);
        grid.setItems(rows);
        return grid;
    }

    private Grid<StatusInvoiceCount> createStatusGrid(List<StatusInvoiceCount> rows) {
        Grid<StatusInvoiceCount> grid = new Grid<>(StatusInvoiceCount.class, false);
        configureResultGrid(grid, "No status analytics available.");
        grid.addColumn(StatusInvoiceCount::status).setHeader("Status").setAutoWidth(true);
        grid.addColumn(StatusInvoiceCount::invoiceCount).setHeader("Invoice count").setAutoWidth(true);
        grid.setItems(rows);
        return grid;
    }

    private void configureResultGrid(Grid<?> grid, String emptyStateText) {
        grid.setWidthFull();
        grid.setHeight("16rem");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText(emptyStateText);
    }

    private String formatMonth(MonthlyGrossSales result) {
        YearMonth month = result.month();
        return month == null ? "Unknown" : month.format(MONTH_FORMATTER);
    }

    private String formatMoney(List<Money> amounts) {
        return amounts.stream()
                .map(amount -> amount.amount().toPlainString() + " " + amount.currency())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
