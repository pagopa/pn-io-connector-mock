package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.exception.SequenceValidationException;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.PaymentStatus;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ReadStatus;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.SequenceStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequenceParserTest {

    private static final String SEQUENCE_LIST = """
            [
              { "sequenceName": "OK_READ_THEN_PAID",
                "steps": [
                  { "afterSeconds": 0,  "status": "ACCEPTED" },
                  { "afterSeconds": 30, "status": "PROCESSED", "readStatus": "UNREAD", "paymentStatus": "NOT_PAID" },
                  { "afterSeconds": 60, "status": "PROCESSED", "readStatus": "READ", "paymentStatus": "NOT_PAID" },
                  { "afterSeconds": 90, "status": "PROCESSED", "paymentStatus": "PAID" }
                ] },
              { "sequenceName": "OK_READ",
                "steps": [
                  { "afterSeconds": 0,  "status": "PROCESSED" },
                  { "afterSeconds": 60, "readStatus": "READ" }
                ] },
              { "sequenceName": "FAILED",
                "steps": [
                  { "afterSeconds": 0,  "status": "ACCEPTED" },
                  { "afterSeconds": 20, "status": "FAILED" }
                ] }
            ]
            """;

    private final SequenceParser sequenceParser = new SequenceParser();

    @Test
    void parseSequenceList() {
        List<Sequence> sequences = sequenceParser.parse(SEQUENCE_LIST);

        assertThat(sequences).extracting(Sequence::sequenceName)
                .containsExactly("OK_READ_THEN_PAID", "OK_READ", "FAILED");
        Sequence okReadThenPaid = sequences.get(0);
        assertThat(okReadThenPaid.steps()).hasSize(4);
        assertThat(okReadThenPaid.steps().get(1))
                .isEqualTo(new SequenceStep(30, MessageStatusValue.PROCESSED, ReadStatus.UNREAD, PaymentStatus.NOT_PAID));
        assertThat(okReadThenPaid.steps().get(3))
                .isEqualTo(new SequenceStep(90, MessageStatusValue.PROCESSED, null, PaymentStatus.PAID));
    }

    @Test
    void sortsStepsByAfterSeconds() {
        String json = """
                [ { "sequenceName": "UNSORTED", "steps": [
                      { "afterSeconds": 60, "readStatus": "READ" },
                      { "afterSeconds": 0,  "status": "PROCESSED" },
                      { "afterSeconds": 30, "paymentStatus": "NOT_PAID" }
                ] } ]
                """;

        List<Sequence> sequences = sequenceParser.parse(json);

        assertThat(sequences.get(0).steps()).extracting(SequenceStep::afterSeconds)
                .containsExactly(0, 30, 60);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSequences")
    void rejectsInvalidSequences(String rule, String json, String expectedMessagePart) {
        assertThatThrownBy(() -> sequenceParser.parse(json))
                .isInstanceOf(SequenceValidationException.class)
                .hasMessageContaining(expectedMessagePart);
    }

    private static Stream<Arguments> invalidSequences() {
        return Stream.of(
                Arguments.of("JSON malformato",
                        "[ { \"sequenceName\": ",
                        "Malformed sequence JSON"),
                Arguments.of("campo sconosciuto",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\", \"reedStatus\": \"READ\" } ] } ]",
                        "Malformed sequence JSON"),
                Arguments.of("sequenceName mancante",
                        "[ { \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\" } ] } ]",
                        "sequenceName must match"),
                Arguments.of("sequenceName vuoto",
                        "[ { \"sequenceName\": \"\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\" } ] } ]",
                        "sequenceName must match"),
                Arguments.of("sequenceName con trattino",
                        "[ { \"sequenceName\": \"OK-READ\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\" } ] } ]",
                        "sequenceName must match"),
                Arguments.of("sequenceName oltre 64 char",
                        "[ { \"sequenceName\": \"" + "A".repeat(65) + "\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\" } ] } ]",
                        "sequenceName must match"),
                Arguments.of("nomi duplicati",
                        """
                        [ { "sequenceName": "S", "steps": [ { "afterSeconds": 0, "status": "ACCEPTED" } ] },
                          { "sequenceName": "S", "steps": [ { "afterSeconds": 0, "status": "ACCEPTED" } ] } ]
                        """,
                        "Duplicate sequenceName 'S'"),
                Arguments.of("nessuno step",
                        "[ { \"sequenceName\": \"S\", \"steps\": [] } ]",
                        "at least one step"),
                Arguments.of("steps mancante",
                        "[ { \"sequenceName\": \"S\" } ]",
                        "at least one step"),
                Arguments.of("step senza campi oltre afterSeconds",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\" }, { \"afterSeconds\": 10 } ] } ]",
                        "step #1: step must define at least one"),
                Arguments.of("afterSeconds mancante",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"status\": \"ACCEPTED\" } ] } ]",
                        "afterSeconds must be >= 0"),
                Arguments.of("afterSeconds negativo",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": -1, \"status\": \"ACCEPTED\" } ] } ]",
                        "afterSeconds must be >= 0"),
                Arguments.of("afterSeconds duplicati",
                        """
                        [ { "sequenceName": "S", "steps": [
                              { "afterSeconds": 0, "status": "ACCEPTED" },
                              { "afterSeconds": 0, "status": "PROCESSED" } ] } ]
                        """,
                        "duplicate afterSeconds 0"),
                Arguments.of("primo step senza status",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 0, \"readStatus\": \"READ\" } ] } ]",
                        "first step must have afterSeconds=0 and a status"),
                Arguments.of("primo step con afterSeconds > 0",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 10, \"status\": \"ACCEPTED\" } ] } ]",
                        "first step must have afterSeconds=0 and a status"),
                Arguments.of("status non valido",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"DELIVERED\" } ] } ]",
                        "invalid status 'DELIVERED'"),
                Arguments.of("readStatus non valido",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\", \"readStatus\": \"SEEN\" } ] } ]",
                        "invalid readStatus 'SEEN'"),
                Arguments.of("paymentStatus non valido",
                        "[ { \"sequenceName\": \"S\", \"steps\": [ { \"afterSeconds\": 0, \"status\": \"ACCEPTED\", \"paymentStatus\": \"PAYED\" } ] } ]",
                        "invalid paymentStatus 'PAYED'"));
    }
}
