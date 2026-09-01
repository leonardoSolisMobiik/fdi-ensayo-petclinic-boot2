package org.springframework.samples.petclinic.visits.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = FutureVisitDateException.MESSAGE)
class FutureVisitDateException extends RuntimeException {

    static final String MESSAGE = "la fecha de la visita no puede ser posterior a hoy";

    FutureVisitDateException() {
        super(MESSAGE);
    }
}
