package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoiceEntity, Long> {

    List<SalesInvoiceEntity> findAllByOrderByIssueDateDescUpdatedAtDesc();

    List<SalesInvoiceEntity> findByInvoiceNumberContainingIgnoreCaseOrContractorNameContainingIgnoreCaseOrderByIssueDateDescUpdatedAtDesc(
            String invoiceNumber, String contractorName);

    Optional<SalesInvoiceEntity> findByExternalId(String externalId);

    Optional<SalesInvoiceEntity> findByInvoiceNumberAndIssueDateAndContractorName(String invoiceNumber, LocalDate issueDate,
            String contractorName);
}
