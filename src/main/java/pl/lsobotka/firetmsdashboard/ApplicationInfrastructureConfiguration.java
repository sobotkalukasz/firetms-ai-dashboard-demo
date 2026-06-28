package pl.lsobotka.firetmsdashboard;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationInfrastructureConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService aiDashboardExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
