package pl.lsobotka.firetmsdashboard.ai;

public record AiQueryGenerationResult(
        String sql,
        String title,
        String explanation,
        AiVisualizationSpec visualizationSpec) {
}
