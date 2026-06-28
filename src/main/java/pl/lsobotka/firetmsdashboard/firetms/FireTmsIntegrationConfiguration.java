package pl.lsobotka.firetmsdashboard.firetms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import pl.lsobotka.firetmsdashboard.firetms.integration.FireTmsClient;

@Configuration
public class FireTmsIntegrationConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    FireTmsClient fireTmsClient(ObjectMapper objectMapper, FireTmsProperties properties) {
        RestClient restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        return new FireTmsClient(restClient, objectMapper, properties);
    }
}
