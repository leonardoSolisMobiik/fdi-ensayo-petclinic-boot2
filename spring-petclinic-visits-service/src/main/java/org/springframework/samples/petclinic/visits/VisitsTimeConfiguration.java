package org.springframework.samples.petclinic.visits;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VisitsTimeConfiguration {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

    @Bean
    Clock visitClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
