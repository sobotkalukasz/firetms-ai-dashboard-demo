package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import pl.lsobotka.firetmsdashboard.MainView;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsClientException;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.FireTmsIssuedSalesInvoicesResponse;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.SalesInvoiceQueryService;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.SalesInvoiceRow;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.SalesInvoiceSyncResult;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.SalesInvoiceSyncService;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationItem;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

@Route(value = MainView.ISSUED_ROUTE, layout = MainView.class)
@PageTitle("FireTMS Sales Invoices")
@AppNavigationItem(section = AppNavigationSection.INVOICES, label = "Issued", order = 10)
public class FireTmsSalesInvoicesView extends VerticalLayout {

    static final int MAX_PREVIEW_LENGTH = 4_000;
    static final String VALIDATION_MESSAGE = "Provide API key, date from, and date to.";
    static final String INVALID_RANGE_MESSAGE = "Date from must be on or before date to.";
    static final String GENERIC_ERROR_MESSAGE = "FireTMS request failed. Check the API key, date range, and base URL.";

    private final SalesInvoiceSyncService syncService;
    private final SalesInvoiceQueryService queryService;
    private final PasswordField apiKeyField = new PasswordField("API key");
    private final DatePicker dateFromField = new DatePicker("Date from");
    private final DatePicker dateToField = new DatePicker("Date to");
    private final TextField filterField = new TextField("Filter");
    private final Div status = new Div();
    private final Grid<SalesInvoiceRow> invoicesGrid = new Grid<>(SalesInvoiceRow.class, false);
    private final TextArea preview = new TextArea("Technical response preview");

    public FireTmsSalesInvoicesView(SalesInvoiceSyncService syncService, SalesInvoiceQueryService queryService) {
        this.syncService = syncService;
        this.queryService = queryService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1400px");

        apiKeyField.setWidthFull();
        apiKeyField.setRevealButtonVisible(false);

        dateFromField.setValue(LocalDate.now().minusDays(7));
        dateToField.setValue(LocalDate.now());

        filterField.setPlaceholder("Invoice number or contractor name");
        filterField.setClearButtonVisible(true);
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.addValueChangeListener(event -> refreshGrid());

        configureGrid();

        preview.setWidthFull();
        preview.setReadOnly(true);
        preview.setMinHeight("220px");
        preview.setMaxLength(MAX_PREVIEW_LENGTH);

        Button fetchButton = new Button("Fetch issued sales invoices", event -> fetchInvoices());
        Button refreshButton = new Button("Refresh from database", event -> refreshGrid());

        HorizontalLayout controls = new HorizontalLayout(apiKeyField, dateFromField, dateToField, fetchButton);
        controls.setAlignItems(Alignment.END);
        controls.setWidthFull();
        controls.expand(apiKeyField);

        HorizontalLayout gridActions = new HorizontalLayout(filterField, refreshButton);
        gridActions.setAlignItems(Alignment.END);
        gridActions.setWidthFull();
        gridActions.expand(filterField);

        Details previewDetails = new Details("Technical response preview", preview);
        previewDetails.setOpened(false);
        previewDetails.setWidthFull();

        add(controls, status, gridActions, invoicesGrid, previewDetails);
        refreshGrid();
    }

    private void configureGrid() {
        invoicesGrid.setWidthFull();
        invoicesGrid.setHeight("28rem");
        invoicesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        invoicesGrid.addColumn(SalesInvoiceRow::invoiceNumber).setHeader("Invoice number").setAutoWidth(true).setSortable(true);
        invoicesGrid.addColumn(SalesInvoiceRow::issueDate).setHeader("Issue date").setAutoWidth(true).setSortable(true);
        invoicesGrid.addColumn(SalesInvoiceRow::saleDate).setHeader("Sale date").setAutoWidth(true).setSortable(true);
        invoicesGrid.addColumn(SalesInvoiceRow::contractorName).setHeader("Contractor name").setAutoWidth(true).setFlexGrow(1);
        invoicesGrid.addColumn(SalesInvoiceRow::netAmount).setHeader("Net amount").setAutoWidth(true);
        invoicesGrid.addColumn(SalesInvoiceRow::grossAmount).setHeader("Gross amount").setAutoWidth(true);
        invoicesGrid.addColumn(SalesInvoiceRow::currency).setHeader("Currency").setAutoWidth(true);
        invoicesGrid.addColumn(SalesInvoiceRow::status).setHeader("Status").setAutoWidth(true);
        invoicesGrid.addColumn(invoice -> formatUpdatedAt(invoice)).setHeader("Updated at").setAutoWidth(true).setSortable(true);
        invoicesGrid.setEmptyStateText("No persisted sales invoices found.");
    }

    private void fetchInvoices() {
        String apiKey = apiKeyField.getValue();
        LocalDate dateFrom = dateFromField.getValue();
        LocalDate dateTo = dateToField.getValue();

        if (isBlank(apiKey) || dateFrom == null || dateTo == null) {
            showStatus(VALIDATION_MESSAGE);
            preview.clear();
            return;
        }

        if (dateFrom.isAfter(dateTo)) {
            showStatus(INVALID_RANGE_MESSAGE);
            preview.clear();
            return;
        }

        try {
            SalesInvoiceSyncResult result = syncService.syncIssuedSalesInvoices(apiKey, dateFrom, dateTo);
            FireTmsIssuedSalesInvoicesResponse response = result.response();
            showStatus(buildSuccessMessage(response, result.persistedInvoices()));
            preview.setValue(limitPreview(response.rawJson()));
            refreshGrid();
        } catch (FireTmsClientException exception) {
            showStatus(GENERIC_ERROR_MESSAGE);
            preview.clear();
        }
    }

    private void refreshGrid() {
        List<SalesInvoiceRow> rows = queryService.findSalesInvoices(filterField.getValue());
        invoicesGrid.setItems(rows);
    }

    private String buildSuccessMessage(FireTmsIssuedSalesInvoicesResponse response, int persistedInvoices) {
        Integer totalItems = response.totalItems();
        Integer returnedItems = response.returnedItems();

        if (totalItems != null && returnedItems != null) {
            return "Sync succeeded. Persisted " + persistedInvoices + " invoices. Returned "
                    + returnedItems + " items, totalItems=" + totalItems + ".";
        }
        if (returnedItems != null) {
            return "Sync succeeded. Persisted " + persistedInvoices + " invoices. Returned " + returnedItems + " items.";
        }
        return "Sync succeeded. Persisted " + persistedInvoices + " invoices.";
    }

    private void showStatus(String message) {
        status.removeAll();
        status.add(new Text(message));
    }

    private String limitPreview(String rawJson) {
        if (rawJson.length() <= MAX_PREVIEW_LENGTH) {
            return rawJson;
        }
        return rawJson.substring(0, MAX_PREVIEW_LENGTH) + "\n... [truncated]";
    }

    private String formatUpdatedAt(SalesInvoiceRow invoice) {
        return invoice.updatedAt() == null
                ? ""
                : invoice.updatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
