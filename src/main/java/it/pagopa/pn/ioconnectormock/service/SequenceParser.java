package it.pagopa.pn.ioconnectormock.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import it.pagopa.pn.ioconnectormock.exception.SequenceValidationException;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageStatusValue;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.PaymentStatus;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.ReadStatus;
import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.SequenceStep;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Effettua il parsing e la validazione del JSON delle sequenze.
 */
@Component
@CustomLog
public class SequenceParser {

    private static final Pattern SEQUENCE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private static final ObjectReader sequenceObjectReader = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .readerFor(new TypeReference<List<RawSequence>>() {
            });

    /**
     * Converte il JSON in sequenze validate, verificando l'unicità dei nomi.
     *
     * @throws SequenceValidationException se il JSON è malformato o una sequenza non è valida
     */
    public List<Sequence> parse(String json) {
        List<RawSequence> rawSequences = readJson(json);
        Set<String> seenNames = new HashSet<>();
        List<Sequence> sequences = new ArrayList<>(rawSequences.size());
        for (RawSequence rawSequence : rawSequences) {
            Sequence sequence = toSequence(rawSequence);
            if (!seenNames.add(sequence.sequenceName())) {
                throw new SequenceValidationException("Duplicate sequenceName '" + sequence.sequenceName() + "'");
            }
            sequences.add(sequence);
        }
        return sequences;
    }

    private List<RawSequence> readJson(String json) {
        try {
            List<RawSequence> rawSequences = sequenceObjectReader.readValue(json);
            if (rawSequences == null) {
                throw new SequenceValidationException("Sequence parameter must be a JSON array, got null");
            }
            return rawSequences;
        } catch (JacksonException e) {
            throw new SequenceValidationException("Malformed sequence JSON: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Valida una singola sequenza e la converte nel modello, ordinandone gli step.
     *
     * @throws SequenceValidationException se nome, step o ordinamento non sono validi
     */
    private Sequence toSequence(RawSequence rawSequence) {
        if (rawSequence == null) {
            throw new SequenceValidationException("Sequence array must not contain null elements");
        }
        String sequenceName = rawSequence.sequenceName();
        if (!StringUtils.hasText(sequenceName) || !SEQUENCE_NAME_PATTERN.matcher(sequenceName).matches()) {
            throw new SequenceValidationException("Sequence '" + sequenceName + "': sequenceName must match [A-Za-z0-9_]{1,64}");
        }
        if (CollectionUtils.isEmpty(rawSequence.steps())) {
            throw new SequenceValidationException("Sequence '" + sequenceName + "': at least one step is required");
        }

        List<SequenceStep> steps = new ArrayList<>(rawSequence.steps().size());
        for (int i = 0; i < rawSequence.steps().size(); i++) {
            steps.add(toStep(sequenceName, i, rawSequence.steps().get(i)));
        }
        // Ordinamento per afterSeconds
        steps.sort(Comparator.comparingInt(SequenceStep::afterSeconds));

        validateOrdering(sequenceName, steps);
        validateFirstStep(sequenceName, steps.get(0));
        return new Sequence(sequenceName, steps);
    }

    /**
     * Valida e converte un singolo step: {@code afterSeconds} non negativo e almeno
     * uno tra status, readStatus, paymentStatus valorizzato.
     */
    private SequenceStep toStep(String sequenceName, int stepIndex, RawStep rawStep) {
        if (rawStep == null) {
            throw new SequenceValidationException(stepError(sequenceName, stepIndex, "step must not be null"));
        }
        if (rawStep.afterSeconds() == null || rawStep.afterSeconds() < 0) {
            throw new SequenceValidationException(stepError(sequenceName, stepIndex, "afterSeconds must be >= 0"));
        }
        if (rawStep.status() == null && rawStep.readStatus() == null && rawStep.paymentStatus() == null) {
            throw new SequenceValidationException(stepError(sequenceName, stepIndex,
                    "step must define at least one of status, readStatus, paymentStatus"));
        }
        return new SequenceStep(rawStep.afterSeconds(),
                toEnum(sequenceName, stepIndex, "status", rawStep.status(), MessageStatusValue::fromValue),
                toEnum(sequenceName, stepIndex, "readStatus", rawStep.readStatus(), ReadStatus::fromValue),
                toEnum(sequenceName, stepIndex, "paymentStatus", rawStep.paymentStatus(), PaymentStatus::fromValue));
    }

    /** Converte una stringa nel relativo enum, {@code null} se il valore è assente. */
    private <E> E toEnum(String sequenceName, int stepIndex, String field, String value, Function<String, E> fromValue) {
        if (value == null) {
            return null;
        }
        try {
            return fromValue.apply(value);
        } catch (IllegalArgumentException e) {
            throw new SequenceValidationException(stepError(sequenceName, stepIndex,
                    "invalid " + field + " '" + value + "'"), e);
        }
    }

    /** Verifica che gli step abbiano {@code afterSeconds} strettamente crescenti (nessun duplicato). */
    private void validateOrdering(String sequenceName, List<SequenceStep> sortedSteps) {
        for (int i = 1; i < sortedSteps.size(); i++) {
            if (sortedSteps.get(i).afterSeconds() == sortedSteps.get(i - 1).afterSeconds()) {
                throw new SequenceValidationException("Sequence '" + sequenceName
                        + "': duplicate afterSeconds " + sortedSteps.get(i).afterSeconds()
                        + ", steps must be strictly increasing");
            }
        }
    }

    /** Verifica che il primo step parta da {@code afterSeconds=0} e definisca uno status. */
    private void validateFirstStep(String sequenceName, SequenceStep firstStep) {
        if (firstStep.afterSeconds() != 0 || firstStep.status() == null) {
            throw new SequenceValidationException("Sequence '" + sequenceName
                    + "': first step must have afterSeconds=0 and a status");
        }
    }

    private String stepError(String sequenceName, int stepIndex, String rule) {
        return "Sequence '" + sequenceName + "' step #" + stepIndex + ": " + rule;
    }

    private record RawSequence(String sequenceName, List<RawStep> steps) {
    }

    private record RawStep(Integer afterSeconds, String status, String readStatus, String paymentStatus) {
    }
}
