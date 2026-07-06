package com.soymods.oreveil.obfuscation;

import java.util.concurrent.atomic.AtomicLong;

public final class ObfuscationMetrics {
    private final AtomicLong blockChangePacketsRewritten = new AtomicLong();
    private final AtomicLong multiBlockPacketsRewritten = new AtomicLong();
    private final AtomicLong multiBlockEntriesRewritten = new AtomicLong();
    private final AtomicLong chunkPacketsPrimed = new AtomicLong();
    private final AtomicLong chunkPrimeCorrectionsSent = new AtomicLong();
    private final AtomicLong syntheticBlockChangesSent = new AtomicLong();
    private final AtomicLong multiBlockRewriteFailures = new AtomicLong();

    public void recordBlockChangePacketRewrite() {
        blockChangePacketsRewritten.incrementAndGet();
    }

    public void recordMultiBlockPacketRewrite(int rewrittenEntries) {
        multiBlockPacketsRewritten.incrementAndGet();
        multiBlockEntriesRewritten.addAndGet(rewrittenEntries);
    }

    public void recordChunkPacketPrimed() {
        chunkPacketsPrimed.incrementAndGet();
    }

    public void recordChunkPrimeCorrection() {
        chunkPrimeCorrectionsSent.incrementAndGet();
    }

    public void recordSyntheticBlockChange() {
        syntheticBlockChangesSent.incrementAndGet();
    }

    public void recordMultiBlockRewriteFailure() {
        multiBlockRewriteFailures.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(
            blockChangePacketsRewritten.get(),
            multiBlockPacketsRewritten.get(),
            multiBlockEntriesRewritten.get(),
            chunkPacketsPrimed.get(),
            chunkPrimeCorrectionsSent.get(),
            syntheticBlockChangesSent.get(),
            multiBlockRewriteFailures.get()
        );
    }

    public record Snapshot(
        long blockChangePacketsRewritten,
        long multiBlockPacketsRewritten,
        long multiBlockEntriesRewritten,
        long chunkPacketsPrimed,
        long chunkPrimeCorrectionsSent,
        long syntheticBlockChangesSent,
        long multiBlockRewriteFailures
    ) {
    }
}
