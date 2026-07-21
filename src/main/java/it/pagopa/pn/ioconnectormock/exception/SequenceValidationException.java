package it.pagopa.pn.ioconnectormock.exception;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

public class SequenceValidationException extends PnRuntimeException {

    public static final String ERROR_CODE_SEQUENCE_VALIDATION = "PN_IOCONNECTORMOCK_SEQUENCE_VALIDATION";

    public SequenceValidationException(String message) {
        this(message, null);
    }

    public SequenceValidationException(String message, Throwable cause) {
        super(message, message, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ERROR_CODE_SEQUENCE_VALIDATION, null, message, cause);
    }
}
