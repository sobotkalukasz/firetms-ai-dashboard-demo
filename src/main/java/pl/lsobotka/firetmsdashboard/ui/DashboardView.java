package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.button.Button;
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
    private final Grid<MonthlyGrossSales> monthlyGrid = new Grid<>(MonthlyGrossSales.class, false);
    private final Grid<ContractorGrossSales> contractorGrid = new Grid<>(ContractorGrossSales.class, false);
    private final Grid<StatusInvoiceCount> statusGrid = new Grid<>(StatusInvoiceCount.class, false);

    public DashboardView(SalesInvoiceAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1400px");

        H2 heading = new H2("Dashboard");
        Button refreshButton = new Button("Refresh", event -> refreshAnalytics());
        HorizontalLayout header = new HorizontalLayout(heading, refreshButton);
        header.setAlignItems(Alignment.END);

        configureMonthlyGrid();
        configureContractorGrid();
        configureStatusGrid();

        add(
                header,
                createSection("Sales invoices: Gross amount by month", monthlyGrid),
                createSection("Sales invoices: Gross amount by contractor", contractorGrid),
                createSection("Sales invoices: Invoice count by status", statusGrid));

        refreshAnalytics();
    }

    private VerticalLayout createSection(String title, Grid<?> grid) {
        H3 sectionHeading = new H3(title);
        VerticalLayout section = new VerticalLayout(sectionHeading, grid);
        section.setPadding(false);
        section.setSpacing(true);
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

    private void refreshAnalytics() {
        SalesInvoiceAnalyticsSnapshot analytics = analyticsService.loadAnalytics();
        monthlyGrid.setItems(analytics.monthlyGrossSales());
        contractorGrid.setItems(analytics.contractorGrossSales());
        statusGrid.setItems(analytics.statusInvoiceCounts());
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
