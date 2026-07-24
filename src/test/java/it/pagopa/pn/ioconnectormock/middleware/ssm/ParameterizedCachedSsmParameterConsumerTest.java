package it.pagopa.pn.ioconnectormock.middleware.ssm;

import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParameterizedCachedSsmParameterConsumerTest {

    private static final String PARAM = "MapIoConnectorMockSequence";
    private static final Function<String, String> IDENTITY = Function.identity();

    private SsmClient ssmClient;

    @BeforeEach
    void setUp() {
        ssmClient = mock(SsmClient.class);
    }

    private ParameterizedCachedSsmParameterConsumer consumerWithTtl(Duration ttl) {
        PnIoConnectorMockConfig config = new PnIoConnectorMockConfig();
        config.setSsmCacheTtl(ttl);
        return new ParameterizedCachedSsmParameterConsumer(ssmClient, config);
    }

    @Test
    void readsSsmOnceWithinTtl() {
        ssmReturns("v1");
        ParameterizedCachedSsmParameterConsumer consumer = consumerWithTtl(Duration.ofMinutes(5));

        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).contains("v1");
        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).contains("v1");

        verify(ssmClient, times(1)).getParameter(any(GetParameterRequest.class));
    }

    @Test
    void refreshesWhenEntryExpired() {
        ssmReturns("v1");
        ParameterizedCachedSsmParameterConsumer consumer = consumerWithTtl(Duration.ZERO);

        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).contains("v1");
        ssmReturns("v2");
        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).contains("v2");

        verify(ssmClient, times(2)).getParameter(any(GetParameterRequest.class));
    }

    @Test
    void keepsLastKnownGoodWhenSsmUnreadable() {
        ssmReturns("v1");
        ParameterizedCachedSsmParameterConsumer consumer = consumerWithTtl(Duration.ZERO);
        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).contains("v1");

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("boom").build());

        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).contains("v1");
    }

    @Test
    void keepsLastKnownGoodWhenValueMalformed() {
        Function<String, Integer> parseInt = Integer::parseInt;
        ssmReturns("42");
        ParameterizedCachedSsmParameterConsumer consumer = consumerWithTtl(Duration.ZERO);
        assertThat(consumer.getParameterValue(PARAM, parseInt)).contains(42);

        ssmReturns("not-a-number"); // mapper will throw on refresh
        assertThat(consumer.getParameterValue(PARAM, parseInt)).contains(42);
    }

    @Test
    void coldStartFailureReturnsEmptyAndDoesNotCache() {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("missing").build());
        ParameterizedCachedSsmParameterConsumer consumer = consumerWithTtl(Duration.ofMinutes(5));

        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).isEmpty();

        assertThat(consumer.getParameterValue(PARAM, IDENTITY)).isEmpty();

        verify(ssmClient, times(2)).getParameter(any(GetParameterRequest.class));
    }

    @Test
    void deserializesJsonForClassContractMethod() {
        ssmReturns("[\"AAA\",\"BBB\"]");
        ParameterizedCachedSsmParameterConsumer consumer = consumerWithTtl(Duration.ofMinutes(5));

        Optional<String[]> value = consumer.getParameterValue(PARAM, String[].class);

        assertThat(value).isPresent();
        assertThat(value.get()).containsExactly("AAA", "BBB");
    }

    private void ssmReturns(String value) {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(GetParameterResponse.builder()
                        .parameter(Parameter.builder().value(value).build())
                        .build());
    }
}
