package pt.tecnico.blockchainist.sequencer.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.Transaction;

public class SequencerState {

    private final List<Block> completedBlocks = new ArrayList<>();
    private List<Transaction> currentBlockTransactions = new ArrayList<>();
    private final List<Transaction> allTransactions = new ArrayList<>(); // kept for retrocompatibility
    
    private final int maxTransactionsPerBlock;
    private final long blockTimeoutMs;
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTimer;

    public SequencerState(int maxTransactionsPerBlock, int blockTimeoutSeconds) {
        this.maxTransactionsPerBlock = maxTransactionsPerBlock;
        this.blockTimeoutMs = blockTimeoutSeconds * 1000L;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public synchronized int addTransaction(Transaction transaction) {
        int seqNumber = allTransactions.size();
        allTransactions.add(transaction);
        
        boolean wasEmpty = currentBlockTransactions.isEmpty();
        currentBlockTransactions.add(transaction);
        
        if (wasEmpty) {
            currentTimer = scheduler.schedule(
                this::closeCurrentBlock, 
                blockTimeoutMs, 
                TimeUnit.MILLISECONDS
            );
        }
        
        if (currentBlockTransactions.size() == maxTransactionsPerBlock) {
            closeCurrentBlock();
        }
        
        return seqNumber;
    }

    public synchronized void closeCurrentBlock() {
        if (currentBlockTransactions.isEmpty()) {
            return;
        }

        Block block = Block.newBuilder()
                .setBlockNumber(completedBlocks.size())
                .addAllTransactions(currentBlockTransactions)
                .build();
                
        completedBlocks.add(block);
        currentBlockTransactions = new ArrayList<>();
        
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
        }
        
        notifyAll();
    }
    
    public synchronized Block getBlock(int blockNumber) {
        if (blockNumber >= 0 && blockNumber < completedBlocks.size()) {
            return completedBlocks.get(blockNumber);
        }
        return null;
    }

    public synchronized Transaction getNextTransaction(int lastSeenSeqNumber) {
        // If the node has seen 'lastSeenSeqNumber' transactions, 
        // the next one is at index 'lastSeenSeqNumber' (since IDs are 1-based and indices 0-based).
        if (lastSeenSeqNumber >= 0 && lastSeenSeqNumber < allTransactions.size()) {
            return allTransactions.get(lastSeenSeqNumber);
        }
        return null;
    }
}