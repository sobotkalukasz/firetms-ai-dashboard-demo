package pl.lsobotka.firetmsdashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestrictedAiSalesInvoiceDatabaseProviderTest {

    private RestrictedAiSalesInvoiceDatabaseProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ai-dashboard-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table sales_invoice (
                        external_id varchar(255),
                        invoice_number varchar(255),
                        issue_date date,
                        sale_date date,
                        payment_due_date date,
                        contractor_name varchar(255),
                        net_amount decimal(19, 2),
                        gross_amount decimal(19, 2),
                        currency varchar(32),
                        status varchar(128),
                        raw_json clob,
                        imported_at timestamp,
                        updated_at timestamp
                    )
                    """);
            statement.execute("""
                    insert into sales_invoice (
                        external_id, invoice_number, issue_date, sale_date, payment_due_date,
                        contractor_name, net_amount, gross_amount, currency, status, raw_json, imported_at, updated_at
                    ) values (
                        'SI:1', 'FV/1', date '2026-06-01', date '2026-06-01', date '2026-06-15',
                        'Acme', 100.00, 123.00, 'EUR', 'ISSUED', '{"private":true}',
                        timestamp '2026-06-28 10:00:00', timestamp '2026-06-28 12:00:00'
                    )
                    """);
            statement.execute("""
                    create view ai_sales_invoice_view as
                    select
                        external_id as firetms_id,
                        invoice_number,
                        issue_date,
                        sale_date,
                        payment_due_date,
                        contractor_name,
                        net_amount,
                        gross_amount,
                        currency,
                        status,
                        imported_at,
                        updated_at
                    from sales_invoice
                    """);
        }

        provider = new RestrictedAiSalesInvoiceDatabaseProvider(dataSource);
    }

    @Test
    void executesSelectQueryAgainstRestrictedView() {
        List<Map<String, Object>> rows = provider.executeQuery("""
                select invoice_number, gross_amount
                from ai_sales_invoice_view
                order by issue_date desc
                limit 10
                """);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("INVOICE_NUMBER")).isEqualTo("FV/1");
            assertThat(row.get("GROSS_AMOUNT").toString()).isEqualTo("123.00");
        });
    }

    @Test
    void rejectsNonSelectStatements() {
        assertThatThrownBy(() -> provider.executeQuery("delete from ai_sales_invoice_view"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SELECT");
    }

    @Test
    void rejectsRawJsonQueries() {
        assertThatThrownBy(() -> provider.executeQuery("""
                select raw_json
                from ai_sales_invoice_view
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raw_json");
    }

    @Test
    void rejectsUnderlyingTableQueries() {
        assertThatThrownBy(() -> provider.executeQuery("""
                select invoice_number
                from sales_invoice
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai_sales_invoice_view");
    }

    @Test
    void rejectsMultipleStatementsAndComments() {
        assertThatThrownBy(() -> provider.executeQuery("""
                select invoice_number
                from ai_sales_invoice_view;
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single SQL statement");

        assertThatThrownBy(() -> provider.executeQuery("""
                select invoice_number
                from ai_sales_invoice_view -- hidden write
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comments");
    }
}
