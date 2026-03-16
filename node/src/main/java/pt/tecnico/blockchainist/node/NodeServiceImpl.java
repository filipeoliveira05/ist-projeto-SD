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
    private final Map<String, RequestResult> completedTransactions;

    public NodeServiceImpl(
            NodeState nodeState,
            SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub,
            Map<String, CompletableFuture<Throwable>> pendingTransactions,
            Map<String, RequestResult> completedTransactions) {
        this.nodeState = nodeState;
        this.sequencerStub = sequencerStub;
        this.pendingTransactions = pendingTransactions;
        this.completedTransactions = completedTransactions;
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

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.toString() : message;
    }

    private Throwable submitAndAwaitResult(String requestId, Transaction transaction) throws Throwable {
        RequestResult completedResult = completedTransactions.get(requestId);
        if (completedResult != null) {
            return completedResult.getError();
        }

        CompletableFuture<Throwable> candidateFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> future = pendingTransactions.putIfAbsent(requestId, candidateFuture);

        if (future == null) {
            future = candidateFuture;

            // Handle race where result was completed after the first cache check.
            completedResult = completedTransactions.get(requestId);
            if (completedResult != null) {
                pendingTransactions.remove(requestId, future);
                return completedResult.getError();
            }

            try {
                sequencerStub.broadcast(BroadcastRequest.newBuilder().setTransaction(transaction).build());
            } catch (Throwable broadcastError) {
                pendingTransactions.remove(requestId, future);
                future.completeExceptionally(broadcastError);
            }
        }

        try {
            return future.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    private void respondWithCreateWalletError(Throwable error, StreamObserver<CreateWalletResponse> responseObserver) {
        String description = describe(error);
        if (error instanceof IllegalArgumentException) {
            if (description.startsWith("Wallet already exists")) {
                responseObserver.onError(Status.ALREADY_EXISTS.withDescription(description).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException());
            }
            return;
        }
        responseObserver.onError(Status.INTERNAL.withDescription(description).asRuntimeException());
    }

    private void respondWithDeleteWalletError(Throwable error, StreamObserver<DeleteWalletResponse> responseObserver) {
        String description = describe(error);
        if (error instanceof IllegalArgumentException) {
            if (description.startsWith("Wallet does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(description).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException());
            }
            return;
        }
        responseObserver.onError(Status.INTERNAL.withDescription(description).asRuntimeException());
    }

    private void respondWithTransferError(Throwable error, StreamObserver<TransferResponse> responseObserver) {
        String description = describe(error);
        if (error instanceof IllegalArgumentException) {
            if (description.contains("does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(description).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException());
            }
            return;
        }
        responseObserver.onError(Status.INTERNAL.withDescription(description).asRuntimeException());
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

        try {
            Throwable error = submitAndAwaitResult(requestId, transaction);
            if (error != null) {
                throw error;
            }
            responseObserver.onNext(CreateWalletResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            respondWithCreateWalletError(e, responseObserver);
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {
        applyDelay();
        DeleteWalletRequest normalizedRequest = normalizeDeleteWalletRequest(request);
        String requestId = normalizedRequest.getRequestId();
        Transaction transaction = Transaction.newBuilder().setDeleteWallet(normalizedRequest).build();

        try {
            Throwable error = submitAndAwaitResult(requestId, transaction);
            if (error != null) {
                throw error;
            }
            responseObserver.onNext(DeleteWalletResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            respondWithDeleteWalletError(e, responseObserver);
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

        try {
            Throwable error = submitAndAwaitResult(requestId, transaction);
            if (error != null) {
                throw error;
            }
            responseObserver.onNext(TransferResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Throwable e) {
            respondWithTransferError(e, responseObserver);
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
