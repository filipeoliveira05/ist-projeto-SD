package pt.tecnico.blockchainist.node;

import java.security.PublicKey;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.DeliverBlockRequest;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.crypto.CryptoUtils;
import pt.tecnico.blockchainist.node.domain.NodeState;

/**
 * Polls the sequencer for new blocks and processes them locally.
 * Runs as a background thread after initial synchronization.
 * When a transaction is processed, it completes the corresponding
 * CompletableFuture so that the waiting gRPC handler can respond to the client.
 */
public class NodeSequencerClient implements Runnable {

    private final SequencerServiceGrpc.SequencerServiceBlockingStub stub;
    private final NodeState nodeState;

    // Shared with NodeServiceImpl: futures awaiting block delivery.
    private final Map<String, CompletableFuture<Throwable>> pendingTransactions;
    
    // Shared with NodeServiceImpl: cached results for idempotent retries.
    private final Map<String, RequestResult> completedTransactions;

    // C.1: Tracks requestIds of transfers applied speculatively by NodeServiceImpl.
    private final Set<String> speculativeTransfers;

    // C.2: Sequencer's public key for block signature verification.
    private final PublicKey sequencerPublicKey;

    // C.1: Lock shared with NodeServiceImpl for causal dependency notification.
    private Object dependencyLock;

    // The next block number this node expects from the sequencer.
    private int nextBlockNumber = 0;

    public NodeSequencerClient(
            SequencerServiceGrpc.SequencerServiceBlockingStub stub,
            NodeState nodeState,
            Map<String, CompletableFuture<Throwable>> pendingTransactions,
            Map<String, RequestResult> completedTransactions,
            Set<String> speculativeTransfers,
            PublicKey sequencerPublicKey) {
        this.stub = stub;
        this.nodeState = nodeState;
        this.pendingTransactions = pendingTransactions;
        this.completedTransactions = completedTransactions;
        this.speculativeTransfers = speculativeTransfers;
        this.sequencerPublicKey = sequencerPublicKey;
    }

    /** C.1: Set the dependency lock shared with NodeServiceImpl for causal notifications. */
    public void setDependencyLock(Object dependencyLock) {
        this.dependencyLock = dependencyLock;
    }

    /** Fetch all existing blocks from the sequencer (used at startup for B.2 sync). */
    public int syncInitialBlocks() {
        return drainAvailableBlocks(0);
    }

    public synchronized void setNextBlockNumber(int nextBlockNumber) {
        if (nextBlockNumber < 0) {
            throw new IllegalArgumentException("nextBlockNumber cannot be negative");
        }
        this.nextBlockNumber = nextBlockNumber;
    }

    /** Process all available blocks up to the latest; used by readBalance/getBlockchainState. */
    public synchronized int catchUpToLatest() {
        catchUpFromCurrentPosition();
        return nextBlockNumber;
    }

    /** Background polling loop: continuously fetches new blocks from the sequencer. */
    @Override
    public void run() {
        while (true) {
            try {
                boolean advanced = catchUpFromCurrentPosition();
                if (!advanced) {
                    Thread.sleep(100); // Polling interval
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in sequencer polling loop: " + e.getMessage());
                try {
                    Thread.sleep(1000); // Backoff on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private synchronized boolean catchUpFromCurrentPosition() {
        int previousBlockNumber = nextBlockNumber;
        nextBlockNumber = drainAvailableBlocks(previousBlockNumber);
        return nextBlockNumber != previousBlockNumber;
    }

    /** Request blocks sequentially until the sequencer has no more to deliver. */
    private int drainAvailableBlocks(int startBlockNumber) {
        int currentBlockNumber = startBlockNumber;
        while (true) {
            DeliverBlockRequest request = DeliverBlockRequest.newBuilder()
                    .setBlockNumber(currentBlockNumber)
                    .build();
            DeliverBlockResponse response = stub.deliverBlock(request);
            if (!response.hasBlock()) {
                return currentBlockNumber;
            }
            processBlock(response.getBlock());
            currentBlockNumber++;
        }
    }

    /** Execute each transaction in the block and append the block to the local blockchain. */
    private void processBlock(Block block) {
        // C.2: Verify block signature before processing.
        if (!verifyBlockSignature(block)) {
            System.err.println("WARNING: Block " + block.getBlockNumber()
                    + " has invalid signature! Skipping block.");
            return;
        }
        for (Transaction transaction : block.getTransactionsList()) {
            processTransaction(transaction);
        }
        nodeState.addBlock(block);
    }

    /**
     * C.2: Verify the sequencer's digital signature on a block.
     * Rebuilds the unsigned block (clearing the signature field) and
     * verifies the signature against the serialized unsigned bytes.
     */
    private boolean verifyBlockSignature(Block block) {
        if (sequencerPublicKey == null) {
            return true; // No key = no verification.
        }
        Block unsigned = block.toBuilder().clearSignature().build();
        return CryptoUtils.verify(
                sequencerPublicKey,
                unsigned.toByteArray(),
                block.getSignature().toByteArray());
    }

    /**
     * Apply a single transaction to the local state and notify any waiting handler.
     * Stores the result in completedTransactions for idempotent retry support.
     * C.1: Transfers already applied speculatively are not re-executed.
     */
    private void processTransaction(Transaction transaction) {
        Throwable error = null;
        String requestId = getRequestId(transaction);

        // C.1: If this transfer was already applied speculatively, skip re-execution.
        boolean alreadySpeculative = requestId != null && !requestId.isBlank()
                && speculativeTransfers.remove(requestId);

        if (!alreadySpeculative) {
            try {
                switch (transaction.getOperationCase()) {
                    case CREATE_WALLET:
                        nodeState.createWallet(transaction.getCreateWallet().getUserId(), transaction.getCreateWallet().getWalletId());
                        break;
                    case DELETE_WALLET:
                        nodeState.deleteWallet(transaction.getDeleteWallet().getUserId(), transaction.getDeleteWallet().getWalletId());
                        break;
                    case TRANSFER:
                        nodeState.transfer(transaction.getTransfer().getSrcUserId(), transaction.getTransfer().getSrcWalletId(),
                                transaction.getTransfer().getDstWalletId(), transaction.getTransfer().getValue());
                        break;
                    default:
                        System.out.println("Unknown operation: " + transaction.getOperationCase());
                }
                System.out.println("Processed transaction: " + transaction);
            } catch (Exception e) {
                error = e;
                System.err.println("Error processing transaction: " + e.getMessage());
            }
        } else {
            System.out.println("Skipped speculative transfer (already applied): " + requestId);
        }

        // Cache the result so that retried requests can return immediately.
        if (requestId != null && !requestId.isBlank()) {
            completedTransactions.putIfAbsent(requestId,
                    error == null ? RequestResult.success() : RequestResult.failure(error));
        }

        // Complete the pending future to unblock the gRPC handler thread.
        CompletableFuture<Throwable> future = requestId == null || requestId.isBlank()
                ? null
                : pendingTransactions.remove(requestId);
        if (future != null) {
            future.complete(error);
        }

        // C.1: Notify threads waiting on causal dependencies.
        if (dependencyLock != null) {
            synchronized (dependencyLock) {
                dependencyLock.notifyAll();
            }
        }
    }

    private String getRequestId(Transaction transaction) {
        return switch (transaction.getOperationCase()) {
            case CREATE_WALLET -> transaction.getCreateWallet().getRequestId();
            case DELETE_WALLET -> transaction.getDeleteWallet().getRequestId();
            case TRANSFER -> transaction.getTransfer().getRequestId();
            default -> "";
        };
    }
}
