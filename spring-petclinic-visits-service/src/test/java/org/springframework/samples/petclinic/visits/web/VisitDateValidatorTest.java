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

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2030-01-02T06:30:00Z"), ZoneId.of("UTC"));

    private final VisitDateValidator validator = new VisitDateValidator(CLOCK);

    @Test
    void rejectsDayAfterTodayInMexicoCity() {
        assertThatThrownBy(() -> validator.validate(date("2030-01-03T06:00:00Z")))
            .isInstanceOf(FutureVisitDateException.class)
            .hasMessage(FutureVisitDateException.MESSAGE);
    }

    @Test
    void acceptsTodayAtDifferentTime() {
        assertThatCode(() -> validator.validate(date("2030-01-03T05:59:59Z")))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsPastDate() {
        assertThatCode(() -> validator.validate(date("2030-01-01T06:00:00Z")))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingDate() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }

    @Test
    void decisionDoesNotDependOnServerTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertThatThrownBy(() -> validator.validate(date("2030-01-03T06:00:00Z")))
                .isInstanceOf(FutureVisitDateException.class);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago"));
            assertThatThrownBy(() -> validator.validate(date("2030-01-03T06:00:00Z")))
                .isInstanceOf(FutureVisitDateException.class);
        }
        finally {
            TimeZone.setDefault(original);
        }
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }
}
