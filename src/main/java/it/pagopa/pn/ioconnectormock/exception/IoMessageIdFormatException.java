package it.pagopa.pn.ioconnectormock.exception;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

public class IoMessageIdFormatException extends PnRuntimeException {

    public static final String ERROR_CODE_IO_MESSAGE_ID_FORMAT = "PN_IOCONNECTORMOCK_IO_MESSAGE_ID_FORMAT";

    public IoMessageIdFormatException(String message) {
        this(message, null);
    }

    public IoMessageIdFormatException(String message, Throwable cause) {
        super(message, message, HttpStatus.BAD_REQUEST.value(),
                ERROR_CODE_IO_MESSAGE_ID_FORMAT, null, message, cause);
    }
}
