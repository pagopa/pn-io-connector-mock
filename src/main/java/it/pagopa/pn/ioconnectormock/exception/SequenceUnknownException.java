package it.pagopa.pn.ioconnectormock.exception;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

public class SequenceUnknownException extends PnRuntimeException {

    public static final String ERROR_CODE_SEQUENCE_UNKNOWN = "SEQUENCE_UNKNOWN";

    public SequenceUnknownException(String message) {
        super(message, message, HttpStatus.BAD_REQUEST.value(),
                ERROR_CODE_SEQUENCE_UNKNOWN, null, message, null);
    }
}
