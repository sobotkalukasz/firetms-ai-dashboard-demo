package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Legend;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.Tooltip;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import pl.lsobotka.firetmsdashboard.ai.AiVisualizationSpec;
import pl.lsobotka.firetmsdashboard.ai.DynamicSqlQueryResult;

@org.springframework.stereotype.Component
public class AiVisualizationChartFactory {

    public Component createChart(String title, AiVisualizationSpec spec, DynamicSqlQueryResult queryResult) {
        return switch (spec.visualization()) {
            case BAR -> createCategoryChart(ChartType.BAR, title, spec, queryResult);
            case COLUMN -> createCategoryChart(ChartType.COLUMN, title, spec, queryResult);
            case LINE -> createCategoryChart(ChartType.LINE, title, spec, queryResult);
            case PIE -> createPieChart(title, spec, queryResult);
            case TABLE -> createEmptyState("Table visualization does not render a chart.");
        };
    }

    private Component createCategoryChart(
            ChartType chartType,
            String title,
            AiVisualizationSpec spec,
            DynamicSqlQueryResult queryResult) {
        Chart chart = createBaseChart(chartType, title);
        Configuration configuration = chart.getConfiguration();
        configuration.addxAxis(createCategoryAxis(categories(spec.xColumn(), queryResult.rows())));
        configuration.addyAxis(createValueAxis(spec.yColumn()));
        configuration.setTooltip(new Tooltip());

        Map<String, Map<String, Number>> valuesBySeries = valuesBySeries(spec, queryResult.rows());
        valuesBySeries.forEach((seriesName, valuesByCategory) -> configuration.addSeries(new ListSeries(
                seriesName,
                categories(spec.xColumn(), queryResult.rows()).stream()
                        .map(valuesByCategory::get)
                        .toArray(Number[]::new))));

        configureLegend(configuration, valuesBySeries.size() > 1);
        return chart;
    }

    private Component createPieChart(String title, AiVisualizationSpec spec, DynamicSqlQueryResult queryResult) {
        Chart chart = createBaseChart(ChartType.PIE, title);
        Configuration configuration = chart.getConfiguration();
        Tooltip tooltip = new Tooltip();
        tooltip.setPointFormat("{series.name}: <b>{point.y}</b>");
        configuration.setTooltip(tooltip);

        DataSeries series = new DataSeries();
        series.setName(spec.yColumn());
        queryResult.rows().forEach(row -> series.add(new DataSeriesItem(
                formatValue(row.get(spec.xColumn())),
                toNumber(row.get(spec.yColumn())))));
        configuration.addSeries(series);
        configureLegend(configuration, true);
        return chart;
    }

    private Chart createBaseChart(ChartType chartType, String title) {
        Chart chart = new Chart(chartType);
        chart.setWidthFull();
        chart.setHeight("22rem");
        chart.getConfiguration().setTitle(title);
        return chart;
    }

    private XAxis createCategoryAxis(List<String> categories) {
        XAxis axis = new XAxis();
        axis.setCategories(categories.toArray(String[]::new));
        return axis;
    }

    private YAxis createValueAxis(String title) {
        YAxis axis = new YAxis();
        axis.setTitle(title);
        return axis;
    }

    private void configureLegend(Configuration configuration, boolean enabled) {
        Legend legend = configuration.getLegend();
        legend.setEnabled(enabled);
    }

    private List<String> categories(String xColumn, List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> formatValue(row.get(xColumn)))
                .distinct()
                .toList();
    }

    private Map<String, Map<String, Number>> valuesBySeries(
            AiVisualizationSpec spec,
            List<Map<String, Object>> rows) {
        if (spec.seriesColumn() == null) {
            Map<String, Number> values = new LinkedHashMap<>();
            rows.forEach(row -> values.put(formatValue(row.get(spec.xColumn())), toNumber(row.get(spec.yColumn()))));
            return Map.of(spec.yColumn(), values);
        }

        Set<String> orderedSeriesNames = rows.stream()
                .map(row -> formatValue(row.get(spec.seriesColumn())))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        Map<String, Map<String, Number>> valuesBySeries = new LinkedHashMap<>();
        for (String seriesName : orderedSeriesNames) {
            valuesBySeries.put(seriesName, new LinkedHashMap<>());
        }
        rows.forEach(row -> valuesBySeries.get(formatValue(row.get(spec.seriesColumn())))
                .put(formatValue(row.get(spec.xColumn())), toNumber(row.get(spec.yColumn()))));
        return valuesBySeries;
    }

    private Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return 0;
    }

    private String formatValue(Object value) {
        return Objects.toString(value, "");
    }

    private Component createEmptyState(String text) {
        Paragraph message = new Paragraph(text);
        message.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Div wrapper = new Div(message);
        wrapper.setWidthFull();
        wrapper.setHeight("22rem");
        wrapper.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("border", "1px dashed var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("box-sizing", "border-box");
        return wrapper;
    }
}
