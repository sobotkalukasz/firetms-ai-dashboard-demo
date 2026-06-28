package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.ContractorGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.Money;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.MonthlyGrossSales;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsService;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsSnapshot;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.StatusInvoiceCount;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.DASHBOARD_ROUTE, layout = MainView.class)
@RouteAlias(value = "", layout = MainView.class)
@PageTitle("Dashboard")
@AppNavigationItem(section = AppNavigationSection.DASHBOARD, label = "Overview", order = 10)
public class DashboardView extends VerticalLayout {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SalesInvoiceAnalyticsService analyticsService;
    private final DashboardChartsFactory chartsFactory;
    private final Grid<MonthlyGrossSales> monthlyGrid = new Grid<>(MonthlyGrossSales.class, false);
    private final Grid<ContractorGrossSales> contractorGrid = new Grid<>(ContractorGrossSales.class, false);
    private final Grid<StatusInvoiceCount> statusGrid = new Grid<>(StatusInvoiceCount.class, false);
    private final Div monthlyChartContainer = new Div();
    private final Div contractorChartContainer = new Div();
    private final Div statusChartContainer = new Div();

    public DashboardView(SalesInvoiceAnalyticsService analyticsService, DashboardChartsFactory chartsFactory) {
        this.analyticsService = analyticsService;
        this.chartsFactory = chartsFactory;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setWidthFull();

        H2 heading = new H2("Dashboard");
        Button refreshButton = new Button("Refresh", event -> refreshAnalytics());
        HorizontalLayout header = new HorizontalLayout(heading, refreshButton);
        header.setAlignItems(Alignment.END);
        header.setWidthFull();
        header.expand(heading);

        configureMonthlyGrid();
        configureContractorGrid();
        configureStatusGrid();
        configureChartContainer(monthlyChartContainer);
        configureChartContainer(contractorChartContainer);
        configureChartContainer(statusChartContainer);

        add(
                header,
                createSection("Sales invoices: Gross amount by month", monthlyChartContainer, monthlyGrid),
                createSection("Sales invoices: Gross amount by contractor", contractorChartContainer, contractorGrid),
                createSection("Sales invoices: Invoice count by status", statusChartContainer, statusGrid));

        refreshAnalytics();
    }

    private VerticalLayout createSection(String title, Div chartContainer, Grid<?> grid) {
        H3 sectionHeading = new H3(title);
        VerticalLayout section = new VerticalLayout(sectionHeading, chartContainer, grid);
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();
        return section;
    }

    private void configureMonthlyGrid() {
        configureGrid(monthlyGrid, "No sales invoice analytics available.");
        monthlyGrid.addColumn(this::formatMonth).setHeader("Month").setAutoWidth(true).setSortable(true);
        monthlyGrid.addColumn(result -> formatMoney(result.grossAmounts())).setHeader("Gross amount").setAutoWidth(true);
    }

    private void configureContractorGrid() {
        configureGrid(contractorGrid, "No contractor analytics available.");
        contractorGrid.addColumn(ContractorGrossSales::contractorName).setHeader("Contractor").setFlexGrow(1);
        contractorGrid.addColumn(result -> formatMoney(result.grossAmounts())).setHeader("Gross amount").setAutoWidth(true);
    }

    private void configureStatusGrid() {
        configureGrid(statusGrid, "No status analytics available.");
        statusGrid.addColumn(StatusInvoiceCount::status).setHeader("Status").setAutoWidth(true);
        statusGrid.addColumn(StatusInvoiceCount::invoiceCount).setHeader("Invoice count").setAutoWidth(true);
    }

    private void configureGrid(Grid<?> grid, String emptyStateText) {
        grid.setWidthFull();
        grid.setHeight("16rem");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setEmptyStateText(emptyStateText);
    }

    private void configureChartContainer(Div chartContainer) {
        chartContainer.setWidthFull();
    }

    private void refreshAnalytics() {
        SalesInvoiceAnalyticsSnapshot analytics = analyticsService.loadAnalytics();
        monthlyGrid.setItems(analytics.monthlyGrossSales());
        contractorGrid.setItems(analytics.contractorGrossSales());
        statusGrid.setItems(analytics.statusInvoiceCounts());
        monthlyChartContainer.removeAll();
        monthlyChartContainer.add(chartsFactory.createGrossAmountByMonthChart(analytics.monthlyGrossSales()));
        contractorChartContainer.removeAll();
        contractorChartContainer.add(chartsFactory.createGrossAmountByContractorChart(analytics.contractorGrossSales()));
        statusChartContainer.removeAll();
        statusChartContainer.add(chartsFactory.createInvoiceCountByStatusChart(analytics.statusInvoiceCounts()));
    }

    private String formatMonth(MonthlyGrossSales result) {
        YearMonth month = result.month();
        return month == null ? "Unknown" : month.format(MONTH_FORMATTER);
    }

    private String formatMoney(List<Money> amounts) {
        return amounts.stream()
                .map(amount -> amount.amount().toPlainString() + " " + amount.currency())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
