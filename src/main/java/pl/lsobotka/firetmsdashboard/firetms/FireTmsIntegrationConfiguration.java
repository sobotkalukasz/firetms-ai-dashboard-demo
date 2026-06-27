package pl.lsobotka.firetmsdashboard.firetms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.firetms.salesinvoices.FireTmsSalesInvoiceClient;

@Configuration
public class FireTmsIntegrationConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    FireTmsSalesInvoiceClient fireTmsSalesInvoiceClient(ObjectMapper objectMapper, FireTmsProperties properties) {
        RestClient restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        return new FireTmsSalesInvoiceClient(restClient, objectMapper, properties);
    }
}
