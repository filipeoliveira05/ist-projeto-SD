package pt.tecnico.blockchainist.sequencer.domain;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.crypto.CryptoUtils;

/**
 * Manages the sequencer's transaction ordering and block creation.
 * Transactions are grouped into blocks and closed when either:
 * - The maximum number of transactions per block (N) is reached, or
 * - A timeout (T seconds) expires since the first transaction in the block.
 * Supports requestId-based deduplication for idempotent retries (B.2).
 */
public class SequencerState {

    private final List<Block> completedBlocks = new ArrayList<>();
    private List<Transaction> currentBlockTransactions = new ArrayList<>();

    // flat list for A.2 compatibility
    private final List<Transaction> allTransactions = new ArrayList<>();

    // requestId -> seqNumber (dedup)
    private final Map<String, Integer> requestSequenceNumbers = new HashMap<>();
    
    // N
    private final int maxTransactionsPerBlock;
    
    // T (in milliseconds)
    private final long blockTimeoutMs;           
    
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTimer;

    // C.2: Sequencer's private key for signing blocks.
    private final PrivateKey sequencerPrivateKey;

    public SequencerState(int maxTransactionsPerBlock, int blockTimeoutSeconds, PrivateKey sequencerPrivateKey) {
        this.maxTransactionsPerBlock = maxTransactionsPerBlock;
        this.blockTimeoutMs = blockTimeoutSeconds * 1000L;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.sequencerPrivateKey = sequencerPrivateKey;
    }

    /**
     * Add a transaction to the current block. If this is a duplicate requestId,
     * return the existing sequence number without re-adding the transaction.
     * Starts the block timeout timer on the first transaction of a new block.
     */
    public synchronized int addTransaction(Transaction transaction) {
        // Deduplication: return existing sequence number for retried requests.
        String requestId = getRequestId(transaction);
        if (requestId != null && !requestId.isBlank()) {
            Integer existingSequenceNumber = requestSequenceNumbers.get(requestId);
            if (existingSequenceNumber != null) {
                return existingSequenceNumber;
            }
        }

        int seqNumber = allTransactions.size();
        allTransactions.add(transaction);
        if (requestId != null && !requestId.isBlank()) {
            requestSequenceNumbers.put(requestId, seqNumber);
        }
        
        // Start the block timeout timer when the first transaction arrives.
        boolean wasEmpty = currentBlockTransactions.isEmpty();
        currentBlockTransactions.add(transaction);
        
        if (wasEmpty) {
            currentTimer = scheduler.schedule(
                this::closeCurrentBlock, 
                blockTimeoutMs, 
                TimeUnit.MILLISECONDS
            );
        }
        
        // Close the block immediately if it reached the maximum size (N).
        if (currentBlockTransactions.size() == maxTransactionsPerBlock) {
            closeCurrentBlock();
        }
        
        return seqNumber;
    }

    /** Extract the requestId from a transaction regardless of its operation type. */
    private String getRequestId(Transaction transaction) {
        return switch (transaction.getOperationCase()) {
            case CREATE_WALLET -> transaction.getCreateWallet().getRequestId();
            case DELETE_WALLET -> transaction.getDeleteWallet().getRequestId();
            case TRANSFER -> transaction.getTransfer().getRequestId();
            default -> "";
        };
    }

    /**
     * Finalize the current block: assign it a sequential block number,
     * add it to the completed list, and cancel any pending timeout timer.
     */
    public synchronized void closeCurrentBlock() {
        if (currentBlockTransactions.isEmpty()) {
            return;
        }

        // Build the block without signature first.
        Block unsignedBlock = Block.newBuilder()
                .setBlockNumber(completedBlocks.size())
                .addAllTransactions(currentBlockTransactions)
                .build();

        // C.2: Sign the serialized unsigned block bytes.
        byte[] signature = CryptoUtils.sign(sequencerPrivateKey, unsignedBlock.toByteArray());

        // Rebuild the block with the signature included.
        Block signedBlock = unsignedBlock.toBuilder()
                .setSignature(ByteString.copyFrom(signature))
                .build();

        completedBlocks.add(signedBlock);
        currentBlockTransactions = new ArrayList<>();
        
        if (currentTimer != null && !currentTimer.isDone()) {
            currentTimer.cancel(false);
        }
        
        notifyAll();
    }
    
    /** Return the block with the given number, or null if not yet available. */
    public synchronized Block getBlock(int blockNumber) {
        if (blockNumber >= 0 && blockNumber < completedBlocks.size()) {
            return completedBlocks.get(blockNumber);
        }
        return null;
    }

    /**
     * Return the block with the given number, blocking until it is available.
     * The caller thread will wait() until closeCurrentBlock() calls notifyAll().
     */
    public synchronized Block getBlockBlocking(int blockNumber) throws InterruptedException {
        while (blockNumber < 0 || blockNumber >= completedBlocks.size()) {
            wait();
        }
        return completedBlocks.get(blockNumber);
    }

    /** Return the transaction at the given sequence number, or null if not yet available (A.2). */
    public synchronized Transaction getNextTransaction(int lastSeenSeqNumber) {
        // If the node has seen 'lastSeenSeqNumber' transactions, 
        // the next one is at index 'lastSeenSeqNumber' (since IDs are 1-based and indices 0-based).
        if (lastSeenSeqNumber >= 0 && lastSeenSeqNumber < allTransactions.size()) {
            return allTransactions.get(lastSeenSeqNumber);
        }
        return null;
    }
}
