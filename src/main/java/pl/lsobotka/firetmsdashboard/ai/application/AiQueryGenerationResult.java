package pl.lsobotka.firetmsdashboard.ai.application;

import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec;

public record AiQueryGenerationResult(
        String sql,
        String title,
        String explanation,
        AiVisualizationSpec visualizationSpec) {
}
