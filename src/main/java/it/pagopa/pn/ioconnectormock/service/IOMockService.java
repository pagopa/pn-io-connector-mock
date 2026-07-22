package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.exception.MarkerNotFoundException;
import it.pagopa.pn.ioconnectormock.exception.MessageNotFoundException;
import it.pagopa.pn.ioconnectormock.exception.SequenceUnknownException;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageDetail;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.NewMessage;
import it.pagopa.pn.ioconnectormock.middleware.ssm.SequenceProvider;
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

@Service
@CustomLog
@RequiredArgsConstructor
public class IOMockService {

    private final SequenceEngine sequenceEngine;
    private final SequenceProvider sequenceProvider;

    private static final String MDC_IO_MESSAGE_ID = "ioMessageId";

    private static final Pattern MARKER = Pattern.compile("@io:([A-Za-z0-9_]+)");

    public boolean evaluateProfile(String fiscalCode) {
        boolean senderAllowed = true;
        log.info("profile_evaluated sender_allowed={}", senderAllowed);
        return senderAllowed;
    }

    public String submitMessage(NewMessage newMessage) {
        if (newMessage.getFeatureLevelType() != NewMessage.FeatureLevelTypeEnum.ADVANCED) {
            log.warn("feature_level_type not ADVANCED: {}", newMessage.getFeatureLevelType());
        }

        String subject = newMessage.getContent() != null ? newMessage.getContent().getSubject() : null;
        String sequenceName = extractMarker(subject)
                .orElseThrow(() -> new MarkerNotFoundException("Marker @io:<sequenceName> not found in subject"));

        sequenceProvider.getSequence(sequenceName)
                .orElseThrow(() -> new SequenceUnknownException("Unknown sequence '" + sequenceName + "'"));
        log.info("sequence_resolved sequenceName={}", sequenceName);

        // TODO sostituire con IoMessageIdCodec.encode
        String id = "ioMessageId";
        MDC.put(MDC_IO_MESSAGE_ID, id);
        try {
            log.info("io_message_id_generated sequenceName={}", sequenceName);
            return id;
        } finally {
            MDC.remove(MDC_IO_MESSAGE_ID);
        }
    }

    public ExternalMessageResponseWithContent getMessage(String fiscalCode, String id) {
        MDC.put(MDC_IO_MESSAGE_ID, id);
        try {

            // TODO sostituire con IoMessageIdCodec.decode
            DecodedId decoded = new DecodedId("OK_READ_THEN_PAID", Instant.now().toEpochMilli());

            Sequence sequence = sequenceProvider.getSequence(decoded.sequenceName())
                    .orElseThrow(() -> new MessageNotFoundException(
                            "Sequence '" + decoded.sequenceName() + "' not found in registry"));

            long elapsedSeconds = Math.floorDiv(Instant.now().toEpochMilli() - decoded.submitEpochMillis(), 1000L);
            StateSnapshot snapshot = getStatusSnapshot(sequence, elapsedSeconds);

            MessageDetail message = new MessageDetail()
                    .id(id)
                    .fiscalCode(fiscalCode)
                    .subject("Mock message @io:" + decoded.sequenceName())
                    .createdAt(Instant.ofEpochMilli(decoded.submitEpochMillis()));

            return new ExternalMessageResponseWithContent(message, snapshot.status())
                    .readStatus(snapshot.readStatus())
                    .paymentStatus(snapshot.paymentStatus());
        } finally {
            MDC.remove(MDC_IO_MESSAGE_ID);
        }
    }

    private StateSnapshot getStatusSnapshot(Sequence sequence, long elapsedSeconds) {
        long clampedElapsedSeconds = Math.max(0, elapsedSeconds);
        StateSnapshot snapshot = sequenceEngine.computeSnapshot(sequence, clampedElapsedSeconds);
        log.info("snapshot_computed sequenceName={} elapsedSeconds={} status={} readStatus={} paymentStatus={}",
                sequence.sequenceName(), clampedElapsedSeconds,
                snapshot.status(), snapshot.readStatus(), snapshot.paymentStatus());
        return snapshot;
    }

    public Optional<String> extractMarker(String subject) {
        if (!StringUtils.hasText(subject)) {
            return Optional.empty();
        }
        Matcher matcher = MARKER.matcher(subject);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    record DecodedId(String sequenceName, long submitEpochMillis) {
    }
}
