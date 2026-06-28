package pl.lsobotka.firetmsdashboard.firetms.salesinvoices;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SalesInvoiceSyncService {

    private final FireTmsSalesInvoiceClient client;
    private final SalesInvoiceRepository repository;
    private final Clock clock;

    public SalesInvoiceSyncService(FireTmsSalesInvoiceClient client, SalesInvoiceRepository repository) {
        this(client, repository, Clock.systemDefaultZone());
    }

    SalesInvoiceSyncService(FireTmsSalesInvoiceClient client, SalesInvoiceRepository repository, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SalesInvoiceSyncResult syncIssuedSalesInvoices(String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        FireTmsIssuedSalesInvoicesResponse response = client.fetchIssuedSalesInvoices(apiKey, dateFrom, dateTo);
        JsonNode items = response.payload().path("items");

        if (!items.isArray()) {
            return new SalesInvoiceSyncResult(response, 0);
        }

        int persistedInvoices = 0;
        for (JsonNode item : items) {
            SalesInvoice invoice = findExisting(item).orElseGet(SalesInvoice::new);
            if (apply(item, invoice)) {
                repository.save(invoice);
                persistedInvoices++;
            }
        }

        return new SalesInvoiceSyncResult(response, persistedInvoices);
    }

    private Optional<SalesInvoice> findExisting(JsonNode item) {
        String externalId = readText(item, "id", "invoiceId", "uid");
        if (StringUtils.hasText(externalId)) {
            return repository.findByExternalId(externalId);
        }

        String invoiceNumber = readText(item, "fullNumber", "number", "invoiceNumber");
        LocalDate issueDate = readDate(item, "dateOfIssue", "issueDate");
        String contractorName = readText(item, "contractor.name", "contractorName", "buyer.name");

        if (!StringUtils.hasText(invoiceNumber)) {
            return Optional.empty();
        }

        return repository.findByInvoiceNumberAndIssueDateAndContractorName(invoiceNumber, issueDate, contractorName);
    }

    private boolean apply(JsonNode item, SalesInvoice invoice) {
        String invoiceNumber = readText(item, "fullNumber", "number", "invoiceNumber");
        if (!StringUtils.hasText(invoiceNumber)) {
            return false;
        }

        invoice.setExternalId(readText(item, "id", "invoiceId", "uid"));
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setIssueDate(readDate(item, "dateOfIssue", "issueDate"));
        invoice.setSaleDate(readDate(item, "dateOfSale", "saleDate"));
        invoice.setContractorName(readText(item, "contractor.name", "contractorName", "buyer.name"));
        invoice.setNetAmount(readDecimal(item, "netValue", "netAmount", "value.net"));
        invoice.setGrossAmount(readDecimal(item, "grossValue", "grossAmount", "value.gross"));
        invoice.setCurrency(readText(item, "currency", "currencyCode"));
        invoice.setStatus(readText(item, "status", "state"));
        invoice.setRawJson(item.toString());
        invoice.setUpdatedAt(LocalDateTime.now(clock));
        return true;
    }

    private String readText(JsonNode item, String... paths) {
        for (String path : paths) {
            JsonNode node = readPath(item, path);
            if (node.isTextual() && StringUtils.hasText(node.textValue())) {
                return node.textValue();
            }
            if (node.isNumber() || node.isBoolean()) {
                return node.asText();
            }
        }
        return null;
    }

    private BigDecimal readDecimal(JsonNode item, String... paths) {
        for (String path : paths) {
            JsonNode node = readPath(item, path);
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual() && StringUtils.hasText(node.textValue())) {
                try {
                    return new BigDecimal(node.textValue());
                } catch (NumberFormatException ignored) {
                    // Try the next candidate path.
                }
            }
        }
        return null;
    }

    private LocalDate readDate(JsonNode item, String... paths) {
        for (String path : paths) {
            JsonNode node = readPath(item, path);
            if (!node.isValueNode() || !StringUtils.hasText(node.asText())) {
                continue;
            }

            String value = node.asText();
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ignored) {
                try {
                    return OffsetDateTime.parse(value).toLocalDate();
                } catch (DateTimeParseException ignoredAgain) {
                    // Try the next candidate path.
                }
            }
        }
        return null;
    }

    private JsonNode readPath(JsonNode item, String path) {
        JsonNode current = item;
        Iterator<String> iterator = java.util.List.of(path.split("\\.")).iterator();
        while (iterator.hasNext() && current != null) {
            current = current.path(iterator.next());
        }
        return current == null ? item.path(path) : current;
    }
}
