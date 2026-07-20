package it.pagopa.pn.ioconnectormock.service;

import it.pagopa.pn.ioconnectormock.model.Sequence;
import it.pagopa.pn.ioconnectormock.model.StateSnapshot;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class IOMockService {

    private final SequenceEngine sequenceEngine;

    public StateSnapshot getStatusSnapshot(Sequence sequence, long elapsedSeconds) {
        long clampedElapsedSeconds = Math.max(0, elapsedSeconds);
        StateSnapshot snapshot = sequenceEngine.computeSnapshot(sequence, clampedElapsedSeconds);
        log.info("snapshot_computed sequenceName={} elapsedSeconds={} status={} readStatus={} paymentStatus={}",
                sequence.sequenceName(), clampedElapsedSeconds,
                snapshot.status(), snapshot.readStatus(), snapshot.paymentStatus());
        return snapshot;
    }
}
