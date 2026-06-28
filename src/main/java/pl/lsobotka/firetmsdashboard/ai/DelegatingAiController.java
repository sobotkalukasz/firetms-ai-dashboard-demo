package pl.lsobotka.firetmsdashboard.ai;

import com.vaadin.flow.component.ai.orchestrator.AIController;
import com.vaadin.flow.component.ai.provider.LLMProvider;
import java.util.ArrayList;
import java.util.List;

public class DelegatingAiController implements AIController {

    private final List<AIController> delegates;

    public DelegatingAiController(List<AIController> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public List<LLMProvider.ToolSpec> getTools() {
        List<LLMProvider.ToolSpec> tools = new ArrayList<>();
        for (AIController delegate : delegates) {
            tools.addAll(delegate.getTools());
        }
        return List.copyOf(tools);
    }

    @Override
    public void onRequest() {
        for (AIController delegate : delegates) {
            delegate.onRequest();
        }
    }

    @Override
    public void onResponse(Throwable error) {
        for (AIController delegate : delegates) {
            delegate.onResponse(error);
        }
    }
}
