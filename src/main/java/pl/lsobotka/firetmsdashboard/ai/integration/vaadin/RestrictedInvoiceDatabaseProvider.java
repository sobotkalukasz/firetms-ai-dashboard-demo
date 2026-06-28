package pl.lsobotka.firetmsdashboard.ai.integration.vaadin;

import com.vaadin.flow.component.ai.provider.DatabaseProvider;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;
import pl.lsobotka.firetmsdashboard.ai.query.SqlSafetyValidator;

@Component
public class RestrictedInvoiceDatabaseProvider implements DatabaseProvider {

    private static final int MAX_ROWS = 500;
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int FETCH_SIZE = 100;
    private static final Pattern LIMIT_WRAPPER = Pattern.compile(
            "(?is)^select\\s+\\*\\s+from\\s*\\((.*)\\)\\s+as\\s+(_[a-z0-9]+)\\s+limit\\s+(\\d+)(\\s+offset\\s+\\d+)?\\s*$");
    private static final Pattern COUNT_WRAPPER = Pattern.compile(
            "(?is)^select\\s+count\\s*\\(\\s*\\*\\s*\\)\\s+from\\s*\\((.*)\\)\\s+as\\s+(_[a-z0-9]+)\\s*$");
    private static final Pattern ORDER_WRAPPER = Pattern.compile(
            "(?is)^select\\s+\\*\\s+from\\s*\\((.*)\\)\\s+as\\s+(_[a-z0-9]+)\\s+order\\s+by\\s+(.+)\\s*$");
    private static final Pattern RESERVED_ALIAS_PATTERN = Pattern.compile("(?i)\\bas\\s+(value|month|year|date)\\b");

    private final JdbcTemplate jdbcTemplate;
    private final SqlSafetyValidator sqlSafetyValidator;

    public RestrictedInvoiceDatabaseProvider(JdbcTemplate jdbcTemplate, SqlSafetyValidator sqlSafetyValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSafetyValidator = sqlSafetyValidator;
    }

    @Override
    public String getSchema() {
        return sqlSafetyValidator.schemaDescription();
    }

    @Override
    public List<Map<String, Object>> executeQuery(String sql) {
        String executableSql = prepareExecutableSql(sql);
        PreparedStatementCreator statementCreator = connection -> {
            PreparedStatement statement = connection.prepareStatement(executableSql);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setMaxRows(MAX_ROWS);
            statement.setFetchSize(FETCH_SIZE);
            return statement;
        };
        try {
            return jdbcTemplate.execute(statementCreator, this::mapResult);
        } catch (DataAccessException exception) {
            throw new IllegalArgumentException(explainExecutionFailure(executableSql, exception), exception);
        }
    }

    String prepareExecutableSql(String sql) {
        return rewriteWrappedQuery(sql == null ? "" : sql.trim());
    }

    String explainExecutionFailure(String sql, Exception exception) {
        String normalizedSql = sql.toLowerCase(Locale.ROOT);
        if (RESERVED_ALIAS_PATTERN.matcher(normalizedSql).find()) {
            return "SQL failed in H2 because it uses a reserved alias. Avoid aliases like value, month, year, or date. "
                    + "Use safe aliases such as metric_value, month_value, label_value, or total_amount instead.";
        }

        String message = exception.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("expected \"identifier\"")) {
            return "SQL failed in H2 with an identifier error. Avoid reserved words as aliases and prefer safe aliases "
                    + "such as metric_value, month_value, label_value, status_label, or total_amount.";
        }
        return message == null ? "SQL execution failed in H2." : message;
    }

    private String rewriteWrappedQuery(String sql) {
        Matcher limitWrapper = LIMIT_WRAPPER.matcher(sql);
        if (limitWrapper.matches()) {
            String inner = rewriteWrappedQuery(limitWrapper.group(1).trim());
            String offset = limitWrapper.group(4) == null ? "" : limitWrapper.group(4);
            return "SELECT * FROM (" + inner + ") AS " + limitWrapper.group(2)
                    + " LIMIT " + limitWrapper.group(3) + offset;
        }

        Matcher countWrapper = COUNT_WRAPPER.matcher(sql);
        if (countWrapper.matches()) {
            String inner = rewriteWrappedQuery(countWrapper.group(1).trim());
            return "SELECT COUNT(*) FROM (" + inner + ") AS " + countWrapper.group(2);
        }

        Matcher orderWrapper = ORDER_WRAPPER.matcher(sql);
        if (orderWrapper.matches()) {
            String inner = rewriteWrappedQuery(orderWrapper.group(1).trim());
            return "SELECT * FROM (" + inner + ") AS " + orderWrapper.group(2)
                    + " ORDER BY " + orderWrapper.group(3).trim();
        }

        return sqlSafetyValidator.validateAndNormalize(sql);
    }

    private List<Map<String, Object>> mapResult(PreparedStatement statement) throws SQLException {
        try (statement; ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(metadata.getColumnLabel(index));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    row.put(columns.get(index - 1), resultSet.getObject(index));
                }
                rows.add(row);
            }
            return List.copyOf(rows);
        }
    }
}
