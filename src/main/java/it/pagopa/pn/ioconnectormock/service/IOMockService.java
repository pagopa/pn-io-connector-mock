package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.exception.MarkerNotFoundException;
import it.pagopa.pn.ioconnectormock.exception.MessageNotFoundException;
import it.pagopa.pn.ioconnectormock.exception.SequenceUnknownException;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageDetail;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.NewMessage;
import it.pagopa.pn.ioconnectormock.middleware.ssm.SenderNotAllowedProvider;
import it.pagopa.pn.ioconnectormock.middleware.ssm.SequenceProvider;
import it.pagopa.pn.ioconnectormock.model.IoMessageId;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.StateSnapshot;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static it.pagopa.pn.ioconnectormock.utils.LogUtils.*;

@Service
@CustomLog
@RequiredArgsConstructor
public class IOMockService {

    private final SequenceEngine sequenceEngine;
    private final SequenceProvider sequenceProvider;
    private final SenderNotAllowedProvider senderNotAllowedProvider;

    private static final String MDC_IO_MESSAGE_ID = "ioMessageId";

    private static final Pattern MARKER = Pattern.compile("@io:([A-Za-z0-9_]+)");

    public boolean evaluateProfile(String fiscalCode) {
        log.logStartingProcess(GET_PROFILE);
        boolean senderAllowed = !senderNotAllowedProvider.isDenied(fiscalCode);
        log.info("EvaluateProfile -> sender_allowed={}", senderAllowed);
        log.logEndingProcess(GET_PROFILE);
        return senderAllowed;
    }

    public String submitMessage(NewMessage newMessage) {
        log.logStartingProcess(SUBMIT_MESSAGE);

        String subject = newMessage.getContent() != null ? newMessage.getContent().getSubject() : null;
        log.warn("Mocking message with subject {}", subject);

        String sequenceName = extractMarker(subject)
                .orElseThrow(() -> new MarkerNotFoundException("Marker @io:<sequenceName> not found in subject"));

        sequenceProvider.getSequence(sequenceName)
                .orElseThrow(() -> new SequenceUnknownException("Unknown sequence '" + sequenceName + "'"));
        log.info("Sequence found with sequenceName={}", sequenceName);

        String ioMessageId = IoMessageIdCodec.encode(sequenceName, Instant.now().toEpochMilli());

        MDC.put(MDC_IO_MESSAGE_ID, ioMessageId);
        try {
            log.info("Encoded ioMessageId={} for subject={}", ioMessageId, subject);
            return ioMessageId;
        } finally {
            MDC.remove(MDC_IO_MESSAGE_ID);
            log.logEndingProcess(SUBMIT_MESSAGE);
        }
    }

    public ExternalMessageResponseWithContent getMessage(String fiscalCode, String id) {
        log.logStartingProcess(GET_MESSAGE);
        MDC.put(MDC_IO_MESSAGE_ID, id);
        try {

            IoMessageId decodeMessageId = IoMessageIdCodec.decode(id);

            Sequence sequence = sequenceProvider.getSequence(decodeMessageId.sequenceName())
                    .orElseThrow(() -> new MessageNotFoundException(
                            "Sequence '" + decodeMessageId.sequenceName() + "' not found in registry"));

            long elapsedSeconds = Math.floorDiv(Instant.now().toEpochMilli() - decodeMessageId.submitMillis(), 1000L);
            StateSnapshot snapshot = getStatusSnapshot(sequence, elapsedSeconds);

            MessageDetail message = new MessageDetail()
                    .id(id)
                    .fiscalCode(fiscalCode)
                    .subject("Mock message @io:" + decodeMessageId.sequenceName())
                    .createdAt(Instant.ofEpochMilli(decodeMessageId.submitMillis()));

            log.info("IoMessageId '{}' snapshot: status={} readStatus={} paymentStatus={}",
                    id, snapshot.status(), snapshot.readStatus(), snapshot.paymentStatus());

            return new ExternalMessageResponseWithContent(message, snapshot.status())
                    .readStatus(snapshot.readStatus())
                    .paymentStatus(snapshot.paymentStatus());
        } finally {
            MDC.remove(MDC_IO_MESSAGE_ID);
            log.logEndingProcess(GET_MESSAGE);
        }
    }

    private StateSnapshot getStatusSnapshot(Sequence sequence, long elapsedSeconds) {
        long clampedElapsedSeconds = Math.max(0, elapsedSeconds);
        return sequenceEngine.computeSnapshot(sequence, clampedElapsedSeconds);
    }

    public Optional<String> extractMarker(String subject) {
        if (!StringUtils.hasText(subject)) {
            return Optional.empty();
        }
        Matcher matcher = MARKER.matcher(subject);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

}
