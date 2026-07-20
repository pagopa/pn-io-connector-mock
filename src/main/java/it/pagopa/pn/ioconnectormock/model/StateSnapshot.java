package it.pagopa.pn.ioconnectormock.model;

import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.PaymentStatus;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ReadStatus;

public record StateSnapshot(MessageStatusValue status, ReadStatus readStatus, PaymentStatus paymentStatus) {

}
