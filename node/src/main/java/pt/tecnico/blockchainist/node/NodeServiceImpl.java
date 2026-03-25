package pt.tecnico.blockchainist.node;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.security.PublicKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.HashMap;

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
import pt.tecnico.blockchainist.contract.crypto.CryptoUtils;
import pt.tecnico.blockchainist.node.domain.NodeState;

/**
 * gRPC service implementation for the blockchain node.
 * Handles client requests by broadcasting transactions to the sequencer
 * and waiting for them to be delivered in a block before responding.
 * Transfers use speculative execution (C.1): applied locally and responded
 * immediately without waiting for block delivery.
 * Supports idempotent retries via requestId-based deduplication (B.2).
 */
public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {

    /** Static mapping of userId → organization, as defined in the enunciado v1.3. */
    private static final Map<String, String> USER_ORGANIZATIONS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("BC", "OrgA");
        m.put("Alice", "OrgA");
        m.put("Bob", "OrgA");
        m.put("Charlie", "OrgA");
        m.put("David", "OrgB");
        m.put("Emma", "OrgB");
        m.put("Fred", "OrgB");
        m.put("Ginger", "OrgC");
        m.put("Henry", "OrgC");
        m.put("Iris", "OrgC");
        USER_ORGANIZATIONS = Map.copyOf(m);
    }

    private final String nodeOrganization;
    private final Map<String, PublicKey> publicKeys;
    private final NodeState nodeState;
    private final SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub;
    private final NodeSequencerClient sequencerClient;

    // Pending futures for transactions that have been sent to the sequencer
    // but not yet delivered in a block.
    private final Map<String, CompletableFuture<Throwable>> pendingTransactions;

    // Cache of already-processed transaction results, used for idempotent retries.
    private final Map<String, RequestResult> completedTransactions;

    // C.1: Tracks requestIds of transfers applied speculatively before block delivery.
    private final Set<String> speculativeTransfers;

    // C.1: Lock used to wait for causal dependencies to be satisfied.
    private final Object dependencyLock = new Object();

    public NodeServiceImpl(
            String nodeOrganization,
            Map<String, PublicKey> publicKeys,
            NodeState nodeState,
            SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub,
            NodeSequencerClient sequencerClient,
            Map<String, CompletableFuture<Throwable>> pendingTransactions,
            Map<String, RequestResult> completedTransactions,
            Set<String> speculativeTransfers) {
        this.nodeOrganization = nodeOrganization;
        this.publicKeys = publicKeys;
        this.nodeState = nodeState;
        this.sequencerStub = sequencerStub;
        this.sequencerClient = sequencerClient;
        this.pendingTransactions = pendingTransactions;
        this.completedTransactions = completedTransactions;
        this.speculativeTransfers = speculativeTransfers;
    }

    /**
     * G3: Verify that the userId belongs to this node's organization.
     * Rejects requests from users that do not belong to this org.
     */
    private void validateUserOrganization(String userId) {
        String userOrg = USER_ORGANIZATIONS.get(userId);
        if (userOrg == null) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Unknown user: " + userId)
                    .asRuntimeException();
        }
        if (!userOrg.equals(nodeOrganization)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("User " + userId + " does not belong to organization " + nodeOrganization)
                    .asRuntimeException();
        }
    }

    /**
     * Shared signature verification helper for C.2 request-specific validators.
     * Returns false if key/signature is missing or verification fails.
     */
    private boolean verifySignatureForEntity(String entityId, Message unsignedRequest, ByteString signatureBytes) {
        if (entityId == null || entityId.isBlank()) {
            return false;
        }
        PublicKey key = publicKeys.get(entityId);
        if (key == null || signatureBytes == null || signatureBytes.isEmpty()) {
            return false;
        }
        try {
            return CryptoUtils.verify(key, unsignedRequest.toByteArray(), signatureBytes.toByteArray());
        } catch (Exception e) {
            return false;
        }
    }

    /** Assign a UUID if the client did not provide a requestId. */
    private static String ensureRequestId(String requestId) {
        return (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
    }

    /** Rebuild the request with a valid requestId if one was missing. */
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

    /**
     * Submit a transaction to the sequencer and block until it is delivered
     * in a block and executed locally. Returns null on success, or the
     * error thrown during execution.
     *
     * Uses a CompletableFuture per requestId so that:
     * - The polling thread (NodeSequencerClient) completes the future when
     *   the transaction is processed from a block.
     * - Duplicate requests (B.2 retries) reuse the same future or return
     *   the cached result from completedTransactions.
     */
    private Throwable submitAndAwaitResult(String requestId, Transaction transaction) throws Throwable {
        // Check if this transaction was already processed (idempotent retry).
        RequestResult completedResult = completedTransactions.get(requestId);
        if (completedResult != null) {
            return completedResult.getError();
        }

        // Atomically register a future; if another thread already registered one,
        // we join its future instead of broadcasting a duplicate.
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

    /** Map domain errors to appropriate gRPC status codes for createWallet. */
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

    /** Map domain errors to appropriate gRPC status codes for deleteWallet. */
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

    /** Map domain errors to appropriate gRPC status codes for transfer. */
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

    /** Sleep for the delay (in seconds) specified in the gRPC metadata header. */
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
        try {
            validateUserOrganization(request.getUserId());
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
            return;
        }
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
        try {
            validateUserOrganization(request.getUserId());
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
            return;
        }
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
            // Ensure reads observe all currently available ordered blocks before replying.
            sequencerClient.catchUpToLatest();
            long balance = nodeState.readBalance(request.getWalletId());
            ReadBalanceResponse response = ReadBalanceResponse.newBuilder().setBalance(balance).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Wallet does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(describe(e)).asRuntimeException());
        }
    }

    /**
     * C.1: Speculative execution - apply the transfer to local state immediately,
     * respond to the client without waiting for block delivery, and broadcast
     * to the sequencer asynchronously. The polling thread will skip re-execution
     * for transfers already applied speculatively.
     */
    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        applyDelay();
        try {
            validateUserOrganization(request.getSrcUserId());
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
            return;
        }
        TransferRequest normalizedRequest = normalizeTransferRequest(request);
        String requestId = normalizedRequest.getRequestId();
        Transaction transaction = Transaction.newBuilder().setTransfer(normalizedRequest).build();

        // Idempotency: check if transaction was already processed (B.2 retry).
        RequestResult completedResult = completedTransactions.get(requestId);
        if (completedResult != null) {
            if (completedResult.isSuccess()) {
                responseObserver.onNext(TransferResponse.getDefaultInstance());
                responseObserver.onCompleted();
            } else {
                respondWithTransferError(completedResult.getError(), responseObserver);
            }
            return;
        }

        // C.1: Wait for causal dependencies before speculative execution.
        List<String> deps = normalizedRequest.getCausalDependenciesList();
        if (!deps.isEmpty()) {
            waitForDependencies(deps, 15000);
        }

        // C.1: Speculative execution - apply to local state immediately.
        try {
            nodeState.transfer(
                    normalizedRequest.getSrcUserId(),
                    normalizedRequest.getSrcWalletId(),
                    normalizedRequest.getDstWalletId(),
                    normalizedRequest.getValue());

            // Mark as speculative and cache the result.
            speculativeTransfers.add(requestId);
            completedTransactions.put(requestId, RequestResult.success());

            // Notify threads waiting on causal dependencies.
            synchronized (dependencyLock) {
                dependencyLock.notifyAll();
            }

            // Broadcast to sequencer (without waiting for block delivery).
            sequencerStub.broadcast(
                    BroadcastRequest.newBuilder().setTransaction(transaction).build());

            responseObserver.onNext(TransferResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            completedTransactions.put(requestId, RequestResult.failure(e));
            respondWithTransferError(e, responseObserver);
        }
    }

    /** C.1: Return the dependency lock so NodeSequencerClient can notify waiting threads. */
    public Object getDependencyLock() {
        return dependencyLock;
    }

    /**
     * C.1: Block until all causal dependencies are satisfied (present in
     * completedTransactions or speculativeTransfers), or until timeout.
     */
    private void waitForDependencies(List<String> deps, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (dependencyLock) {
            while (!allDependenciesSatisfied(deps)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    System.err.println("WARNING: Causal dependency timeout, proceeding anyway.");
                    break;
                }
                try {
                    dependencyLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** C.1: Check whether all dependency requestIds have been processed. */
    private boolean allDependenciesSatisfied(List<String> deps) {
        for (String depId : deps) {
            if (!completedTransactions.containsKey(depId)
                    && !speculativeTransfers.contains(depId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void getBlockchainState(GetBlockchainStateRequest request, StreamObserver<GetBlockchainStateResponse> responseObserver) {
        try {
            // Keep debug reads consistent with the latest available sequenced blocks.
            sequencerClient.catchUpToLatest();
            GetBlockchainStateResponse response = GetBlockchainStateResponse.newBuilder()
                    .addAllTransactions(nodeState.getAllTransactions())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(describe(e)).asRuntimeException());
        }
    }
}
