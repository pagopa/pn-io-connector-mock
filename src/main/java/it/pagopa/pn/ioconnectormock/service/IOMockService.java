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

    /**
     * Valuta se il mittente è abilitato consultando la lista dei sender not allowed.
     *
     * @return {@code true} se il codice fiscale non è nella lista dei sender not allowed.
     */
    public boolean evaluateProfile(String fiscalCode) {
        log.logStartingProcess(GET_PROFILE);
        boolean senderAllowed = !senderNotAllowedProvider.isDenied(fiscalCode);
        log.info("EvaluateProfile -> sender_allowed={}", senderAllowed);
        log.logEndingProcess(GET_PROFILE);
        return senderAllowed;
    }

    /**
     * Simula l'invio di un messaggio: estrae dal subject il marker della sequenza,
     * ne verifica l'esistenza e restituisce l'id messaggio codificato.
     *
     * @return l'id messaggio mock
     * @throws MarkerNotFoundException se il subject non contiene il marker {@code @io:}
     * @throws SequenceUnknownException se la sequenza indicata non esiste
     */
    public String submitMessage(NewMessage newMessage) {
        log.logStartingProcess(SUBMIT_MESSAGE);

        String subject = newMessage.getContent() != null ? newMessage.getContent().getSubject() : null;
        log.warn("Mocking message with subject {}", subject);

        String sequenceName = extractMarker(subject)
                .orElseThrow(() -> new MarkerNotFoundException("Marker @io:<sequenceName> not found in subject"));

        sequenceProvider.getSequence(sequenceName)
                .orElseThrow(() -> new SequenceUnknownException("Unknown sequence '" + sequenceName + "'"));
        log.info("Sequence found with sequenceName={}", sequenceName);

        // L'istante di invio viene incorporato nell'id
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

    /**
     * Ricostruisce il dettaglio e lo stato corrente del messaggio a partire dal suo id.
     * Lo stato dipende dal tempo trascorso dall'invio codificato nell'id.
     *
     * @throws MessageNotFoundException se la sequenza referenziata dall'id non esiste
     * @throws it.pagopa.pn.ioconnectormock.exception.IoMessageIdFormatException se l'id è malformato
     */
    public ExternalMessageResponseWithContent getMessage(String fiscalCode, String id) {
        log.logStartingProcess(GET_MESSAGE);
        MDC.put(MDC_IO_MESSAGE_ID, id);
        try {

            IoMessageId decodeMessageId = IoMessageIdCodec.decode(id);

            Sequence sequence = sequenceProvider.getSequence(decodeMessageId.sequenceName())
                    .orElseThrow(() -> new MessageNotFoundException(
                            "Sequence '" + decodeMessageId.sequenceName() + "' not found in registry"));

            // Secondi trascorsi dall'invio
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

    /** Calcola lo snapshot di stato azzerando eventuali valori negativi */
    private StateSnapshot getStatusSnapshot(Sequence sequence, long elapsedSeconds) {
        long clampedElapsedSeconds = Math.max(0, elapsedSeconds);
        return sequenceEngine.computeSnapshot(sequence, clampedElapsedSeconds);
    }

    /**
     * Estrae il nome della sequenza dal marker {@code @io:<sequenceName>} nel subject.
     *
     * @return il nome della sequenza, vuoto se il subject è assente o privo di marker
     */
    public Optional<String> extractMarker(String subject) {
        if (!StringUtils.hasText(subject)) {
            return Optional.empty();
        }
        Matcher matcher = MARKER.matcher(subject);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

}
