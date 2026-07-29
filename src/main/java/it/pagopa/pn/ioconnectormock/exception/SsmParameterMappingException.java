package it.pagopa.pn.ioconnectormock.exception;

public class SsmParameterMappingException extends RuntimeException {

    public SsmParameterMappingException(String message) {
        super(message);
    }

    public SsmParameterMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
