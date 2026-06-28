package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsIssuedSalesInvoicesResponse;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsSalesInvoiceData;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.FireTmsSalesInvoiceMapper;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration.IssuedSalesInvoicesGateway;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceEntity;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceRepository;

@Service
public class SalesInvoiceSyncService {

    private static final Logger log = LoggerFactory.getLogger(SalesInvoiceSyncService.class);

    private final IssuedSalesInvoicesGateway gateway;
    private final FireTmsSalesInvoiceMapper mapper;
    private final SalesInvoiceRepository repository;
    private final Clock clock;

    @Autowired
    public SalesInvoiceSyncService(IssuedSalesInvoicesGateway gateway, FireTmsSalesInvoiceMapper mapper,
            SalesInvoiceRepository repository) {
        this(gateway, mapper, repository, Clock.systemDefaultZone());
    }

    SalesInvoiceSyncService(IssuedSalesInvoicesGateway gateway, FireTmsSalesInvoiceMapper mapper,
            SalesInvoiceRepository repository, Clock clock) {
        this.gateway = gateway;
        this.mapper = mapper;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SalesInvoiceSyncResult syncIssuedSalesInvoices(String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        FireTmsIssuedSalesInvoicesResponse response = gateway.fetchIssuedSalesInvoices(apiKey, dateFrom, dateTo);
        int fetchedInvoices = response.returnedItems() != null ? response.returnedItems() : 0;
        List<FireTmsSalesInvoiceData> invoices = mapper.map(response.payload());

        if (invoices.isEmpty() && fetchedInvoices == 0) {
            SalesInvoiceSyncResult result = new SalesInvoiceSyncResult(response, 0, 0, 0);
            logSummary(result);
            return result;
        }

        int insertedInvoices = 0;
        int updatedInvoices = 0;
        for (FireTmsSalesInvoiceData imported : invoices) {
            Optional<SalesInvoiceEntity> existing = findExisting(imported);
            SalesInvoiceEntity invoice = existing.orElseGet(SalesInvoiceEntity::new);
            apply(imported, invoice);
            repository.save(invoice);
            if (existing.isPresent()) {
                updatedInvoices++;
            } else {
                insertedInvoices++;
            }
        }

        SalesInvoiceSyncResult result = new SalesInvoiceSyncResult(response, fetchedInvoices, insertedInvoices,
                updatedInvoices);
        logSummary(result);
        return result;
    }

    private Optional<SalesInvoiceEntity> findExisting(FireTmsSalesInvoiceData imported) {
        String externalId = imported.externalId();
        if (StringUtils.hasText(externalId)) {
            return repository.findByExternalId(externalId);
        }

        String invoiceNumber = imported.invoiceNumber();
        LocalDate issueDate = imported.issueDate();
        String contractorName = imported.contractorName();

        if (!StringUtils.hasText(invoiceNumber)) {
            return Optional.empty();
        }

        return repository.findByInvoiceNumberAndIssueDateAndContractorName(invoiceNumber, issueDate, contractorName);
    }

    private void apply(FireTmsSalesInvoiceData imported, SalesInvoiceEntity invoice) {
        invoice.setExternalId(imported.externalId());
        invoice.setInvoiceNumber(imported.invoiceNumber());
        invoice.setIssueDate(imported.issueDate());
        invoice.setSaleDate(imported.saleDate());
        invoice.setContractorName(imported.contractorName());
        invoice.setNetAmount(imported.netAmount());
        invoice.setGrossAmount(imported.grossAmount());
        invoice.setCurrency(imported.currency());
        invoice.setStatus(imported.status());
        invoice.setRawJson(imported.rawJson());
        invoice.setUpdatedAt(LocalDateTime.now(clock));
    }

    private void logSummary(SalesInvoiceSyncResult result) {
        log.info("Sales invoices sync finished. Fetched: {}, Inserted: {}, Updated: {}", result.fetchedInvoices(),
                result.insertedInvoices(), result.updatedInvoices());
    }
}
