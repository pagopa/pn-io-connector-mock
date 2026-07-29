package it.pagopa.pn.ioconnectormock.exception;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

public class MarkerNotFoundException extends PnRuntimeException {

    public static final String ERROR_CODE_MARKER_NOT_FOUND = "MARKER_NOT_FOUND";

    public MarkerNotFoundException(String message) {
        super(message, message, HttpStatus.BAD_REQUEST.value(),
                ERROR_CODE_MARKER_NOT_FOUND, null, message, null);
    }
}
