package com.soymods.oreveil.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ObfuscationMetricsTest {
    @Test
    void accumulatesRewriteAndFallbackCounters() {
        ObfuscationMetrics metrics = new ObfuscationMetrics();

        metrics.recordBlockChangePacketRewrite();
        metrics.recordMultiBlockPacketRewrite(3);
        metrics.recordChunkPacketRewrite(7);
        metrics.recordChunkRewriteFailure();
        metrics.recordChunkPacketPrimed();
        metrics.recordChunkPrimeCorrection();
        metrics.recordSyntheticBlockChange();
        metrics.recordMultiBlockRewriteFailure();

        ObfuscationMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.blockChangePacketsRewritten());
        assertEquals(1, snapshot.multiBlockPacketsRewritten());
        assertEquals(3, snapshot.multiBlockEntriesRewritten());
        assertEquals(1, snapshot.chunkPacketsRewritten());
        assertEquals(7, snapshot.chunkBlockEntriesRewritten());
        assertEquals(1, snapshot.chunkRewriteFailures());
        assertEquals(1, snapshot.chunkPacketsPrimed());
        assertEquals(1, snapshot.chunkPrimeCorrectionsSent());
        assertEquals(1, snapshot.syntheticBlockChangesSent());
        assertEquals(1, snapshot.multiBlockRewriteFailures());
    }
}
