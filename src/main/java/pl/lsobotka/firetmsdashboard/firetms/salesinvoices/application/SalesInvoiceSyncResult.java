package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsIssuedSalesInvoicesResponse;

public record SalesInvoiceSyncResult(
        FireTmsIssuedSalesInvoicesResponse response,
        int fetchedInvoices,
        int insertedInvoices,
        int updatedInvoices) {

    public int persistedInvoices() {
        return insertedInvoices + updatedInvoices;
    }

    public String successMessage() {
        Integer totalItems = response.totalItems();
        Integer returnedItems = response.returnedItems();

        if (totalItems != null && returnedItems != null) {
            return "Sync succeeded. Fetched " + fetchedInvoices
                    + ", inserted " + insertedInvoices
                    + ", updated " + updatedInvoices
                    + ". Returned " + returnedItems + " items, totalItems=" + totalItems + ".";
        }
        if (returnedItems != null) {
            return "Sync succeeded. Fetched " + fetchedInvoices
                    + ", inserted " + insertedInvoices
                    + ", updated " + updatedInvoices
                    + ". Returned " + returnedItems + " items.";
        }
        return "Sync succeeded. Fetched " + fetchedInvoices
                + ", inserted " + insertedInvoices
                + ", updated " + updatedInvoices + ".";
    }
}
