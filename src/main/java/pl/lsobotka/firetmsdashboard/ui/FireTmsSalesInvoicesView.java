package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.LocalDate;
import pl.lsobotka.firetmsdashboard.firetms.FireTmsClientException;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.FireTmsIssuedSalesInvoicesResponse;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.FireTmsSalesInvoiceClient;

@Route("firetms/sales-invoices")
@PageTitle("FireTMS Sales Invoices")
public class FireTmsSalesInvoicesView extends VerticalLayout {

    static final int MAX_PREVIEW_LENGTH = 4_000;
    static final String VALIDATION_MESSAGE = "Provide API key, date from, and date to.";
    static final String INVALID_RANGE_MESSAGE = "Date from must be on or before date to.";
    static final String GENERIC_ERROR_MESSAGE = "FireTMS request failed. Check the API key, date range, and base URL.";

    private final PasswordField apiKeyField = new PasswordField("API key");
    private final DatePicker dateFromField = new DatePicker("Date from");
    private final DatePicker dateToField = new DatePicker("Date to");
    private final Div status = new Div();
    private final TextArea preview = new TextArea("Technical response preview");

    public FireTmsSalesInvoicesView(FireTmsSalesInvoiceClient client) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setMaxWidth("960px");

        apiKeyField.setWidthFull();
        apiKeyField.setRevealButtonVisible(false);

        dateFromField.setValue(LocalDate.now().minusDays(7));
        dateToField.setValue(LocalDate.now());

        preview.setWidthFull();
        preview.setReadOnly(true);
        preview.setMinHeight("360px");
        preview.setMaxLength(MAX_PREVIEW_LENGTH);

        Button fetchButton = new Button("Fetch issued sales invoices", event -> fetchInvoices(client));

        add(
                new H2("FireTMS sales invoices spike"),
                apiKeyField,
                dateFromField,
                dateToField,
                fetchButton,
                status,
                preview);
    }

    private void fetchInvoices(FireTmsSalesInvoiceClient client) {
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
            FireTmsIssuedSalesInvoicesResponse response = client.fetchIssuedSalesInvoices(apiKey, dateFrom, dateTo);
            showStatus(buildSuccessMessage(response));
            preview.setValue(limitPreview(response.rawJson()));
        } catch (FireTmsClientException exception) {
            showStatus(GENERIC_ERROR_MESSAGE);
            preview.clear();
        }
    }

    private String buildSuccessMessage(FireTmsIssuedSalesInvoicesResponse response) {
        Integer totalItems = response.totalItems();
        Integer returnedItems = response.returnedItems();

        if (totalItems != null && returnedItems != null) {
            return "Request succeeded. Returned " + returnedItems + " items, totalItems=" + totalItems + ".";
        }
        if (returnedItems != null) {
            return "Request succeeded. Returned " + returnedItems + " items.";
        }
        return "Request succeeded.";
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
