package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.chart.ChartAIController;
import com.vaadin.flow.component.ai.grid.AIDataRow;
import com.vaadin.flow.component.ai.grid.GridAIController;
import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.ai.provider.LLMProvider;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ai.DelegatingAiController;
import pl.lsobotka.firetmsdashboard.ai.RestrictedAiSalesInvoiceDatabaseProvider;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final String SYSTEM_PROMPT = """
            You are configuring read-only analytics components for FireTMS issued sales invoices.
            Use only SQL SELECT queries against ai_sales_invoice_view.
            Never ask for or reference API keys, secrets, or raw JSON payloads.
            Keep queries reasonably scoped and use LIMIT when practical.
            """;
    private static final String PROVIDER_MISSING_MESSAGE = """
            Safe placeholder mode.

            The restricted DatabaseProvider, GridAIController, and ChartAIController are wired,
            but no supported LLM provider is configured for this application yet.

            To enable live AI generation later, add either:
            - a Spring AI ChatModel bean, or
            - a LangChain4j ChatModel bean

            The FireTMS API key is never shared with AI.
            """;

    private final TextArea promptField = new TextArea("Prompt");
    private final Div status = new Div();
    private final Grid<AIDataRow> aiGrid = new Grid<>(AIDataRow.class, false);
    private final Chart aiChart = new Chart();
    private final AIOrchestrator orchestrator;

    public AiDashboardExperimentView(RestrictedAiSalesInvoiceDatabaseProvider databaseProvider,
            ApplicationContext applicationContext) {
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1200px");

        H2 heading = new H2("AI Dashboard Experiment");
        Paragraph description = new Paragraph(
                "This page experiments with Vaadin AI-generated grids and charts over a restricted analytics view.");

        configurePromptField();
        configureGrid();
        configureChart();

        GridAIController gridController = new GridAIController(aiGrid, databaseProvider);
        ChartAIController chartController = new ChartAIController(aiChart, databaseProvider);
        orchestrator = createOrchestrator(applicationContext, new DelegatingAiController(List.of(gridController, chartController)));

        Button generateButton = new Button("Generate", event -> generateDashboard());
        generateButton.setEnabled(orchestrator != null);

        add(
                heading,
                description,
                createPromptExamples(),
                promptField,
                generateButton,
                status,
                createSection("AI Grid", aiGrid),
                createSection("AI Chart", aiChart),
                new Details("Restricted AI schema", new Text(databaseProvider.getSchema())));

        showInitialStatus();
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
        configuration.setSubTitle("Awaiting a configured LLM provider and a prompt.");
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
                Show gross sales by month.
                Show the latest imported invoices.
                Compare gross amount by contractor for the top 10 rows.
                """);
    }

    private void showInitialStatus() {
        if (orchestrator == null) {
            showStatus(PROVIDER_MISSING_MESSAGE);
            return;
        }
        showStatus("""
                AI provider detected.

                Prompts can update the grid and chart with read-only SQL against ai_sales_invoice_view.
                """);
    }

    private void generateDashboard() {
        String prompt = promptField.getValue() == null ? "" : promptField.getValue().trim();
        if (prompt.isEmpty()) {
            showStatus("Enter a prompt to prepare the AI dashboard experiment.");
            return;
        }

        if (orchestrator == null) {
            showStatus(PROVIDER_MISSING_MESSAGE);
            return;
        }

        try {
            showStatus("AI request submitted. The grid and chart will update if the generated SQL passes validation.");
            orchestrator.prompt(prompt);
        } catch (RuntimeException exception) {
            showStatus("AI request could not be started. Check the LLM provider configuration.");
        }
    }

    private AIOrchestrator createOrchestrator(ApplicationContext applicationContext,
            DelegatingAiController controller) {
        Optional<LLMProvider> llmProvider = resolveProvider(applicationContext);
        if (llmProvider.isEmpty()) {
            return null;
        }

        UI ui = UI.getCurrent();
        return AIOrchestrator.builder(llmProvider.get(), SYSTEM_PROMPT)
                .withController(controller)
                .withResponseListener(event -> ui.access(() -> {
                    if (event.getError().isPresent()) {
                        showStatus("AI request failed. The provider or generated SQL did not complete successfully.");
                    } else if (event.getResponse().isBlank()) {
                        showStatus("AI request completed. The grid and chart may have been updated without text output.");
                    } else {
                        showStatus("AI request completed. The grid and chart were updated from the restricted analytics view.");
                    }
                }))
                .build();
    }

    private Optional<LLMProvider> resolveProvider(ApplicationContext applicationContext) {
        Optional<LLMProvider> springAiProvider = instantiateProvider(applicationContext,
                "org.springframework.ai.chat.model.ChatModel",
                "com.vaadin.flow.component.ai.provider.SpringAILLMProvider");
        if (springAiProvider.isPresent()) {
            disableSpringAiStreaming(springAiProvider.get());
            return springAiProvider;
        }

        Optional<LLMProvider> langChainProvider = instantiateProvider(applicationContext,
                "dev.langchain4j.model.chat.ChatModel",
                "com.vaadin.flow.component.ai.provider.LangChain4JLLMProvider");
        if (langChainProvider.isPresent()) {
            return langChainProvider;
        }

        return instantiateProvider(applicationContext,
                "dev.langchain4j.model.chat.StreamingChatModel",
                "com.vaadin.flow.component.ai.provider.LangChain4JLLMProvider");
    }

    private Optional<LLMProvider> instantiateProvider(ApplicationContext applicationContext,
            String modelClassName, String providerClassName) {
        try {
            Class<?> modelClass = Class.forName(modelClassName);
            String[] beanNames = applicationContext.getBeanNamesForType(modelClass);
            if (beanNames.length == 0) {
                return Optional.empty();
            }

            Object modelBean = applicationContext.getBean(beanNames[0], modelClass);
            Class<?> providerClass = Class.forName(providerClassName);
            Constructor<?> constructor = providerClass.getConstructor(modelClass);
            return Optional.of((LLMProvider) constructor.newInstance(modelBean));
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize the Vaadin AI provider.", exception);
        }
    }

    private void disableSpringAiStreaming(LLMProvider provider) {
        try {
            Method setStreaming = provider.getClass().getMethod("setStreaming", boolean.class);
            setStreaming.invoke(provider, false);
        } catch (ReflectiveOperationException ignored) {
            // The provider may not expose streaming controls.
        }
    }

    private void showStatus(String message) {
        status.removeAll();
        status.getStyle().set("white-space", "pre-wrap");
        status.add(new Text(message));
    }
}
