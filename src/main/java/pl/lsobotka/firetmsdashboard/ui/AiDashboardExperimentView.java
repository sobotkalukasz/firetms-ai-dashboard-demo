package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.AI_DASHBOARD_ROUTE, layout = MainView.class)
@PageTitle("AI Dashboard Experiment")
@AppNavigationItem(section = AppNavigationSection.EXPERIMENTS, label = "AI Dashboard", order = 10)
public class AiDashboardExperimentView extends VerticalLayout {

    private static final String PLACEHOLDER_MESSAGE = """
            Experimental placeholder only.

            The next step is to connect Vaadin AI-generated Grid/Chart components to a strictly read-only analytics source.
            Safety constraints for that integration:
            - never pass the FireTMS API key to AI services
            - never expose raw_json to generated queries or generated UI
            - allow only read-only invoice analytics access
            - prefer a dedicated analytics view/service instead of full-table access
            """;

    private final TextArea promptField = new TextArea("Prompt");
    private final Div result = new Div();

    public AiDashboardExperimentView() {
        setPadding(true);
        setSpacing(true);
        setMaxWidth("960px");

        H2 heading = new H2("AI Dashboard Experiment");
        Paragraph description = new Paragraph(
                "This page will be used to experiment with natural-language generated grids and charts.");

        configurePromptField();
        configureResult();

        Button generateButton = new Button("Generate", event -> showPlaceholderResult());

        add(heading, description, promptField, generateButton, result);
    }

    private void configurePromptField() {
        promptField.setWidthFull();
        promptField.setMinHeight("180px");
        promptField.setPlaceholder("""
                Show gross sales by month
                Show top 10 contractors by gross amount
                Show invoice count by status
                """);
        promptField.setHelperText("Read-only experiment. No LLM call is performed yet.");
    }

    private void configureResult() {
        result.setWidthFull();
        result.getStyle().set("white-space", "pre-wrap");
    }

    private void showPlaceholderResult() {
        result.removeAll();

        String prompt = promptField.getValue() == null ? "" : promptField.getValue().trim();
        if (prompt.isEmpty()) {
            result.add(new Text("Enter a prompt to prepare the AI dashboard experiment."));
            return;
        }

        result.add(new Text("Prompt received:\n" + prompt + "\n\n" + PLACEHOLDER_MESSAGE));
    }
}
