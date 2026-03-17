package pt.tecnico.blockchainist.sequencer;

import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.contract.BroadcastRequest;
import pt.tecnico.blockchainist.contract.BroadcastResponse;
import pt.tecnico.blockchainist.contract.DeliverTransactionRequest;
import pt.tecnico.blockchainist.contract.DeliverTransactionResponse;
import pt.tecnico.blockchainist.contract.DeliverBlockRequest;
import pt.tecnico.blockchainist.contract.DeliverBlockResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.sequencer.domain.SequencerState;

/**
 * gRPC service implementation for the sequencer.
 * Receives transactions from nodes, orders them into blocks,
 * and delivers completed blocks back to nodes on request.
 */
public class SequencerServiceImpl extends SequencerServiceGrpc.SequencerServiceImplBase {

    private final SequencerState sequencerState;

    public SequencerServiceImpl(SequencerState sequencerState) {
        this.sequencerState = sequencerState;
    }

    /** Receive a transaction from a node and add it to the current block. */
    @Override
    public void broadcast(BroadcastRequest request, StreamObserver<BroadcastResponse> responseObserver) {
        int assignedSeq = sequencerState.addTransaction(request.getTransaction());
        BroadcastResponse response = BroadcastResponse.newBuilder()
                .setSequenceNumber(assignedSeq)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /** Deliver an individual transaction by sequence number (A.2 compatibility). */
    @Override
    public void deliverTransaction(DeliverTransactionRequest request, StreamObserver<DeliverTransactionResponse> responseObserver) {
        Transaction nextTransaction = sequencerState.getNextTransaction(request.getSequenceNumber());
        DeliverTransactionResponse.Builder responseBuilder = DeliverTransactionResponse.newBuilder();
        if (nextTransaction != null) {
            responseBuilder.setTransaction(nextTransaction);
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    /** Deliver a completed block by block number (B.1 block-based delivery). */
    @Override
    public void deliverBlock(DeliverBlockRequest request, StreamObserver<DeliverBlockResponse> responseObserver) {
        Block block = sequencerState.getBlock(request.getBlockNumber());
        DeliverBlockResponse.Builder responseBuilder = DeliverBlockResponse.newBuilder();
        if (block != null) {
            responseBuilder.setBlock(block);
        }
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}