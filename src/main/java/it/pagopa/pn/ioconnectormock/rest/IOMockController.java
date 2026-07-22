package it.pagopa.pn.ioconnectormock.rest;

import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.api.IoMockApi;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.CreatedMessage;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.NewMessage;
import it.pagopa.pn.ioconnectormock.service.IOMockService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RestController
@RequiredArgsConstructor
public class IOMockController implements IoMockApi {

    private final IOMockService ioMockService;

    @Override
    public ResponseEntity<LimitedProfile> getProfileByPOST(FiscalCodePayload fiscalCodePayload) {
        boolean senderAllowed = ioMockService.evaluateProfile(fiscalCodePayload.getFiscalCode());
        return ResponseEntity.ok(new LimitedProfile(senderAllowed));
    }

    @Override
    public ResponseEntity<CreatedMessage> submitMessageforUserWithFiscalCodeInBody(NewMessage newMessage) {
        String id = ioMockService.submitMessage(newMessage);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedMessage(id));
    }

    @Override
    public ResponseEntity<ExternalMessageResponseWithContent> getMessage(String fiscalCode, String id) {
        return ResponseEntity.ok(ioMockService.getMessage(fiscalCode, id));
    }
}
