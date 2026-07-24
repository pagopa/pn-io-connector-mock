package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.exception.MarkerNotFoundException;
import it.pagopa.pn.ioconnectormock.exception.SequenceUnknownException;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageContent;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.NewMessage;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.PaymentStatus;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ReadStatus;
import it.pagopa.pn.ioconnectormock.middleware.ssm.SenderNotAllowedProvider;
import it.pagopa.pn.ioconnectormock.middleware.ssm.SequenceProvider;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.SequenceStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue.ACCEPTED;
import static it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IOMockServiceTest {

    private static final String SEQUENCE_NAME = "OK_READ_THEN_PAID";
    private static final Sequence OK_READ_THEN_PAID = new Sequence(SEQUENCE_NAME, List.of(
            new SequenceStep(0, ACCEPTED, null, null),
            new SequenceStep(30, PROCESSED, ReadStatus.UNREAD, PaymentStatus.NOT_PAID),
            new SequenceStep(60, PROCESSED, ReadStatus.READ, PaymentStatus.NOT_PAID),
            new SequenceStep(90, PROCESSED, null, PaymentStatus.PAID)));

    private SequenceProvider sequenceProvider;
    private SenderNotAllowedProvider senderNotAllowedProvider;
    private IOMockService service;

    @BeforeEach
    void setUp() {
        sequenceProvider = mock(SequenceProvider.class);
        senderNotAllowedProvider = mock(SenderNotAllowedProvider.class);
        service = new IOMockService(new SequenceEngine(), sequenceProvider, senderNotAllowedProvider);
    }

    @Test
    void profileAllowedWhenNotDenied() {
        when(senderNotAllowedProvider.isDenied("RSSMRA80A01H501U")).thenReturn(false);
        assertThat(service.evaluateProfile("RSSMRA80A01H501U")).isTrue();
    }

    @Test
    void profileNotAllowedWhenDenied() {
        when(senderNotAllowedProvider.isDenied("RSSMRA80A01H501U")).thenReturn(true);
        assertThat(service.evaluateProfile("RSSMRA80A01H501U")).isFalse();
    }


    @Test
    void extractMarkerReturnsFirstMatch() {
        assertThat(service.extractMarker("hello @io:FIRST bye @io:SECOND")).contains("FIRST");
    }

    @Test
    void extractMarkerEmptyWhenAbsentOrBlank() {
        assertThat(service.extractMarker("no marker here")).isEmpty();
        assertThat(service.extractMarker("@io:")).isEmpty();
        assertThat(service.extractMarker(null)).isEmpty();
    }

    @Test
    void submitFailsWhenMarkerMissing() {
        assertThatThrownBy(() -> service.submitMessage(newMessage("no marker")))
                .isInstanceOf(MarkerNotFoundException.class);
    }

    @Test
    void submitResolvesKnownSequence() {
        when(sequenceProvider.getSequence(SEQUENCE_NAME)).thenReturn(Optional.of(OK_READ_THEN_PAID));

        assertThat(service.submitMessage(newMessage("@io:" + SEQUENCE_NAME))).isNotBlank();
    }

    @Test
    void submitFailsWhenSequenceUnknown() {
        when(sequenceProvider.getSequence(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitMessage(newMessage("@io:UNKNOWN_SEQUENCE")))
                .isInstanceOf(SequenceUnknownException.class);
    }

    private NewMessage newMessage(String subject) {
        return new NewMessage("RSSMRA80A01H501U", new MessageContent(subject, "body"))
                .featureLevelType(NewMessage.FeatureLevelTypeEnum.ADVANCED);
    }
}
