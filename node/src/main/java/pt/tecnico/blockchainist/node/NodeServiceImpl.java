package pt.tecnico.blockchainist.node;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.contract.BroadcastRequest;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.GetBlockchainStateRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;
import pt.tecnico.blockchainist.node.domain.NodeState;

public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {

    private final NodeState nodeState;
    private final SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub;
    private final Map<String, CompletableFuture<Throwable>> pendingTransactions;

    public NodeServiceImpl(NodeState nodeState, SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub, Map<String, CompletableFuture<Throwable>> pendingTransactions) {
        this.nodeState = nodeState;
        this.sequencerStub = sequencerStub;
        this.pendingTransactions = pendingTransactions;
    }

    private static String ensureRequestId(String requestId) {
        return (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
    }

    private static CreateWalletRequest normalizeCreateWalletRequest(CreateWalletRequest request) {
        String requestId = ensureRequestId(request.getRequestId());
        if (requestId.equals(request.getRequestId())) {
            return request;
        }
        return request.toBuilder().setRequestId(requestId).build();
    }

    private static DeleteWalletRequest normalizeDeleteWalletRequest(DeleteWalletRequest request) {
        String requestId = ensureRequestId(request.getRequestId());
        if (requestId.equals(request.getRequestId())) {
            return request;
        }
        return request.toBuilder().setRequestId(requestId).build();
    }

    private static TransferRequest normalizeTransferRequest(TransferRequest request) {
        String requestId = ensureRequestId(request.getRequestId());
        if (requestId.equals(request.getRequestId())) {
            return request;
        }
        return request.toBuilder().setRequestId(requestId).build();
    }

    private void applyDelay() {
        Integer delay = DelayInterceptor.DELAY_CTX_KEY.get();
        if (delay != null && delay > 0) {
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void createWallet(CreateWalletRequest request, StreamObserver<CreateWalletResponse> responseObserver) {
        applyDelay();
        CreateWalletRequest normalizedRequest = normalizeCreateWalletRequest(request);
        String requestId = normalizedRequest.getRequestId();
        Transaction transaction = Transaction.newBuilder().setCreateWallet(normalizedRequest).build();
        CompletableFuture<Throwable> future = new CompletableFuture<>();
        pendingTransactions.put(requestId, future);

        try {
            sequencerStub.broadcast(BroadcastRequest.newBuilder().setTransaction(transaction).build());
            Throwable error = future.get();
            if (error != null) {
                throw error;
            }
            responseObserver.onNext(CreateWalletResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            pendingTransactions.remove(requestId);
            if (e instanceof ExecutionException) e = e.getCause();
            if (e instanceof IllegalArgumentException) {
            if (e.getMessage().startsWith("Wallet already exists")) {
                responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            }
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {
        applyDelay();
        DeleteWalletRequest normalizedRequest = normalizeDeleteWalletRequest(request);
        String requestId = normalizedRequest.getRequestId();
        Transaction transaction = Transaction.newBuilder().setDeleteWallet(normalizedRequest).build();
        CompletableFuture<Throwable> future = new CompletableFuture<>();
        pendingTransactions.put(requestId, future);

        try {
            sequencerStub.broadcast(BroadcastRequest.newBuilder().setTransaction(transaction).build());
            Throwable error = future.get();
            if (error != null) {
                throw error;
            }
            responseObserver.onNext(DeleteWalletResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            pendingTransactions.remove(requestId);
            if (e instanceof ExecutionException) e = e.getCause();
            if (e instanceof IllegalArgumentException) {
            if (e.getMessage().startsWith("Wallet does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            }
        }
    }

    @Override
    public void readBalance(ReadBalanceRequest request, StreamObserver<ReadBalanceResponse> responseObserver) {
        applyDelay();
        try {
            long balance = nodeState.readBalance(request.getWalletId());
            ReadBalanceResponse response = ReadBalanceResponse.newBuilder().setBalance(balance).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Wallet does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
        }
    }

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        applyDelay();
        TransferRequest normalizedRequest = normalizeTransferRequest(request);
        String requestId = normalizedRequest.getRequestId();
        Transaction transaction = Transaction.newBuilder().setTransfer(normalizedRequest).build();
        CompletableFuture<Throwable> future = new CompletableFuture<>();
        pendingTransactions.put(requestId, future);

        try {
            sequencerStub.broadcast(BroadcastRequest.newBuilder().setTransaction(transaction).build());
            Throwable error = future.get();
            if (error != null) {
                throw error;
            }
            responseObserver.onNext(TransferResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            pendingTransactions.remove(requestId);
            if (e instanceof ExecutionException) e = e.getCause();
            if (e instanceof IllegalArgumentException) {
            if (e.getMessage().contains("does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
            } else {
                responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
            }
        }
    }

    @Override
    public void getBlockchainState(GetBlockchainStateRequest request, StreamObserver<GetBlockchainStateResponse> responseObserver) {
        GetBlockchainStateResponse response = GetBlockchainStateResponse.newBuilder()
                .addAllTransactions(nodeState.getAllTransactions())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
