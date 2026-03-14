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
    private final Map<Transaction, CompletableFuture<Throwable>> pendingTransactions;
    private int nextBlockNumber = 0;

    public NodeSequencerClient(SequencerServiceGrpc.SequencerServiceBlockingStub stub, NodeState nodeState, Map<Transaction, CompletableFuture<Throwable>> pendingTransactions) {
        this.stub = stub;
        this.nodeState = nodeState;
        this.pendingTransactions = pendingTransactions;
    }

    @Override
    public void run() {
        while (true) {
            try {
                DeliverBlockRequest request = DeliverBlockRequest.newBuilder()
                        .setBlockNumber(nextBlockNumber)
                        .build();
                DeliverBlockResponse response = stub.deliverBlock(request);

                if (response.hasBlock()) {
                    Block block = response.getBlock();
                    for (Transaction transaction : block.getTransactionsList()) {
                        processTransaction(transaction);
                    }
                    nodeState.addBlock(block);
                    nextBlockNumber++;
                } else {
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

    private void processTransaction(Transaction transaction) {
        Throwable error = null;
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
        
        CompletableFuture<Throwable> future = pendingTransactions.remove(transaction);
        if (future != null) {
            future.complete(error);
        }
    }
}
