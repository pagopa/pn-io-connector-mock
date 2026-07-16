package it.pagopa.pn.ioconnectormock.springbootcfg;

import it.pagopa.pn.commons.configs.RuntimeMode;
import it.pagopa.pn.commons.configs.aws.AwsConfigs;
import it.pagopa.pn.commons.configs.aws.AwsServicesClientsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AwsServicesClientsConfigActivation extends AwsServicesClientsConfig {

    public AwsServicesClientsConfigActivation(AwsConfigs props) {
        super(props, RuntimeMode.PROD);
    }

}
