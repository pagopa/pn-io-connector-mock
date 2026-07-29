package it.pagopa.pn.ioconnectormock.middleware.ssm;

import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifica se un codice fiscale è presente nella lista dei sender not allowed.
 */
@Component
@CustomLog
public class SenderNotAllowedProvider {

    private final ParameterizedCachedSsmParameterConsumer parameterConsumer;
    private final String parameterName;

    public SenderNotAllowedProvider(ParameterizedCachedSsmParameterConsumer parameterConsumer,
                                    PnIoConnectorMockConfig config) {
        this.parameterConsumer = parameterConsumer;
        this.parameterName = config.getSenderNotAllowedParameterName();
    }

    /**
     * Indica se il codice fiscale è nella lista dei sender not allowed.
     *
     * @return {@code true} se presente; {@code false} se il codice è vuoto o non elencato
     */
    public boolean isDenied(String fiscalCode) {
        if (!StringUtils.hasText(fiscalCode)) {
            return false;
        }
        Set<String> deniedFiscalCodes = parameterConsumer.getParameterValue(parameterName, this::parseDenyList)
                .orElseGet(Set::of);
        return deniedFiscalCodes.contains(fiscalCode.trim());
    }

    /** Converte l'elenco in un set di codici fiscali, ignorando i valori vuoti. */
    private Set<String> parseDenyList(String value) {
        return Arrays.stream(value.split(","))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }
}
