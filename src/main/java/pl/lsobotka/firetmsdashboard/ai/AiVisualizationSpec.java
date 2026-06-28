package pl.lsobotka.firetmsdashboard.ai;

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
