package pl.lsobotka.firetmsdashboard.ai;

import com.vaadin.flow.component.ai.provider.DatabaseProvider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class RestrictedAiSalesInvoiceDatabaseProvider implements DatabaseProvider {

    static final int MAX_ROWS = 500;
    static final String ALLOWED_VIEW = "ai_sales_invoice_view";
    private static final Pattern WRITE_OR_DDL_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|merge|drop|alter|create|truncate|grant|revoke|call|execute|exec)\\b");
    private static final Pattern FORBIDDEN_COLUMN_PATTERN = Pattern.compile("\\braw_json\\b");
    private static final Pattern FORBIDDEN_TABLE_PATTERN = Pattern.compile(
            "\\b(sales_invoice|flyway_schema_history|app_metadata|information_schema)\\b");
    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "\\b(from|join)\\s+([a-zA-Z0-9_\\.]+)\\b");
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
            Query rules:
            - SELECT statements only
            - query ai_sales_invoice_view only
            - do not reference raw_json, secrets, or underlying tables
            - keep result sets reasonably small; use LIMIT when practical
            """;

    private final DataSource dataSource;

    public RestrictedAiSalesInvoiceDatabaseProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSchema() {
        return SCHEMA;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String sql) {
        validateSql(sql);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setReadOnly(true);
            statement.setMaxRows(MAX_ROWS);
            statement.setFetchSize(MAX_ROWS);

            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalArgumentException("Read-only AI SQL execution failed.", exception);
        }
    }

    void validateSql(String sql) {
        Objects.requireNonNull(sql, "SQL query must not be null");

        String normalized = normalize(sql);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("SQL query must not be blank.");
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("Only a single SQL statement is allowed.");
        }
        if (normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new IllegalArgumentException("SQL comments are not allowed.");
        }
        if (!normalized.startsWith("select ")) {
            throw new IllegalArgumentException("Only SELECT statements are allowed.");
        }
        if (WRITE_OR_DDL_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException("Write and DDL statements are not allowed.");
        }
        if (FORBIDDEN_COLUMN_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException("The raw_json column is not available to AI queries.");
        }
        if (!normalized.contains(ALLOWED_VIEW)) {
            throw new IllegalArgumentException("AI queries must use ai_sales_invoice_view.");
        }
        if (FORBIDDEN_TABLE_PATTERN.matcher(normalized).find()
                && !normalized.contains(ALLOWED_VIEW)) {
            throw new IllegalArgumentException("Underlying tables are not available to AI queries.");
        }

        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String tableName = matcher.group(2);
            if (!ALLOWED_VIEW.equals(tableName) && !"_v".equals(tableName)) {
                throw new IllegalArgumentException("AI queries may only read from ai_sales_invoice_view.");
            }
        }
    }

    private List<Map<String, Object>> mapRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= columnCount; index++) {
                row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
            }
            rows.add(row);
        }
        return rows;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
