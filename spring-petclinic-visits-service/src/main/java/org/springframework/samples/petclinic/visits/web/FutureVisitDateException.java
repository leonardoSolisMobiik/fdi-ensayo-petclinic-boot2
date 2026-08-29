package org.springframework.samples.petclinic.visits.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
class FutureVisitDateException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    static final String MESSAGE = "la fecha de la visita no puede ser posterior a hoy";

    FutureVisitDateException() {
        super(MESSAGE);
    }
}
