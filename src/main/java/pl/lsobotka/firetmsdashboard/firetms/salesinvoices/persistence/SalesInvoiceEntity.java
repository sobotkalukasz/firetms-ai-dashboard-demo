package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_invoice")
public class SalesInvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "sale_date")
    private LocalDate saleDate;

    @Column(name = "contractor_name")
    private String contractorName;

    @Column(name = "ksef_number")
    private String ksefNumber;

    @Column(name = "net_amount", precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "gross_amount", precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "outstanding_to_pay", precision = 19, scale = 2)
    private BigDecimal outstandingToPay;

    @Column(name = "currency")
    private String currency;

    @Column(name = "status")
    private String status;

    @Lob
    @Column(name = "raw_json")
    private String rawJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
    }

    public String getContractorName() {
        return contractorName;
    }

    public void setContractorName(String contractorName) {
        this.contractorName = contractorName;
    }

    public String getKsefNumber() {
        return ksefNumber;
    }

    public void setKsefNumber(String ksefNumber) {
        this.ksefNumber = ksefNumber;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getOutstandingToPay() {
        return outstandingToPay;
    }

    public void setOutstandingToPay(BigDecimal outstandingToPay) {
        this.outstandingToPay = outstandingToPay;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
