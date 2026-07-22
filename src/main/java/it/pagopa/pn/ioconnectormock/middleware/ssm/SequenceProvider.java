package it.pagopa.pn.ioconnectormock.middleware.ssm;

import it.pagopa.pn.ioconnectormock.config.PnIoConnectorMockConfig;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.service.SequenceParser;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@CustomLog
public class SequenceProvider {

    private final ParameterizedCachedSsmParameterConsumer parameterConsumer;
    private final SequenceParser sequenceParser;
    private final List<String> parameterNames;

    public SequenceProvider(ParameterizedCachedSsmParameterConsumer parameterConsumer,
                            SequenceParser sequenceParser,
                            PnIoConnectorMockConfig config) {
        this.parameterConsumer = parameterConsumer;
        this.sequenceParser = sequenceParser;
        this.parameterNames = config.getMockSequenceParameterNames();
    }

    public Optional<Sequence> getSequence(String sequenceName) {
        if (sequenceName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mergedSequences().get(sequenceName));
    }

    private Map<String, Sequence> mergedSequences() {
        Map<String, Sequence> merged = new HashMap<>();
        for (String parameterName : parameterNames) {
            parameterConsumer.getParameterValue(parameterName, sequenceParser::parse)
                    .ifPresent(sequences -> mergeInto(merged, parameterName, sequences));
        }
        return merged;
    }

    private void mergeInto(Map<String, Sequence> merged, String parameterName, List<Sequence> sequences) {
        for (Sequence sequence : sequences) {
            Sequence previous = merged.put(sequence.sequenceName(), sequence);
            if (previous != null) {
                log.warn("sequence_duplicate name={} overridden by parameter={}",
                        sequence.sequenceName(), parameterName);
            }
        }
    }
}
