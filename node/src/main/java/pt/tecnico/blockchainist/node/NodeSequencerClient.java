package pt.tecnico.blockchainist.node;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.DeliverBlockRequest;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.node.domain.NodeState;

public class NodeSequencerClient implements Runnable {

    private final SequencerServiceGrpc.SequencerServiceBlockingStub stub;
    private final NodeState nodeState;
    private final Map<String, CompletableFuture<Throwable>> pendingTransactions;
    private final Map<String, RequestResult> completedTransactions;
    private int nextBlockNumber = 0;

    public NodeSequencerClient(
            SequencerServiceGrpc.SequencerServiceBlockingStub stub,
            NodeState nodeState,
            Map<String, CompletableFuture<Throwable>> pendingTransactions,
            Map<String, RequestResult> completedTransactions) {
        this.stub = stub;
        this.nodeState = nodeState;
        this.pendingTransactions = pendingTransactions;
        this.completedTransactions = completedTransactions;
    }

    public int syncInitialBlocks() {
        return drainAvailableBlocks(0);
    }

    public void setNextBlockNumber(int nextBlockNumber) {
        if (nextBlockNumber < 0) {
            throw new IllegalArgumentException("nextBlockNumber cannot be negative");
        }
        this.nextBlockNumber = nextBlockNumber;
    }

    @Override
    public void run() {
        while (true) {
            try {
                int drainedUntilBlock = drainAvailableBlocks(nextBlockNumber);
                if (drainedUntilBlock == nextBlockNumber) {
                    Thread.sleep(100); // Polling interval
                } else {
                    nextBlockNumber = drainedUntilBlock;
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

    private void processBlock(Block block) {
        for (Transaction transaction : block.getTransactionsList()) {
            processTransaction(transaction);
        }
        nodeState.addBlock(block);
    }

    private void processTransaction(Transaction transaction) {
        Throwable error = null;
        String requestId = getRequestId(transaction);
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

        if (requestId != null && !requestId.isBlank()) {
            completedTransactions.put(requestId, error == null ? RequestResult.success() : RequestResult.failure(error));
        }

        CompletableFuture<Throwable> future = requestId == null || requestId.isBlank()
                ? null
                : pendingTransactions.remove(requestId);
        if (future != null) {
            future.complete(error);
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
