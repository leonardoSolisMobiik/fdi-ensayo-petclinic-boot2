package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.visits.VisitsServiceApplication;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = { VisitsServiceApplication.class, VisitResourceIntegrationTest.FixedClockConfiguration.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class VisitResourceIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
    private static final Instant FIXED_INSTANT = Instant.parse("2030-06-15T18:00:00Z");
    private static final int PET_ID = 410;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VisitRepository visitRepository;

    @BeforeEach
    void cleanDatabase() {
        visitRepository.deleteAll();
    }

    @Test
    void shouldRegisterVisitsForTodayAndAPastDate() {
        ResponseEntity<Visit> today = postVisit(PET_ID, visitJson("2030-06-15", "today"), Visit.class);
        ResponseEntity<Visit> past = postVisit(PET_ID, visitJson("2030-06-14", "past"), Visit.class);

        assertThat(today.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(past.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(visitRepository.findByPetId(PET_ID))
            .extracting(Visit::getDescription)
            .containsExactlyInAnyOrder("today", "past");
    }

    @Test
    void shouldUseTheCurrentDefaultDateWhenDateIsMissing() {
        ResponseEntity<Visit> response = postVisit(
            PET_ID,
            "{\"description\":\"without date\"}",
            Visit.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDate()).isNotNull();
        assertThat(visitRepository.findById(response.getBody().getId()))
            .hasValueSatisfying(visit -> assertThat(visit.getDate()).isNotNull());
    }

    @Test
    void shouldRejectTomorrowWithTheStandardSpringBootError() {
        ResponseEntity<JsonNode> response = postVisit(
            PET_ID,
            visitJson("2030-06-16", "future"),
            JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asInt()).isEqualTo(400);
        assertThat(response.getBody().path("error").asText()).isEqualTo("Bad Request");
        assertThat(response.getBody().path("message").asText())
            .isEqualTo("la fecha de la visita no puede ser posterior a hoy");
        assertThat(response.getBody().path("path").asText())
            .isEqualTo("/owners/1/pets/" + PET_ID + "/visits");
        assertThat(response.getBody().hasNonNull("timestamp")).isTrue();
        assertThat(visitRepository.count()).isZero();
    }

    @Test
    void shouldReturnRegisteredVisitsForThePet() {
        postVisit(PET_ID, visitJson("2030-06-14", "registered"), Visit.class);

        ResponseEntity<Visit[]> response = restTemplate.getForEntity(
            url("/owners/1/pets/" + PET_ID + "/visits"),
            Visit[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getPetId()).isEqualTo(PET_ID);
        assertThat(response.getBody()[0].getDescription()).isEqualTo("registered");
    }

    @Test
    void shouldKeepTheAggregatedVisitsQueryAvailable() {
        postVisit(PET_ID, visitJson("2030-06-14", "first pet"), Visit.class);
        postVisit(PET_ID + 1, visitJson("2030-06-13", "second pet"), Visit.class);

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
            url("/pets/visits?petId=" + PET_ID + "," + (PET_ID + 1)),
            JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("items").isArray()).isTrue();
        assertThat(response.getBody().path("items")).hasSize(2);
    }

    @Test
    void shouldReadAHistoricalFutureVisitWithoutChangingIt() {
        Date futureDate = Date.from(Instant.parse("2030-06-16T12:00:00Z"));
        Visit historical = visitRepository.saveAndFlush(Visit.visit()
            .petId(PET_ID)
            .date(futureDate)
            .description("historical future")
            .build());

        ResponseEntity<Visit[]> response = restTemplate.getForEntity(
            url("/owners/1/pets/" + PET_ID + "/visits"),
            Visit[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getDate()).isEqualTo(futureDate);
        assertThat(visitRepository.findById(historical.getId()))
            .hasValueSatisfying(stored -> {
                assertThat(stored.getDate()).isEqualTo(futureDate);
                assertThat(stored.getDescription()).isEqualTo("historical future");
            });
        assertThat(visitRepository.count()).isEqualTo(1);
    }

    private <T> ResponseEntity<T> postVisit(int petId, String json, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
            url("/owners/1/pets/" + petId + "/visits"),
            HttpMethod.POST,
            new HttpEntity<String>(json, headers),
            responseType
        );
    }

    private String visitJson(String date, String description) {
        return "{\"date\":\"" + date + "\",\"description\":\"" + description + "\"}";
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedVisitClock() {
            return Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
        }
    }
}
