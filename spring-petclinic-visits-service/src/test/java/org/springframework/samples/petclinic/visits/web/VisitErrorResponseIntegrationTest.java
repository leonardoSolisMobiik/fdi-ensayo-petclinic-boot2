package org.springframework.samples.petclinic.visits.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(VisitErrorResponseTest.FixedClockConfiguration.class)
@ActiveProfiles("test")
class VisitErrorResponseTest {

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    VisitRepository visitRepository;

    @Test
    void futureDateReturnsStandardSpringBootErrorBodyWithMessage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<String>(
            "{\"date\":\"2030-01-03\",\"description\":\"future\"}", headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            "/owners/1/pets/7/visits", HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Bad Request");
        assertThat(response.getBody()).containsEntry("message", FutureVisitDateException.MESSAGE);
        assertThat(response.getBody()).containsEntry("path", "/owners/1/pets/7/visits");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedVisitClock() {
            return Clock.fixed(Instant.parse("2030-01-02T06:30:00Z"), ZoneId.of("UTC"));
        }
    }
}
