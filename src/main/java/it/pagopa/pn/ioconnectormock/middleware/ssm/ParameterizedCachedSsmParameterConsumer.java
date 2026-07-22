package it.pagopa.pn.ioconnectormock.middleware.ssm;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.abstractions.ParameterConsumer;
import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import it.pagopa.pn.ioconnectormock.exception.SsmParameterMappingException;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
@CustomLog
public class ParameterizedCachedSsmParameterConsumer implements ParameterConsumer {

    private final SsmClient ssmClient;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ParameterizedCachedSsmParameterConsumer(SsmClient ssmClient, PnIoConnectorMockConfig config) {
        this.ssmClient = ssmClient;
        this.cacheTtl = config.getSsmCacheTtl();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public <T> Optional<T> getParameterValue(String parameterName, Class<T> targetType) {
        return getParameterValue(parameterName, raw -> deserialize(parameterName, raw, targetType));
    }

    public <T> Optional<T> getParameterValue(String parameterName, Function<String, T> mapper) {
        CacheEntry entry = cache.compute(parameterName, (name, cached) -> refresh(name, cached, mapper));
        if (entry == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T value = (T) entry.value();
        return Optional.ofNullable(value);
    }

    private CacheEntry refresh(String parameterName, CacheEntry cached, Function<String, ?> mapper) {
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached;
        }
        try {
            String raw = readRawParameter(parameterName);
            Object value = mapper.apply(raw);
            log.info("ssm_parameter_refreshed name={} outcome=OK", parameterName);
            return new CacheEntry(value, now.plus(cacheTtl));
        } catch (RuntimeException e) {
            if (cached != null) {
                log.error("ssm_parameter_refresh_failed name={} outcome=KEEP_LAST_KNOWN_GOOD: {}",
                        parameterName, e.getMessage(), e);
                return new CacheEntry(cached.value(), now.plus(cacheTtl));
            }
            log.error("ssm_parameter_refresh_failed name={} outcome=NO_CACHED_VALUE: {}",
                    parameterName, e.getMessage(), e);
            return null;
        }
    }

    private String readRawParameter(String parameterName) {
        GetParameterRequest request = GetParameterRequest.builder().name(parameterName).build();
        String value = ssmClient.getParameter(request).parameter().value();
        if (value == null) {
            throw new SsmParameterMappingException("SSM parameter '" + parameterName + "' has a null value");
        }
        return value;
    }

    private <T> T deserialize(String parameterName, String raw, Class<T> targetType) {
        try {
            return objectMapper.readValue(raw, targetType);
        } catch (JacksonException e) {
            throw new SsmParameterMappingException(
                    "SSM parameter '" + parameterName + "' is not valid JSON for " + targetType.getSimpleName(), e);
        }
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
