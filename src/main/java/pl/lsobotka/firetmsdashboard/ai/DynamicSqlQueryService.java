package pl.lsobotka.firetmsdashboard.ai;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicSqlQueryService {

    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int FETCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final SqlSafetyValidator sqlSafetyValidator;

    public DynamicSqlQueryService(JdbcTemplate jdbcTemplate, SqlSafetyValidator sqlSafetyValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSafetyValidator = sqlSafetyValidator;
    }

    @Transactional(readOnly = true)
    public DynamicSqlQueryResult executeValidatedQuery(String sql) {
        String validatedSql = sqlSafetyValidator.validateAndNormalize(sql);
        PreparedStatementCreator statementCreator = connection -> {
            PreparedStatement statement = connection.prepareStatement(validatedSql);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setMaxRows(SqlSafetyValidator.MAX_LIMIT);
            statement.setFetchSize(FETCH_SIZE);
            return statement;
        };
        return jdbcTemplate.execute(statementCreator, this::mapResult);
    }

    private DynamicSqlQueryResult mapResult(PreparedStatement statement) throws SQLException {
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
            return new DynamicSqlQueryResult(List.copyOf(columns), List.copyOf(rows));
        }
    }
}
