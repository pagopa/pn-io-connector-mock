package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.PaymentStatus;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ReadStatus;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.SequenceStep;
import it.pagopa.pn.ioconnectormock.model.StateSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue.ACCEPTED;
import static it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue.FAILED;
import static it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;

class SequenceEngineTest {

    private static final Sequence OK_READ_THEN_PAID = new Sequence("OK_READ_THEN_PAID", List.of(
            step(0, ACCEPTED, null, null),
            step(30, PROCESSED, ReadStatus.UNREAD, PaymentStatus.NOT_PAID),
            step(60, PROCESSED, ReadStatus.READ, PaymentStatus.NOT_PAID),
            step(90, PROCESSED, null, PaymentStatus.PAID)));

    private final SequenceEngine sequenceEngine = new SequenceEngine();

    @ParameterizedTest(name = "elapsed={0} -> {1}/{2}/{3}")
    @MethodSource("okReadThenPaid")
    void computeSnapshotOnOkReadThenPaid(long elapsedSeconds, MessageStatusValue expectedStatus,
                                         ReadStatus expectedReadStatus, PaymentStatus expectedPaymentStatus) {
        StateSnapshot snapshot = sequenceEngine.computeSnapshot(OK_READ_THEN_PAID, elapsedSeconds);

        assertThat(snapshot).isEqualTo(new StateSnapshot(expectedStatus, expectedReadStatus, expectedPaymentStatus));
    }

    private static Stream<Arguments> okReadThenPaid() {
        return Stream.of(
                Arguments.of(-5L, null, null, null),
                Arguments.of(0L, ACCEPTED, null, null),
                Arguments.of(29L, ACCEPTED, null, null),
                Arguments.of(30L, PROCESSED, ReadStatus.UNREAD, PaymentStatus.NOT_PAID),
                Arguments.of(59L, PROCESSED, ReadStatus.UNREAD, PaymentStatus.NOT_PAID),
                Arguments.of(60L, PROCESSED, ReadStatus.READ, PaymentStatus.NOT_PAID),
                Arguments.of(90L, PROCESSED, ReadStatus.READ, PaymentStatus.PAID),
                Arguments.of(100000L, PROCESSED, ReadStatus.READ, PaymentStatus.PAID));
    }

    @Test
    void sequenceStepWithoutStatus() {
        Sequence okRead = new Sequence("OK_READ", List.of(
                step(0, PROCESSED, null, null),
                step(60, null, ReadStatus.READ, null)));

        StateSnapshot snapshot = sequenceEngine.computeSnapshot(okRead, 60);

        assertThat(snapshot).isEqualTo(new StateSnapshot(PROCESSED, ReadStatus.READ, null));
    }

    @Test
    void lastStatusReturned() {
        Sequence failed = new Sequence("FAILED", List.of(
                step(0, ACCEPTED, null, null),
                step(20, FAILED, null, null)));

        assertThat(sequenceEngine.computeSnapshot(failed, 19).status()).isEqualTo(ACCEPTED);
        assertThat(sequenceEngine.computeSnapshot(failed, 20).status()).isEqualTo(FAILED);
    }

    private static SequenceStep step(int afterSeconds, MessageStatusValue status,
                                     ReadStatus readStatus, PaymentStatus paymentStatus) {
        return new SequenceStep(afterSeconds, status, readStatus, paymentStatus);
    }
}
