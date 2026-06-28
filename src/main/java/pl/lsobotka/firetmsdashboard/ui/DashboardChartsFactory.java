package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.ChartType;
import com.vaadin.flow.component.charts.model.Configuration;
import com.vaadin.flow.component.charts.model.DataSeries;
import com.vaadin.flow.component.charts.model.DataSeriesItem;
import com.vaadin.flow.component.charts.model.Legend;
import com.vaadin.flow.component.charts.model.ListSeries;
import com.vaadin.flow.component.charts.model.PlotOptionsBar;
import com.vaadin.flow.component.charts.model.PlotOptionsLine;
import com.vaadin.flow.component.charts.model.PlotOptionsPie;
import com.vaadin.flow.component.charts.model.Tooltip;
import com.vaadin.flow.component.charts.model.XAxis;
import com.vaadin.flow.component.charts.model.YAxis;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.ContractorGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.Money;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.MonthlyGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.StatusInvoiceCount;

@org.springframework.stereotype.Component
public class DashboardChartsFactory {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TOP_CONTRACTORS_LIMIT = 10;

    public Component createGrossAmountByMonthChart(List<MonthlyGrossSales> monthlyGrossSales) {
        if (monthlyGrossSales == null || monthlyGrossSales.isEmpty()) {
            return createEmptyState("No sales invoice analytics available.");
        }

        List<MonthlyGrossSales> orderedRows = monthlyGrossSales.stream()
                .sorted(Comparator.comparing(
                        MonthlyGrossSales::month,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String[] categories = orderedRows.stream()
                .map(this::formatMonth)
                .toArray(String[]::new);
        Set<String> currencies = collectCurrenciesFromMonths(orderedRows);
        if (currencies.isEmpty()) {
            return createEmptyState("No gross amounts available for the monthly chart.");
        }

        Chart chart = createChart(ChartType.LINE);
        Configuration configuration = chart.getConfiguration();
        configuration.addxAxis(createCategoryAxis(categories));
        configuration.addyAxis(createValueAxis("Gross amount"));
        configuration.setTooltip(new Tooltip());

        PlotOptionsLine plotOptions = new PlotOptionsLine();
        plotOptions.setAnimation(false);
        configuration.setPlotOptions(plotOptions);

        currencies.forEach(currency -> configuration.addSeries(new ListSeries(
                currency,
                orderedRows.stream()
                        .map(row -> amountForCurrency(row.grossAmounts(), currency))
                        .toArray(Number[]::new))));

        configureLegend(configuration, currencies.size() > 1);
        return chart;
    }

    public Component createGrossAmountByContractorChart(List<ContractorGrossSales> contractorGrossSales) {
        if (contractorGrossSales == null || contractorGrossSales.isEmpty()) {
            return createEmptyState("No contractor analytics available.");
        }

        List<ContractorGrossSales> topContractors = contractorGrossSales.stream()
                .limit(TOP_CONTRACTORS_LIMIT)
                .toList();

        String[] categories = topContractors.stream()
                .map(ContractorGrossSales::contractorName)
                .toArray(String[]::new);
        Set<String> currencies = collectCurrenciesFromContractors(topContractors);
        if (currencies.isEmpty()) {
            return createEmptyState("No gross amounts available for the contractor chart.");
        }

        Chart chart = createChart(ChartType.BAR);
        Configuration configuration = chart.getConfiguration();
        configuration.addxAxis(createCategoryAxis(categories));
        configuration.addyAxis(createValueAxis("Gross amount"));
        configuration.setTooltip(new Tooltip());

        PlotOptionsBar plotOptions = new PlotOptionsBar();
        plotOptions.setAnimation(false);
        configuration.setPlotOptions(plotOptions);

        currencies.forEach(currency -> configuration.addSeries(new ListSeries(
                currency,
                topContractors.stream()
                        .map(row -> amountForCurrency(row.grossAmounts(), currency))
                        .toArray(Number[]::new))));

        configureLegend(configuration, currencies.size() > 1);
        return chart;
    }

    public Component createInvoiceCountByStatusChart(List<StatusInvoiceCount> statusInvoiceCounts) {
        if (statusInvoiceCounts == null || statusInvoiceCounts.isEmpty()) {
            return createEmptyState("No status analytics available.");
        }

        Chart chart = createChart(ChartType.PIE);
        Configuration configuration = chart.getConfiguration();
        Tooltip tooltip = new Tooltip();
        tooltip.setPointFormat("{series.name}: <b>{point.y}</b>");
        configuration.setTooltip(tooltip);

        PlotOptionsPie plotOptions = new PlotOptionsPie();
        plotOptions.setAnimation(false);
        plotOptions.setShowInLegend(true);
        configuration.setPlotOptions(plotOptions);

        DataSeries series = new DataSeries();
        series.setName("Invoices");
        statusInvoiceCounts.forEach(status -> series.add(new DataSeriesItem(status.status(), status.invoiceCount())));
        configuration.addSeries(series);
        configureLegend(configuration, true);
        return chart;
    }

    private Chart createChart(ChartType chartType) {
        Chart chart = new Chart(chartType);
        chart.setWidthFull();
        chart.setHeight("22rem");
        return chart;
    }

    private XAxis createCategoryAxis(String[] categories) {
        XAxis axis = new XAxis();
        axis.setCategories(categories);
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

    private Set<String> collectCurrenciesFromMonths(List<MonthlyGrossSales> rows) {
        return rows.stream()
                .flatMap(row -> row.grossAmounts().stream())
                .map(Money::currency)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private Set<String> collectCurrenciesFromContractors(List<ContractorGrossSales> rows) {
        return rows.stream()
                .flatMap(row -> row.grossAmounts().stream())
                .map(Money::currency)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private double amountForCurrency(List<Money> amounts, String currency) {
        return amounts.stream()
                .filter(amount -> amount.currency().equals(currency))
                .map(Money::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO)
                .doubleValue();
    }

    private String formatMonth(MonthlyGrossSales result) {
        YearMonth month = result.month();
        return month == null ? "Unknown" : month.format(MONTH_FORMATTER);
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
