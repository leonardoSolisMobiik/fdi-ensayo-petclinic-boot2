package org.springframework.samples.petclinic.visits.model;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

public class VisitDateJsonDeserializer extends JsonDeserializer<Date> {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getText();
        try {
            return Date.from(LocalDate.parse(value).atStartOfDay(BUSINESS_ZONE).toInstant());
        }
        catch (DateTimeParseException dateOnlyFailure) {
            try {
                return Date.from(OffsetDateTime.parse(value).toInstant());
            }
            catch (DateTimeParseException offsetFailure) {
                throw JsonMappingException.from(parser, "Formato de fecha de visita inválido", offsetFailure);
            }
        }
    }
}
