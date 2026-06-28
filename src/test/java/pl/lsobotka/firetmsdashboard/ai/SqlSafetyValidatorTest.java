package pl.lsobotka.firetmsdashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SqlSafetyValidatorTest {

    private final SqlSafetyValidator validator = new SqlSafetyValidator();

    @Test
    void allowsSelectFromAiSalesInvoiceView() {
        String sql = validator.validateAndNormalize("""
                select invoice_number, gross_amount
                from ai_sales_invoice_view
                order by issue_date desc
                limit 25
                """);

        assertThat(sql).contains("from ai_sales_invoice_view");
        assertThat(sql).contains("limit 25");
    }

    @Test
    void rejectsInsert() {
        assertThatThrownBy(() -> validator.validateAndNormalize("insert into ai_sales_invoice_view values ('x')"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("SELECT");
    }

    @Test
    void rejectsUpdate() {
        assertThatThrownBy(() -> validator.validateAndNormalize("update ai_sales_invoice_view set status = 'PAID'"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("SELECT");
    }

    @Test
    void rejectsDelete() {
        assertThatThrownBy(() -> validator.validateAndNormalize("delete from ai_sales_invoice_view"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("SELECT");
    }

    @Test
    void rejectsDrop() {
        assertThatThrownBy(() -> validator.validateAndNormalize("select * from ai_sales_invoice_view drop table x"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("Write or DDL");
    }

    @Test
    void rejectsMultipleStatements() {
        assertThatThrownBy(() -> validator.validateAndNormalize("select * from ai_sales_invoice_view; select 1"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("one statement");
    }

    @Test
    void rejectsRawJson() {
        assertThatThrownBy(() -> validator.validateAndNormalize("select raw_json from ai_sales_invoice_view"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("forbidden");
    }

    @Test
    void rejectsSalesInvoice() {
        assertThatThrownBy(() -> validator.validateAndNormalize("select * from sales_invoice"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("forbidden");
    }

    @Test
    void rejectsMissingAiSalesInvoiceView() {
        assertThatThrownBy(() -> validator.validateAndNormalize("select current_date"))
                .isInstanceOf(SqlValidationException.class)
                .hasMessageContaining("ai_sales_invoice_view");
    }

    @Test
    void appendsLimitWhenMissing() {
        String sql = validator.validateAndNormalize("select invoice_number from ai_sales_invoice_view");

        assertThat(sql).endsWith("limit 100");
    }

    @Test
    void enforcesMaximumLimit() {
        String sql = validator.validateAndNormalize("select invoice_number from ai_sales_invoice_view limit 999");

        assertThat(sql).endsWith("limit 500");
    }
}
