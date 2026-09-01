package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VisitClockConfiguration {

    @Bean
    Clock visitClock() {
        return Clock.system(VisitDateValidator.BUSINESS_ZONE);
    }
}
