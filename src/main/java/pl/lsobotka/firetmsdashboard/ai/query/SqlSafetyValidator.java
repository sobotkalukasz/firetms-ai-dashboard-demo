package pl.lsobotka.firetmsdashboard.ai.query;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SqlSafetyValidator {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;
    static final String ALLOWED_VIEW = "ai_sales_invoice_view";

    private static final Pattern COMMENTS_PATTERN = Pattern.compile("(?s)(--|/\\*|\\*/)");
    private static final Pattern WRITE_OR_DDL_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|merge|drop|alter|create|truncate|grant|revoke|call|execute|exec)\\b");
    private static final Pattern FORBIDDEN_REFERENCE_PATTERN = Pattern.compile(
            "\\b(sales_invoice|raw_json|app_metadata|information_schema|flyway_schema_history)\\b");
    private static final Pattern SELECT_PATTERN = Pattern.compile("^select\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "\\b(from|join)\\s+([a-zA-Z0-9_\\.]+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final String SCHEMA = """
            SQL dialect: H2.
            Available read-only view:
            - ai_sales_invoice_view(
                firetms_id VARCHAR,
                invoice_number VARCHAR,
                issue_date DATE,
                sale_date DATE,
                payment_due_date DATE,
                contractor_name VARCHAR,
                net_amount DECIMAL,
                gross_amount DECIMAL,
                currency VARCHAR,
                status VARCHAR,
                imported_at TIMESTAMP,
                updated_at TIMESTAMP
              )
            SQL generation rules:
            - generate exactly one SELECT statement
            - query ai_sales_invoice_view only
            - use only the listed columns
            - never query sales_invoice
            - never query raw_json
            - do not use write or DDL statements
            - do not use SQL comments
            - always include LIMIT
            - keep titles short and explanations concise
            """;

    public String schemaDescription() {
        return SCHEMA;
    }

    public String validateAndNormalize(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new SqlValidationException("Generated SQL was empty.");
        }

        String trimmed = sql.trim();
        if (COMMENTS_PATTERN.matcher(trimmed).find()) {
            throw new SqlValidationException("Generated SQL contains comments, which are not allowed.");
        }
        if (trimmed.contains(";")) {
            throw new SqlValidationException("Generated SQL must contain exactly one statement.");
        }

        String normalized = normalize(trimmed);
        if (!SELECT_PATTERN.matcher(normalized).find()) {
            throw new SqlValidationException("Only SELECT statements are allowed.");
        }
        if (WRITE_OR_DDL_PATTERN.matcher(normalized).find()) {
            throw new SqlValidationException("Write or DDL statements are not allowed.");
        }
        if (FORBIDDEN_REFERENCE_PATTERN.matcher(normalized).find()) {
            throw new SqlValidationException("Generated SQL references a forbidden table or column.");
        }
        if (!normalized.contains(ALLOWED_VIEW)) {
            throw new SqlValidationException("Generated SQL must query ai_sales_invoice_view.");
        }

        Set<String> referencedTables = extractReferencedTables(normalized);
        if (referencedTables.isEmpty()) {
            throw new SqlValidationException("Generated SQL must read from ai_sales_invoice_view.");
        }
        for (String referencedTable : referencedTables) {
            if (!ALLOWED_VIEW.equals(referencedTable)) {
                throw new SqlValidationException("Generated SQL may read only from ai_sales_invoice_view.");
            }
        }

        return enforceLimit(trimmed);
    }

    private Set<String> extractReferencedTables(String sql) {
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
        Set<String> referencedTables = new LinkedHashSet<>();
        while (matcher.find()) {
            referencedTables.add(matcher.group(2).toLowerCase(Locale.ROOT));
        }
        return referencedTables;
    }

    private String enforceLimit(String sql) {
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql + " limit " + DEFAULT_LIMIT;
        }

        int limit = Integer.parseInt(matcher.group(1));
        if (limit <= MAX_LIMIT) {
            return sql;
        }
        return matcher.replaceFirst("limit " + MAX_LIMIT);
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
