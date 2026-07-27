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

/**
 * Consumer di parametri SSM con cache TTL per singolo parametro.
 */
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

    /**
     * Recupera il parametro deserializzandone il valore JSON nel tipo indicato.
     *
     * @param targetType type di destinazione per la deserializzazione JSON
     * @return il valore in cache o appena letto, vuoto se non disponibile
     */
    @Override
    public <T> Optional<T> getParameterValue(String parameterName, Class<T> targetType) {
        return getParameterValue(parameterName, raw -> deserialize(parameterName, raw, targetType));
    }

    /**
     * Recupera il parametro applicando un mapper specifico per il type al valore.
     *
     * @param mapper trasforma il valore nel tipo desiderato
     * @return il valore in cache o appena letto, vuoto se non disponibile
     */
    public <T> Optional<T> getParameterValue(String parameterName, Function<String, T> mapper) {
        CacheEntry entry = cache.compute(parameterName, (name, cached) -> refresh(name, cached, mapper));
        if (entry == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T value = (T) entry.value();
        return Optional.ofNullable(value);
    }

    /**
     * Ricalcola l'entry di cache: riusa quella valida, altrimenti rilegge da SSM.
     * Applica il fallback se la rilettura fallisce.
     */
    private CacheEntry refresh(String parameterName, CacheEntry cached, Function<String, ?> mapper) {
        Instant now = Instant.now();
        // Cache ancora valida, ritorno il valore
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached;
        }
        try {
            // Refresh della cache: nuovo valore con TTL aggiornato
            String raw = readRawParameter(parameterName);
            Object value = mapper.apply(raw);
            log.info("ssm_parameter_refreshed name={} outcome=OK", parameterName);
            return new CacheEntry(value, now.plus(cacheTtl));
        } catch (RuntimeException e) {
            // Refresh fallito ma esiste un valore precedente, lo si mantengo e aggiorno il TTL
            if (cached != null) {
                log.error("ssm_parameter_refresh_failed name={} outcome=KEEP_LAST_KNOWN_GOOD: {}",
                        parameterName, e.getMessage(), e);
                return new CacheEntry(cached.value(), now.plus(cacheTtl));
            }
            // Refresh fallito e nessun valore in cache, ritorno null
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

    /**
     * Deserializza il valore del parametro nel tipo richiesto.
     *
     * @throws SsmParameterMappingException se il valore non è JSON valido per il tipo
     */
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
