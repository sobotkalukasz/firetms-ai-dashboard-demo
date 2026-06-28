package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    List<SalesInvoice> findAllByOrderByIssueDateDescUpdatedAtDesc();

    List<SalesInvoice> findByInvoiceNumberContainingIgnoreCaseOrContractorNameContainingIgnoreCaseOrderByIssueDateDescUpdatedAtDesc(
            String invoiceNumber, String contractorName);

    Optional<SalesInvoice> findByExternalId(String externalId);

    Optional<SalesInvoice> findByInvoiceNumberAndIssueDateAndContractorName(String invoiceNumber, LocalDate issueDate,
            String contractorName);
}
