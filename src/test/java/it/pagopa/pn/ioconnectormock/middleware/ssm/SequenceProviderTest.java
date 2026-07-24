package it.pagopa.pn.ioconnectormock.middleware.ssm;

import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.service.SequenceParser;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SequenceProviderTest {

    private static final String PARAM_1 = "MapIoConnectorMockSequence";
    private static final String PARAM_2 = "MapIoConnectorMockSequence2";

    private final SsmClient ssmClient = mock(SsmClient.class);

    private SequenceProvider providerOver(String... parameterNames) {
        PnIoConnectorMockConfig config = new PnIoConnectorMockConfig();
        config.setSsmCacheTtl(Duration.ofMinutes(5));
        config.setMockSequenceParameterNames(List.of(parameterNames));
        ParameterizedCachedSsmParameterConsumer consumer =
                new ParameterizedCachedSsmParameterConsumer(ssmClient, config);
        return new SequenceProvider(consumer, new SequenceParser(), config);
    }

    @Test
    void mergesSequencesAcrossParameters() {
        ssmReturns(PARAM_1, """
                [ { "sequenceName": "OK_READ_THEN_PAID", "steps": [ { "afterSeconds": 0, "status": "ACCEPTED" } ] },
                  { "sequenceName": "OK_READ",           "steps": [ { "afterSeconds": 0, "status": "PROCESSED" } ] } ]
                """);
        ssmReturns(PARAM_2, """
                [ { "sequenceName": "FAILED", "steps": [ { "afterSeconds": 0, "status": "FAILED" } ] } ]
                """);

        SequenceProvider provider = providerOver(PARAM_1, PARAM_2);

        assertThat(provider.getSequence("OK_READ_THEN_PAID")).map(Sequence::sequenceName).contains("OK_READ_THEN_PAID");
        assertThat(provider.getSequence("OK_READ")).isPresent();
        assertThat(provider.getSequence("FAILED")).isPresent();
    }

    @Test
    void lastParameterWinsOnDuplicateName() {
        ssmReturns(PARAM_1, """
                [ { "sequenceName": "S", "steps": [ { "afterSeconds": 0, "status": "ACCEPTED" } ] } ]
                """);
        ssmReturns(PARAM_2, """
                [ { "sequenceName": "S", "steps": [ { "afterSeconds": 0, "status": "PROCESSED" } ] } ]
                """);

        SequenceProvider provider = providerOver(PARAM_1, PARAM_2);

        assertThat(provider.getSequence("S"))
                .map(s -> s.steps().get(0).status())
                .contains(MessageStatusValue.PROCESSED);
    }

    @Test
    void unknownAndNullLookupsReturnEmpty() {
        ssmReturns(PARAM_1, """
                [ { "sequenceName": "S", "steps": [ { "afterSeconds": 0, "status": "ACCEPTED" } ] } ]
                """);

        SequenceProvider provider = providerOver(PARAM_1);

        assertThat(provider.getSequence("DOES_NOT_EXIST")).isEmpty();
        assertThat(provider.getSequence(null)).isEmpty();
    }

    @Test
    void malformedParameterDoesNotBlockOtherParameters() {
        ssmReturns(PARAM_1, """
                [ { "sequenceName": "GOOD", "steps": [ { "afterSeconds": 0, "status": "ACCEPTED" } ] } ]
                """);
        ssmReturns(PARAM_2, "this-is-not-json");

        SequenceProvider provider = providerOver(PARAM_1, PARAM_2);

        assertThat(provider.getSequence("GOOD")).isPresent();
    }

    private void ssmReturns(String parameterName, String value) {
        when(ssmClient.getParameter(argThat((GetParameterRequest r) -> r != null && parameterName.equals(r.name()))))
                .thenReturn(GetParameterResponse.builder()
                        .parameter(Parameter.builder().value(value).build())
                        .build());
    }
}
