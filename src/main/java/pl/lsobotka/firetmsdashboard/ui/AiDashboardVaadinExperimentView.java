package pl.lsobotka.firetmsdashboard.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.grid.AIDataRow;
import com.vaadin.flow.component.ai.grid.GridAIController;
import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ai.integration.openai.AiOpenAiProperties;
import pl.lsobotka.firetmsdashboard.ai.integration.vaadin.DelegatingAiController;
import pl.lsobotka.firetmsdashboard.ai.integration.vaadin.OpenAiResponsesLlmProvider;
import pl.lsobotka.firetmsdashboard.ai.integration.vaadin.RestrictedInvoiceDatabaseProvider;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

@Route(value = MainView.AI_DASHBOARD_VAADIN_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Vaadin Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard Vaadin", order = 20)
public class AiDashboardVaadinExperimentView extends VerticalLayout {

    private static final String OPENAI_API_KEY_REQUIRED_MESSAGE = "OpenAI API key is required for this Vaadin AI experiment.";
    private static final String SYSTEM_PROMPT = """
            You are a dashboard assistant for FireTMS sales invoice data.

            Use the available tools to update the result grid and chart directly.
            Query only ai_sales_invoice_view with H2-compatible SQL.
            Always inspect the current state and schema before changing grid or chart data.
            Keep SQL read-only and include a LIMIT in the base query.
            Never use reserved H2 keywords as aliases. In particular avoid aliases such as value, month, year, and date.
            Prefer safe aliases such as month_value, metric_value, label_value, status_label, category_label, and total_amount.

            Visualization guidance:
            - use LINE or COLUMN for trends over time
            - use BAR for top categories or contractors
            - use PIE or COLUMN for shares by status or currency
            - if the request is primarily tabular, update only the grid

            Respond briefly with what you changed.
            """;

    private final PasswordField openAiApiKeyField = new PasswordField("OpenAI API key");
    private final Div status = new Div();
    private final MessageList messageList = new MessageList();
    private final MessageInput messageInput = new MessageInput();
    private final Grid<AIDataRow> resultGrid = new Grid<>();
    private final Chart resultChart = new Chart(ChartType.COLUMN);
    private final AIOrchestrator orchestrator;

    public AiDashboardVaadinExperimentView(
            RestrictedInvoiceDatabaseProvider databaseProvider,
            @Qualifier("openAiRestClient") RestClient openAiRestClient,
            ObjectMapper objectMapper,
            AiOpenAiProperties properties) {
        setPadding(true);
        setSpacing(true);
        setWidthFull();
        addClassName("ai-dashboard-view");

        configureOpenAiApiKeyField();
        configureStatus();
        configureMessageList();
        configureMessageInput();
        configureGrid();
        configureChart();

        GridAIController gridController = new GridAIController(resultGrid, databaseProvider);
        ChartAIController chartController = new ChartAIController(resultChart, databaseProvider);
        this.orchestrator = AIOrchestrator.builder(
                        new OpenAiResponsesLlmProvider(
                                openAiRestClient,
                                objectMapper,
                                properties,
                                () -> openAiApiKeyField.getValue() == null ? "" : openAiApiKeyField.getValue().trim()),
                        SYSTEM_PROMPT)
                .withController(new DelegatingAiController(List.of(gridController, chartController)))
                .withMessageList(messageList)
                .withUserName("You")
                .withAssistantName("Vaadin AI")
                .withResponseListener(event -> {
                    getUI().ifPresent(ui -> ui.access(() -> event.getError()
                            .ifPresentOrElse(
                                    error -> showStatus(error.getMessage(), StatusTone.ERROR),
                                    () -> showStatus("Grid and chart updated using Vaadin AI controllers.", StatusTone.SUCCESS))));
                })
                .build();

        H2 heading = new H2("AI Dashboard Vaadin Experiment");
        heading.addClassName("page-hero-title");
        Paragraph description = new Paragraph(
                "This experiment uses Vaadin 25.2 AI controllers directly. The LLM can update a Grid and a Chart through "
                        + "GridAIController and ChartAIController against the restricted invoice view.");
        description.addClassName("page-hero-description");

        Button resetSessionButton = new Button("Reset Session", event -> resetSession());
        resetSessionButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout heroCard = createSurfaceCard("surface-card", "surface-card-hero");
        heroCard.add(heading, description, createPromptExamples(), openAiApiKeyField, status, resetSessionButton);

        add(
                heroCard,
                createSection("AI Conversation", messageList, messageInput),
                createSection("AI Grid", resultGrid),
                createSection("AI Chart", resultChart));

        showInitialStatus();
    }

    private void configureOpenAiApiKeyField() {
        openAiApiKeyField.setWidthFull();
        openAiApiKeyField.setRevealButtonVisible(false);
        openAiApiKeyField.setHelperText("Used only for this Vaadin AI session. It is not persisted.");
        openAiApiKeyField.setErrorMessage(OPENAI_API_KEY_REQUIRED_MESSAGE);
    }

    private void configureStatus() {
        status.setClassName("ai-dashboard-status");
        status.getStyle().set("white-space", "pre-wrap");
    }

    private void configureMessageList() {
        messageList.setWidthFull();
        messageList.setHeight("20rem");
    }

    private void configureMessageInput() {
        messageInput.setWidthFull();
        messageInput.addSubmitListener(event -> submitPrompt(event.getValue()));
    }

    private void configureGrid() {
        resultGrid.setWidthFull();
        resultGrid.setMinHeight("20rem");
        resultGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        resultGrid.setEmptyStateText("Ask the Vaadin AI controller to load invoice data into the grid.");
    }

    private void configureChart() {
        resultChart.setWidthFull();
        resultChart.setHeight("24rem");
        resultChart.getConfiguration().setTitle("AI chart will appear here");
    }

    private void submitPrompt(String prompt) {
        String apiKey = openAiApiKeyField.getValue() == null ? "" : openAiApiKeyField.getValue().trim();
        openAiApiKeyField.setInvalid(false);
        if (apiKey.isEmpty()) {
            openAiApiKeyField.setInvalid(true);
            showStatus(OPENAI_API_KEY_REQUIRED_MESSAGE, StatusTone.ERROR);
            return;
        }
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        showStatus("Vaadin AI is inspecting the schema and updating the grid or chart.", StatusTone.INFO);
        orchestrator.prompt(prompt.trim());
    }

    private void resetSession() {
        getUI().ifPresent(ui -> ui.getPage().reload());
    }

    private Paragraph createPromptExamples() {
        Paragraph examples = new Paragraph("""
                Prompt examples:
                Show the 20 latest invoices in the grid
                Show invoice count by status as a chart
                Show monthly gross amount totals by currency in both the grid and chart
                """);
        examples.addClassName("prompt-examples");
        return examples;
    }

    private void showInitialStatus() {
        showStatus("""
                Enter an OpenAI API key, then ask for a grid or chart update.

                This experiment uses Vaadin's GridAIController and ChartAIController directly against the restricted invoice view.
                """, StatusTone.INFO);
    }

    private void showStatus(String message, StatusTone tone) {
        status.removeAll();
        status.getElement().setAttribute("data-state", tone.name().toLowerCase());
        status.add(new Text(message));
    }

    private VerticalLayout createSection(String title, com.vaadin.flow.component.Component... content) {
        H3 heading = new H3(title);
        heading.addClassName("section-title");
        VerticalLayout section = new VerticalLayout();
        section.addClassNames("surface-card", "surface-card-section");
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();
        section.add(heading);
        section.add(content);
        return section;
    }

    private VerticalLayout createSurfaceCard(String... classNames) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(false);
        card.setSpacing(true);
        card.addClassNames(classNames);
        return card;
    }

    private enum StatusTone {
        INFO,
        SUCCESS,
        ERROR
    }
}
