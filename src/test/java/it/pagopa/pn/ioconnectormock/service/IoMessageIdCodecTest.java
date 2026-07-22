package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.exception.IoMessageIdFormatException;
import it.pagopa.pn.ioconnectormock.model.IoMessageId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IoMessageIdCodecTest {

    private static final long FIXED_MILLIS = 1750579200000L;

    @Test
    void encode_startsWithMockPrefix() {
        String id = IoMessageIdCodec.encode("OK_READ", FIXED_MILLIS);

        assertThat(id).startsWith("MOCK-");
    }

    @Test
    void encode_containsSequenceName() {
        String id = IoMessageIdCodec.encode("OK_READ_THEN_PAID", FIXED_MILLIS);

        assertThat(id).contains("OK_READ_THEN_PAID");
    }

    @Test
    void encode_formatHasFourSegments() {
        String id = IoMessageIdCodec.encode("OK_READ", FIXED_MILLIS);

        String[] parts = id.split("-");
        assertThat(parts).hasSize(4);
    }

    @Test
    void encode_randSegmentIsNotAllDigits() {
        String id = IoMessageIdCodec.encode("OK_READ", FIXED_MILLIS);

        String rand = id.substring(id.lastIndexOf('-') + 1);
        assertThat(rand).doesNotMatch("\\d+");
    }

    @Test
    void encode_randSegmentHasSixChars() {
        String id = IoMessageIdCodec.encode("OK_READ", FIXED_MILLIS);

        String rand = id.substring(id.lastIndexOf('-') + 1);
        assertThat(rand).hasSize(6);
    }

    @Test
    void encode_submitMillisIsInOutput() {
        String id = IoMessageIdCodec.encode("OK_READ", FIXED_MILLIS);

        assertThat(id).contains(String.valueOf(FIXED_MILLIS));
    }

    @Test
    void encode_sequenceNameWithHyphen_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.encode("SEQ-WITH-HYPHEN", FIXED_MILLIS))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void encode_nullSequenceName_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.encode(null, FIXED_MILLIS))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void encode_blankSequenceName_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.encode("   ", FIXED_MILLIS))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void decode_knownId_returnsExpectedValues() {
        String id = "MOCK-OK_READ_THEN_PAID-1750579200000-a1b2c3";

        IoMessageId decoded = IoMessageIdCodec.decode(id);

        assertThat(decoded.sequenceName()).isEqualTo("OK_READ_THEN_PAID");
        assertThat(decoded.submitMillis()).isEqualTo(1750579200000L);
    }

    @Test
    void decode_roundtrip() {
        String id = IoMessageIdCodec.encode("OK_READ_THEN_PAID", FIXED_MILLIS);

        IoMessageId decoded = IoMessageIdCodec.decode(id);

        assertThat(decoded.sequenceName()).isEqualTo("OK_READ_THEN_PAID");
        assertThat(decoded.submitMillis()).isEqualTo(FIXED_MILLIS);
    }

    @Test
    void decode_missingMockPrefix_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.decode("NOMOCK-OK_READ-1750579200000-a1b2c3"))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void decode_tooFewSegments_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.decode("MOCK-1750579200000-a1b2c3"))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void decode_submitMillisNotNumeric_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.decode("MOCK-OK_READ-NOTANUMBER-a1b2c3"))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void decode_emptyId_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.decode(""))
                .isInstanceOf(IoMessageIdFormatException.class);
    }

    @Test
    void decode_nullId_throwsIoMessageIdFormatException() {
        assertThatThrownBy(() -> IoMessageIdCodec.decode(null))
                .isInstanceOf(IoMessageIdFormatException.class);
    }
}
