package it.pagopa.pn.ioconnectormock.exception;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

public class MessageNotFoundException extends PnRuntimeException {

    public static final String ERROR_CODE_MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";

    public MessageNotFoundException(String message) {
        super(message, message, HttpStatus.NOT_FOUND.value(),
                ERROR_CODE_MESSAGE_NOT_FOUND, null, message, null);
    }
}
