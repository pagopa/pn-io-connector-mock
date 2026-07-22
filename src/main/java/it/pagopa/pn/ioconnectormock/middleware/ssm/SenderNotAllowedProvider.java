package it.pagopa.pn.ioconnectormock.middleware.ssm;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import it.pagopa.pn.ioconnectormock.exception.SsmParameterMappingException;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@CustomLog
public class SenderNotAllowedProvider {

    private final ParameterizedCachedSsmParameterConsumer parameterConsumer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String parameterName;

    public SenderNotAllowedProvider(ParameterizedCachedSsmParameterConsumer parameterConsumer,
                                    PnIoConnectorMockConfig config) {
        this.parameterConsumer = parameterConsumer;
        this.parameterName = config.getSenderNotAllowedParameterName();
    }

    public boolean isDenied(String fiscalCode) {
        if (!StringUtils.hasText(fiscalCode)) {
            return false;
        }
        Set<String> deniedFiscalCodes = parameterConsumer.getParameterValue(parameterName, this::parseDenyList)
                .orElseGet(Set::of);
        return deniedFiscalCodes.contains(fiscalCode.trim());
    }

    private Set<String> parseDenyList(String json) {
        try {
            String[] fiscalCodes = objectMapper.readValue(json, String[].class);
            return Arrays.stream(fiscalCodes)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (JacksonException e) {
            throw new SsmParameterMappingException(
                    "Deny-list parameter '" + parameterName + "' is not a valid JSON array of strings", e);
        }
    }
}
