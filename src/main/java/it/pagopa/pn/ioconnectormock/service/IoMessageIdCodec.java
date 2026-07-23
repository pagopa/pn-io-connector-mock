package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.exception.IoMessageIdFormatException;
import it.pagopa.pn.ioconnectormock.model.IoMessageId;

import java.util.UUID;

public class IoMessageIdCodec {

    private static final String PREFIX = "MOCK-";
    private static final int RAND_LENGTH = 6;

    private IoMessageIdCodec() {
    }

    public static String encode(String sequenceName, long submitMillis) {
        if (sequenceName == null || sequenceName.isBlank()) {
            throw new IoMessageIdFormatException("sequenceName must not be null or blank");
        }
        if (sequenceName.contains("-")) {
            throw new IoMessageIdFormatException("sequenceName must not contain hyphens");
        }
        String rand = generateRand();
        return PREFIX + sequenceName + "-" + submitMillis + "-" + rand;
    }

    public static IoMessageId decode(String id) {
        if (id == null || id.isBlank()) {
            throw new IoMessageIdFormatException("id must not be null or blank");
        }
        if (!id.startsWith(PREFIX)) {
            throw new IoMessageIdFormatException("id must start with MOCK- prefix");
        }
        String withoutPrefix = id.substring(PREFIX.length());
        int lastHyphen = withoutPrefix.lastIndexOf('-');
        if (lastHyphen < 0) {
            throw new IoMessageIdFormatException("invalid id format: missing rand segment");
        }
        String withoutRand = withoutPrefix.substring(0, lastHyphen);
        int secondLastHyphen = withoutRand.lastIndexOf('-');
        if (secondLastHyphen < 0) {
            throw new IoMessageIdFormatException("invalid id format: missing submitMillis segment");
        }
        String sequenceName = withoutRand.substring(0, secondLastHyphen);
        String submitMillisStr = withoutRand.substring(secondLastHyphen + 1);
        if (sequenceName.isEmpty()) {
            throw new IoMessageIdFormatException("invalid id format: sequenceName is empty");
        }
        long submitMillis;
        try {
            submitMillis = Long.parseLong(submitMillisStr);
        } catch (NumberFormatException e) {
            throw new IoMessageIdFormatException("invalid id format: submitMillis is not numeric", e);
        }
        return new IoMessageId(sequenceName, submitMillis);
    }

    private static String generateRand() {
        String rand;
        do {
            rand = UUID.randomUUID().toString().replace("-", "").substring(0, RAND_LENGTH);
        } while (rand.matches("\\d+"));
        return rand;
    }
}
