package pl.lsobotka.firetmsdashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.application.SalesInvoiceAnalyticsService;

class AiDashboardPromptServiceTest {

    private final AiDashboardPromptService service =
            new AiDashboardPromptService(Mockito.mock(SalesInvoiceAnalyticsService.class));

    @Test
    void classifiesGrossSalesByMonthPrompt() {
        AiDashboardPromptService.PromptRequest result = service.classifyPrompt("Show gross sales by month");

        assertThat(result.intent()).isEqualTo(AiDashboardPromptService.PromptIntent.GROSS_SALES_BY_MONTH);
    }

    @Test
    void classifiesTopContractorsPrompt() {
        AiDashboardPromptService.PromptRequest result = service.classifyPrompt("show TOP 10 contractors by gross amount");

        assertThat(result.intent()).isEqualTo(AiDashboardPromptService.PromptIntent.TOP_CONTRACTORS_BY_GROSS_AMOUNT);
        assertThat(result.limit()).isEqualTo(10);
    }

    @Test
    void classifiesInvoiceCountByStatusPrompt() {
        AiDashboardPromptService.PromptRequest result = service.classifyPrompt("Show invoice count by status");

        assertThat(result.intent()).isEqualTo(AiDashboardPromptService.PromptIntent.INVOICE_COUNT_BY_STATUS);
    }

    @Test
    void returnsUnknownForUnsupportedPrompt() {
        AiDashboardPromptService.PromptRequest result = service.classifyPrompt("Show me trends that matter");

        assertThat(result.intent()).isEqualTo(AiDashboardPromptService.PromptIntent.UNKNOWN);
    }
}
