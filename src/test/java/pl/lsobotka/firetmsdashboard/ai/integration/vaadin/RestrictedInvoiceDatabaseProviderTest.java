package pl.lsobotka.firetmsdashboard.ai.integration.vaadin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.lsobotka.firetmsdashboard.ai.query.SqlSafetyValidator;

class RestrictedInvoiceDatabaseProviderTest {

    private final RestrictedInvoiceDatabaseProvider provider =
            new RestrictedInvoiceDatabaseProvider(new JdbcTemplate(), new SqlSafetyValidator());

    @Test
    void addsDefaultLimitToBaseQuery() {
        String executableSql = provider.prepareExecutableSql(
                "select invoice_number from ai_sales_invoice_view");

        assertThat(executableSql)
                .isEqualTo("select invoice_number from ai_sales_invoice_view limit 100");
    }

    @Test
    void preservesGridLimitWrapperWhileValidatingInnerQuery() {
        String executableSql = provider.prepareExecutableSql(
                "SELECT * FROM (select invoice_number from ai_sales_invoice_view) AS _limited LIMIT 25");

        assertThat(executableSql)
                .isEqualTo("SELECT * FROM (select invoice_number from ai_sales_invoice_view limit 100) AS _limited LIMIT 25");
    }

    @Test
    void preservesNestedGridWrappers() {
        String executableSql = provider.prepareExecutableSql(
                """
                SELECT * FROM (
                    SELECT * FROM (
                        select issue_date, gross_amount from ai_sales_invoice_view limit 50
                    ) AS _t ORDER BY "gross_amount" DESC
                ) AS _limited LIMIT 20 OFFSET 0
                """);

        assertThat(executableSql)
                .isEqualTo(
                        "SELECT * FROM (SELECT * FROM (select issue_date, gross_amount from ai_sales_invoice_view limit 50) "
                                + "AS _t ORDER BY \"gross_amount\" DESC) AS _limited LIMIT 20 OFFSET 0");
    }

    @Test
    void preservesCountWrapper() {
        String executableSql = provider.prepareExecutableSql(
                "SELECT COUNT(*) FROM (select status from ai_sales_invoice_view limit 40) AS _counted");

        assertThat(executableSql)
                .isEqualTo("SELECT COUNT(*) FROM (select status from ai_sales_invoice_view limit 40) AS _counted");
    }

    @Test
    void explainsReservedAliasErrorsWithSafeAliasHint() {
        String message = provider.explainExecutionFailure(
                "SELECT sum(gross_amount) AS value FROM ai_sales_invoice_view limit 100",
                new IllegalArgumentException("expected \"identifier\""));

        assertThat(message).contains("reserved alias");
        assertThat(message).contains("metric_value");
        assertThat(message).contains("month_value");
    }
}
