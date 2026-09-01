package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.samples.petclinic.visits.model.Visit.visit;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(VisitResource.class)
@Import({VisitDateValidator.class, VisitResourceDateTest.FixedClockConfiguration.class})
@ActiveProfiles("test")
class VisitResourceDateTest {

    private static final String VISITS_PATH = "/owners/1/pets/7/visits";

    @Autowired
    MockMvc mvc;

    @MockBean
    VisitRepository visitRepository;

    @Test
    void futureDateReturnsBadRequestAndIsNotSaved() throws Exception {
        mvc.perform(post(VISITS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2030-01-03\",\"description\":\"future\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(FutureVisitDateException.MESSAGE));

        verify(visitRepository, never()).save(any(Visit.class));
    }

    @Test
    void todayWithDifferentTimeIsSaved() throws Exception {
        given(visitRepository.save(any(Visit.class))).willAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(post(VISITS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2030-01-02T23:45:00-06:00\",\"description\":\"today\"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<Visit> saved = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(saved.capture());
        assertThat(saved.getValue().getDate()).isEqualTo(Date.from(Instant.parse("2030-01-03T05:45:00Z")));
    }

    @Test
    void pastDateIsSavedWithoutChangingItsDay() throws Exception {
        given(visitRepository.save(any(Visit.class))).willAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(post(VISITS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2029-12-31\",\"description\":\"past\"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<Visit> saved = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(saved.capture());
        assertThat(saved.getValue().getDate()).isEqualTo(Date.from(Instant.parse("2029-12-31T06:00:00Z")));
    }

    @Test
    void invalidDateFormatReturnsBadRequestAndIsNotSaved() throws Exception {
        mvc.perform(post(VISITS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"not-a-date\"}"))
            .andExpect(status().isBadRequest());

        verify(visitRepository, never()).save(any(Visit.class));
    }

    @Test
    void missingDateKeepsExistingDefaultAssignment() throws Exception {
        given(visitRepository.save(any(Visit.class))).willAnswer(invocation -> invocation.getArgument(0));
        Instant before = Instant.now();

        mvc.perform(post(VISITS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"without date\"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<Visit> saved = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(saved.capture());
        assertThat(saved.getValue().getDate()).isNotNull();
        assertThat(saved.getValue().getDate().toInstant()).isAfterOrEqualTo(before);
    }

    @Test
    void serverTimeZoneDoesNotChangeTodayDecision() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            given(visitRepository.save(any(Visit.class))).willAnswer(invocation -> invocation.getArgument(0));

            mvc.perform(post(VISITS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"2030-01-02T00:15:00-06:00\"}"))
                .andExpect(status().isCreated());
        }
        finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void historicalFutureVisitCanBeReadUnchanged() throws Exception {
        Visit historical = visit().id(9).petId(7)
            .date(Date.from(Instant.parse("2030-01-03T06:00:00Z"))).build();
        given(visitRepository.findByPetId(7)).willReturn(singletonList(historical));

        mvc.perform(get(VISITS_PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(9))
            .andExpect(jsonPath("$[0].date").value("2030-01-03"));

        assertThat(historical.getDate()).isEqualTo(Date.from(Instant.parse("2030-01-03T06:00:00Z")));
        verify(visitRepository, never()).save(any(Visit.class));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedVisitClock() {
            return Clock.fixed(Instant.parse("2030-01-02T06:30:00Z"), ZoneId.of("UTC"));
        }
    }
}
