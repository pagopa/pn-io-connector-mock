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

    public StateSnapshot computeSnapshot(Sequence sequence, long elapsedSeconds) {
        MessageStatusValue status = null;
        ReadStatus readStatus = null;
        PaymentStatus paymentStatus = null;
        for (SequenceStep step : sequence.steps()) {
            if (step.afterSeconds() > elapsedSeconds) {
                break;
            }
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
