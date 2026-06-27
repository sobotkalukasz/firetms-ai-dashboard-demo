package pl.lsobotka.firetmsdashboard.firetms;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "firetms")
public record FireTmsProperties(@NotBlank String baseUrl) {
}
