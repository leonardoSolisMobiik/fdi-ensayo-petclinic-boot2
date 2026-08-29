package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitDateValidatorTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-15T18:00:00Z"), BUSINESS_ZONE);
    private final VisitDateValidator validator = new VisitDateValidator(CLOCK);

    @Test
    void shouldRejectADateAfterToday() {
        Date tomorrow = Date.from(Instant.parse("2030-06-16T12:00:00Z"));

        assertThatThrownBy(() -> validator.validate(tomorrow))
            .isInstanceOf(FutureVisitDateException.class)
            .hasMessage(FutureVisitDateException.MESSAGE);
    }

    @Test
    void shouldAcceptTodayAtADifferentTime() {
        Date laterToday = Date.from(Instant.parse("2030-06-16T05:59:59Z"));

        assertThatCode(() -> validator.validate(laterToday)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptAPastDate() {
        Date yesterday = Date.from(Instant.parse("2030-06-14T12:00:00Z"));

        assertThatCode(() -> validator.validate(yesterday)).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepTheExistingDefaultWhenDateIsAbsent() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldUseTheBusinessZoneRegardlessOfTheServerTimeZone() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        Date lastSecondOfToday = Date.from(Instant.parse("2030-06-16T05:59:59Z"));

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertThatCode(() -> validator.validate(lastSecondOfToday)).doesNotThrowAnyException();

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertThatCode(() -> validator.validate(lastSecondOfToday)).doesNotThrowAnyException();
        }
        finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void shouldRejectAtTheFirstInstantOfTomorrowInTheBusinessZone() {
        Date firstInstantOfTomorrow = Date.from(Instant.parse("2030-06-16T06:00:00Z"));

        assertThatThrownBy(() -> validator.validate(firstInstantOfTomorrow))
            .isInstanceOf(FutureVisitDateException.class);
    }
}
