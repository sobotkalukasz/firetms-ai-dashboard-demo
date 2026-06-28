package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceEntity;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.persistence.SalesInvoiceRepository;

@Service
public class SalesInvoiceAnalyticsService {

    static final String UNKNOWN_CONTRACTOR = "Unknown contractor";
    static final String UNKNOWN_STATUS = "Unknown";

    private final SalesInvoiceRepository repository;

    public SalesInvoiceAnalyticsService(SalesInvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SalesInvoiceAnalyticsSnapshot loadAnalytics() {
        List<SalesInvoiceEntity> invoices = repository.findAll();
        return new SalesInvoiceAnalyticsSnapshot(
                aggregateGrossAmountByMonth(invoices),
                aggregateGrossAmountByContractor(invoices),
                aggregateInvoiceCountByStatus(invoices));
    }

    List<MonthlyGrossSales> aggregateGrossAmountByMonth(List<SalesInvoiceEntity> invoices) {
        Map<YearMonth, Map<String, Money>> amountsByMonth = new LinkedHashMap<>();
        for (SalesInvoiceEntity invoice : invoices) {
            YearMonth month = invoice.getIssueDate() == null ? null : YearMonth.from(invoice.getIssueDate());
            amountsByMonth.computeIfAbsent(month, ignored -> new LinkedHashMap<>());
            mergeMoney(amountsByMonth.get(month), toMoney(invoice));
        }

        return amountsByMonth.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, Map<String, Money>>comparingByKey(Comparator.nullsLast(Comparator.reverseOrder())))
                .map(entry -> new MonthlyGrossSales(entry.getKey(), toSortedMoneyList(entry.getValue())))
                .toList();
    }

    List<ContractorGrossSales> aggregateGrossAmountByContractor(List<SalesInvoiceEntity> invoices) {
        Map<String, Map<String, Money>> amountsByContractor = new LinkedHashMap<>();
        for (SalesInvoiceEntity invoice : invoices) {
            String contractorName = normalizeText(invoice.getContractorName(), UNKNOWN_CONTRACTOR);
            amountsByContractor.computeIfAbsent(contractorName, ignored -> new LinkedHashMap<>());
            mergeMoney(amountsByContractor.get(contractorName), toMoney(invoice));
        }

        return amountsByContractor.entrySet().stream()
                .sorted(Map.Entry.<String, Map<String, Money>>comparingByValue(this::compareMoneyTotals)
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new ContractorGrossSales(entry.getKey(), toSortedMoneyList(entry.getValue())))
                .toList();
    }

    List<StatusInvoiceCount> aggregateInvoiceCountByStatus(List<SalesInvoiceEntity> invoices) {
        Map<String, Long> countsByStatus = new LinkedHashMap<>();
        for (SalesInvoiceEntity invoice : invoices) {
            countsByStatus.merge(normalizeText(invoice.getStatus(), UNKNOWN_STATUS), 1L, Long::sum);
        }

        return countsByStatus.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new StatusInvoiceCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private Money toMoney(SalesInvoiceEntity invoice) {
        return new Money(defaultAmount(invoice.getGrossAmount()), invoice.getCurrency());
    }

    private void mergeMoney(Map<String, Money> totals, Money money) {
        totals.merge(money.currency(), money, Money::add);
    }

    private List<Money> toSortedMoneyList(Map<String, Money> totals) {
        return totals.values().stream()
                .sorted(Comparator.comparing(Money::currency))
                .toList();
    }

    private int compareMoneyTotals(Map<String, Money> left, Map<String, Money> right) {
        BigDecimal leftTotal = left.values().stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rightTotal = right.values().stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return rightTotal.compareTo(leftTotal);
    }

    private String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
