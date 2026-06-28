package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

public record SalesInvoiceSyncResult(FireTmsIssuedSalesInvoicesResponse response, int persistedInvoices) {
}
