package it.pagopa.pn.ioconnectormock.middleware.ssm;

import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SenderNotAllowedProviderTest {

    private static final String PARAM = "MapIoConnectorMockSenderNotAllowed";
    private static final String DENIED = "RSSMRA80A01H501U";

    private final SsmClient ssmClient = mock(SsmClient.class);

    private SenderNotAllowedProvider provider() {
        PnIoConnectorMockConfig config = new PnIoConnectorMockConfig();
        config.setSsmCacheTtl(Duration.ofMinutes(5));
        config.setSenderNotAllowedParameterName(PARAM);
        ParameterizedCachedSsmParameterConsumer consumer =
                new ParameterizedCachedSsmParameterConsumer(ssmClient, config);
        return new SenderNotAllowedProvider(consumer, config);
    }

    @Test
    void deniesExactMatch() {
        ssmReturns("[\"" + DENIED + "\",\"OTHERCF00A00A000A\"]");
        SenderNotAllowedProvider provider = provider();

        assertThat(provider.isDenied(DENIED)).isTrue();
        assertThat(provider.isDenied("NOTINLIST00A00A00")).isFalse();
    }

    @Test
    void trimsBeforeMatching() {
        ssmReturns("[\"" + DENIED + "\"]");
        SenderNotAllowedProvider provider = provider();

        assertThat(provider.isDenied("  " + DENIED + "  ")).isTrue();
    }

    @Test
    void allowsBlankOrNullFiscalCode() {
        ssmReturns("[\"" + DENIED + "\"]");
        SenderNotAllowedProvider provider = provider();

        assertThat(provider.isDenied(null)).isFalse();
        assertThat(provider.isDenied("  ")).isFalse();
    }

    @Test
    void emptyDenyListAllowsEveryone() {
        ssmReturns("[]");
        SenderNotAllowedProvider provider = provider();

        assertThat(provider.isDenied(DENIED)).isFalse();
    }

    @Test
    void malformedDenyListAllowsEveryoneWithoutCrashing() {
        ssmReturns("not-a-json-array");
        SenderNotAllowedProvider provider = provider();

        assertThat(provider.isDenied(DENIED)).isFalse();
    }

    private void ssmReturns(String value) {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(GetParameterResponse.builder()
                        .parameter(Parameter.builder().value(value).build())
                        .build());
    }
}
