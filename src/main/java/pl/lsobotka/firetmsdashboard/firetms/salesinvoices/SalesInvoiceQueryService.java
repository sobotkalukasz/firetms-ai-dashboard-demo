package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SalesInvoiceQueryService {

    private final SalesInvoiceRepository repository;

    public SalesInvoiceQueryService(SalesInvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SalesInvoiceRow> findSalesInvoices(String filterText) {
        List<SalesInvoice> invoices = StringUtils.hasText(filterText)
                ? repository.findByInvoiceNumberContainingIgnoreCaseOrContractorNameContainingIgnoreCaseOrderByIssueDateDescUpdatedAtDesc(
                        filterText.trim(), filterText.trim())
                : repository.findAllByOrderByIssueDateDescUpdatedAtDesc();

        return invoices.stream().map(SalesInvoiceRow::from).toList();
    }
}
