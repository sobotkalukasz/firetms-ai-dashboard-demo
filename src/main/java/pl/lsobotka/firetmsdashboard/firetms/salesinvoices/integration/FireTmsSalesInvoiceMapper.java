package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FireTmsSalesInvoiceMapper {

    public List<FireTmsSalesInvoiceData> map(JsonNode payload) {
        JsonNode items = payload.path("items");
        if (!items.isArray()) {
            return List.of();
        }

        List<FireTmsSalesInvoiceData> mapped = new ArrayList<>();
        for (JsonNode item : items) {
            FireTmsSalesInvoiceData invoice = mapItem(item);
            if (invoice != null) {
                mapped.add(invoice);
            }
        }
        return mapped;
    }

    private FireTmsSalesInvoiceData mapItem(JsonNode item) {
        String invoiceNumber = readText(item, "documentNumber");
        if (!StringUtils.hasText(invoiceNumber)) {
            return null;
        }

        return new FireTmsSalesInvoiceData(
                readText(item, "documentId"),
                invoiceNumber,
                readDate(item, "issuanceDate"),
                readDate(item, "sellDate"),
                readDate(item, "calculatedPaymentTerm", "paymentDueDate", "paymentDate"),
                readText(item, "client.companyName"),
                readText(item, "ksefNumber.number"),
                readDecimal(item, "totalNet.amount"),
                readDecimal(item, "totalGross.amount"),
                readDecimal(item, "outstandingToPay.amount"),
                readText(item, "totalGross.currencyCode"),
                readText(item, "status"),
                item.toString());
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
        Iterator<String> iterator = List.of(path.split("\\.")).iterator();
        while (iterator.hasNext() && current != null) {
            current = current.path(iterator.next());
        }
        return current == null ? item.path(path) : current;
    }
}
