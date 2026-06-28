package pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

    public static final String UNKNOWN_CURRENCY = "Unknown";

    public Money {
        amount = amount == null ? BigDecimal.ZERO : amount;
        currency = normalizeCurrency(currency);
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency())) {
            throw new IllegalArgumentException("Cannot add money with different currencies.");
        }
        return new Money(amount.add(other.amount()), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? UNKNOWN_CURRENCY : currency.trim();
    }
}
