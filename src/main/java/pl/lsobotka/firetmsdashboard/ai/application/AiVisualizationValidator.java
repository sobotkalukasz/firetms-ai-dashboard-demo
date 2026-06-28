package pl.lsobotka.firetmsdashboard.ai.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec.VisualizationType;
import pl.lsobotka.firetmsdashboard.ai.query.DynamicSqlQueryResult;

@Service
public class AiVisualizationValidator {

    public AiVisualizationSpec validate(AiVisualizationSpec spec, DynamicSqlQueryResult queryResult) {
        if (spec == null || spec.visualization() == null) {
            throw new AiVisualizationValidationException("Chart configuration is missing.");
        }

        if (spec.visualization() == VisualizationType.TABLE) {
            return new AiVisualizationSpec(VisualizationType.TABLE, null, null, null);
        }

        Map<String, String> columnsByKey = indexColumns(queryResult.columns());
        String xColumn = resolveColumn(spec.xColumn(), columnsByKey, "xColumn");
        String yColumn = resolveColumn(spec.yColumn(), columnsByKey, "yColumn");
        String seriesColumn = resolveOptionalColumn(spec.seriesColumn(), columnsByKey, "seriesColumn");

        ensureNumericColumn(queryResult.rows(), yColumn, spec.visualization());

        if (spec.visualization() == VisualizationType.PIE && !StringUtils.hasText(xColumn)) {
            throw new AiVisualizationValidationException("Pie charts require a category column.");
        }

        return new AiVisualizationSpec(spec.visualization(), xColumn, yColumn, seriesColumn);
    }

    private Map<String, String> indexColumns(List<String> columns) {
        Map<String, String> columnsByKey = new LinkedHashMap<>();
        for (String column : columns) {
            columnsByKey.put(column.toLowerCase(Locale.ROOT), column);
        }
        return columnsByKey;
    }

    private String resolveColumn(String requestedColumn, Map<String, String> columnsByKey, String fieldName) {
        if (!StringUtils.hasText(requestedColumn)) {
            throw new AiVisualizationValidationException("Chart configuration is missing " + fieldName + ".");
        }
        return resolveExistingColumn(requestedColumn, columnsByKey, fieldName);
    }

    private String resolveOptionalColumn(String requestedColumn, Map<String, String> columnsByKey, String fieldName) {
        if (!StringUtils.hasText(requestedColumn)) {
            return null;
        }
        return resolveExistingColumn(requestedColumn, columnsByKey, fieldName);
    }

    private String resolveExistingColumn(String requestedColumn, Map<String, String> columnsByKey, String fieldName) {
        String resolved = columnsByKey.get(requestedColumn.trim().toLowerCase(Locale.ROOT));
        if (resolved == null) {
            throw new AiVisualizationValidationException("Chart configuration references an unknown " + fieldName + ".");
        }
        return resolved;
    }

    private void ensureNumericColumn(
            List<Map<String, Object>> rows,
            String yColumn,
            VisualizationType visualization) {
        for (Map<String, Object> row : rows) {
            Object value = row.get(yColumn);
            if (value == null) {
                continue;
            }
            if (isNumeric(value)) {
                return;
            }
            throw new AiVisualizationValidationException(
                    "Chart configuration requires a numeric yColumn for " + visualization + " charts.");
        }
    }

    private boolean isNumeric(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }
}
