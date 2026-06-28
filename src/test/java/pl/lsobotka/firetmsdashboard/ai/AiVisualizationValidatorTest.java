package pl.lsobotka.firetmsdashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.lsobotka.firetmsdashboard.ai.application.AiVisualizationValidationException;
import pl.lsobotka.firetmsdashboard.ai.application.AiVisualizationValidator;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec;
import pl.lsobotka.firetmsdashboard.ai.model.AiVisualizationSpec.VisualizationType;
import pl.lsobotka.firetmsdashboard.ai.query.DynamicSqlQueryResult;

class AiVisualizationValidatorTest {

    private final AiVisualizationValidator validator = new AiVisualizationValidator();

    @Test
    void acceptsTableVisualizationWithoutColumns() {
        AiVisualizationSpec validated = validator.validate(
                new AiVisualizationSpec(VisualizationType.TABLE, null, null, null),
                new DynamicSqlQueryResult(List.of("invoice_number"), List.of()));

        assertThat(validated.visualization()).isEqualTo(VisualizationType.TABLE);
        assertThat(validated.xColumn()).isNull();
        assertThat(validated.yColumn()).isNull();
    }

    @Test
    void resolvesColumnsCaseInsensitivelyForCharts() {
        DynamicSqlQueryResult queryResult = new DynamicSqlQueryResult(
                List.of("month_value", "gross_sales"),
                List.of(Map.of("month_value", "2026-06", "gross_sales", BigDecimal.TEN)));

        AiVisualizationSpec validated = validator.validate(
                new AiVisualizationSpec(VisualizationType.LINE, "MONTH_VALUE", "GROSS_SALES", null),
                queryResult);

        assertThat(validated.xColumn()).isEqualTo("month_value");
        assertThat(validated.yColumn()).isEqualTo("gross_sales");
    }

    @Test
    void rejectsUnknownChartColumns() {
        DynamicSqlQueryResult queryResult = new DynamicSqlQueryResult(
                List.of("status", "invoice_count"),
                List.of(Map.of("status", "PAID", "invoice_count", 2)));

        assertThatThrownBy(() -> validator.validate(
                new AiVisualizationSpec(VisualizationType.BAR, "status", "gross_sales", null),
                queryResult))
                .isInstanceOf(AiVisualizationValidationException.class)
                .hasMessage("Chart configuration references an unknown yColumn.");
    }

    @Test
    void rejectsNonNumericYColumnForCategoryCharts() {
        DynamicSqlQueryResult queryResult = new DynamicSqlQueryResult(
                List.of("status", "label_value"),
                List.of(Map.of("status", "PAID", "label_value", "two")));

        assertThatThrownBy(() -> validator.validate(
                new AiVisualizationSpec(VisualizationType.COLUMN, "status", "label_value", null),
                queryResult))
                .isInstanceOf(AiVisualizationValidationException.class)
                .hasMessage("Chart configuration requires a numeric yColumn for COLUMN charts.");
    }

    @Test
    void rejectsPieChartsWithoutCategoryColumn() {
        DynamicSqlQueryResult queryResult = new DynamicSqlQueryResult(
                List.of("invoice_count"),
                List.of(Map.of("invoice_count", 3)));

        assertThatThrownBy(() -> validator.validate(
                new AiVisualizationSpec(VisualizationType.PIE, null, "invoice_count", null),
                queryResult))
                .isInstanceOf(AiVisualizationValidationException.class)
                .hasMessage("Chart configuration is missing xColumn.");
    }

    @Test
    void allowsNullNumericValuesWhenColumnTypeIsOtherwiseNumeric() {
        DynamicSqlQueryResult queryResult = new DynamicSqlQueryResult(
                List.of("status", "gross_sales"),
                List.of(
                        Map.of("status", "PAID", "gross_sales", BigDecimal.ONE),
                        row("status", "OPEN", "gross_sales", null)));

        AiVisualizationSpec validated = validator.validate(
                new AiVisualizationSpec(VisualizationType.BAR, "status", "gross_sales", null),
                queryResult);

        assertThat(validated.yColumn()).isEqualTo("gross_sales");
    }

    private Map<String, Object> row(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key1, value1);
        row.put(key2, value2);
        return row;
    }
}
