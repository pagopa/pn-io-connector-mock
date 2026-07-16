package it.pagopa.pn.ioconnectormock.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Data
@Validated
@Configuration
@Import(SharedAutoConfiguration.class)
@ConfigurationProperties(prefix = "pn.ioconnectormock")
public class PnIoConnectorMockConfig {

    private List<String> mockSequenceParameterNames;
    private String senderNotAllowedParameterName;
    private Duration ssmCacheTtl;
}
