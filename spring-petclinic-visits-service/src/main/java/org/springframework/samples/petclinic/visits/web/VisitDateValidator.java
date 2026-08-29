package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.stereotype.Component;

@Component
class VisitDateValidator {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

    private final Clock clock;

    VisitDateValidator(Clock clock) {
        this.clock = clock;
    }

    void validate(Date visitDate) {
        if (visitDate == null) {
            return;
        }

        LocalDate date = visitDate.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
        if (date.isAfter(LocalDate.now(clock.withZone(BUSINESS_ZONE)))) {
            throw new FutureVisitDateException();
        }
    }
}
