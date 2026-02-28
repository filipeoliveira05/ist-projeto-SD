package pt.tecnico.blockchainist.node;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeliverTransactionRequest;
import pt.tecnico.blockchainist.contract.DeliverTransactionResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.node.domain.NodeState;

public class NodeSequencerClient implements Runnable {

    private final SequencerServiceGrpc.SequencerServiceBlockingStub stub;
    private final NodeState nodeState;
    private int nextSeqNumber = 0;

    public NodeSequencerClient(SequencerServiceGrpc.SequencerServiceBlockingStub stub, NodeState nodeState) {
        this.stub = stub;
        this.nodeState = nodeState;
    }

    @Override
    public void run() {
        while (true) {
            try {
                DeliverTransactionRequest request = DeliverTransactionRequest.newBuilder()
                        .setSequenceNumber(nextSeqNumber)
                        .build();
                DeliverTransactionResponse response = stub.deliverTransaction(request);

                if (response.hasTransaction()) {
                    Transaction transaction = response.getTransaction();
                    processTransaction(transaction);
                    nextSeqNumber++;
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
        nodeState.addTransaction(transaction);
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
            System.err.println("Error processing transaction: " + e.getMessage());
        }
    }
}
