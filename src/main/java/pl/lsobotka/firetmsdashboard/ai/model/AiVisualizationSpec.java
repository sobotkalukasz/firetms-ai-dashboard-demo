package pl.lsobotka.firetmsdashboard.ai.model;

public record AiVisualizationSpec(
        VisualizationType visualization,
        String xColumn,
        String yColumn,
        String seriesColumn) {

    public enum VisualizationType {
        TABLE,
        BAR,
        COLUMN,
        LINE,
        PIE
    }
}
