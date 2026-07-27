package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.PaymentStatus;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ReadStatus;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.SequenceStep;
import it.pagopa.pn.ioconnectormock.model.StateSnapshot;
import org.springframework.stereotype.Component;

@Component
public class SequenceEngine {

    /**
     * Restituisce lo snapshot risultante dall'applicazione, in ordine, di tutti gli
     * step con {@code afterSeconds <= elapsedSeconds}. Ogni campo conserva l'ultimo
     * valore non-null incontrato.
     *
     * @param elapsedSeconds secondi trascorsi dall'invio del messaggio
     */
    public StateSnapshot computeSnapshot(Sequence sequence, long elapsedSeconds) {
        MessageStatusValue status = null;
        ReadStatus readStatus = null;
        PaymentStatus paymentStatus = null;
        for (SequenceStep step : sequence.steps()) {
            // Gli step sono ordinati per afterSeconds: al primo step futuro si interrompe.
            if (step.afterSeconds() > elapsedSeconds) {
                break;
            }
            // Ogni campo viene aggiornato solo se lo step lo valorizza
            if (step.status() != null) {
                status = step.status();
            }
            if (step.readStatus() != null) {
                readStatus = step.readStatus();
            }
            if (step.paymentStatus() != null) {
                paymentStatus = step.paymentStatus();
            }
        }
        return new StateSnapshot(status, readStatus, paymentStatus);
    }
}
