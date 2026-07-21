package it.pagopa.pn.ioconnectormock.model;

import java.util.List;

public record Sequence(String sequenceName, List<SequenceStep> steps) {

    public Sequence {
        steps = List.copyOf(steps);
    }
}
