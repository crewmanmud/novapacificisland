package ca.andrewmccallum.novapacificisland.booking.config;

import java.time.Clock;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class AppConfig {

    private static final ApiInfo API_INFO =
            new ApiInfo(
                    "Booking API",
                    "An API for booking stays on Nova Pacific Island.",
                    "0.0.1-alpha",
                    null,
                    null,
                    null,
                    null,
                    Collections.emptyList());

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public Docket docket() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(API_INFO);
    }
}
