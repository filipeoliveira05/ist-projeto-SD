package pt.tecnico.blockchainist.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.GetBlockchainStateRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;

/**
 * gRPC client wrapper for communicating with a single blockchain node.
 * Provides both blocking and async variants of each RPC, and attaches
 * the server-side delay as gRPC metadata when requested.
 */
public class ClientNodeService {

    // Metadata key used to send the requested delay (in seconds) to the node.
    public static final Metadata.Key<String> DELAY_KEY =
            Metadata.Key.of("delay-seconds", Metadata.ASCII_STRING_MARSHALLER);

    // Must exceed the sequencer block timeout (T=5s) plus polling interval,
    // otherwise the client deadline fires before the block is delivered.
    private static final int MIN_DEADLINE_SECONDS = 10;
    private static final int DEADLINE_MARGIN_SECONDS = 2;
    private static final int CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 3;

    private final ManagedChannel channel;

    // for blocking (C/E/S/T) commands
    private final NodeServiceGrpc.NodeServiceBlockingStub stub;

    // for async (c/e/s/t) commands
    private final NodeServiceGrpc.NodeServiceStub asyncStub;

    public ClientNodeService(String host, int port, String organization) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = NodeServiceGrpc.newBlockingStub(channel);
        this.asyncStub = NodeServiceGrpc.newStub(channel);
    }

    /** Compute a deadline that accounts for server-side delay plus a safety margin. */
    private long calculateDeadlineSeconds(int delaySeconds) {
        return Math.max(MIN_DEADLINE_SECONDS, (long) delaySeconds + DEADLINE_MARGIN_SECONDS);
    }

    /** Configure blocking stub with delay metadata header and appropriate deadline. */
    private NodeServiceGrpc.NodeServiceBlockingStub getStubWithDelay(int delaySeconds) {
        NodeServiceGrpc.NodeServiceBlockingStub configuredStub = this.stub;
        if (delaySeconds <= 0) {
            return configuredStub.withDeadlineAfter(calculateDeadlineSeconds(0), TimeUnit.SECONDS);
        }
        Metadata metadata = new Metadata();
        metadata.put(DELAY_KEY, String.valueOf(delaySeconds));
        configuredStub = configuredStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        return configuredStub.withDeadlineAfter(calculateDeadlineSeconds(delaySeconds), TimeUnit.SECONDS);
    }

    /** Configure async stub with delay metadata header and appropriate deadline. */
    private NodeServiceGrpc.NodeServiceStub getAsyncStubWithDelay(int delaySeconds) {
        NodeServiceGrpc.NodeServiceStub configuredStub = this.asyncStub;
        if (delaySeconds <= 0) {
            return configuredStub.withDeadlineAfter(calculateDeadlineSeconds(0), TimeUnit.SECONDS);
        }
        Metadata metadata = new Metadata();
        metadata.put(DELAY_KEY, String.valueOf(delaySeconds));
        configuredStub = configuredStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
        return configuredStub.withDeadlineAfter(calculateDeadlineSeconds(delaySeconds), TimeUnit.SECONDS);
    }

    public CreateWalletResponse createWallet(String userId, String walletId, String requestId, int delaySeconds) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        return getStubWithDelay(delaySeconds).createWallet(request);
    }

    public void createWalletAsync(
            String userId,
            String walletId,
            String requestId,
            int delaySeconds,
            StreamObserver<CreateWalletResponse> responseObserver) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        getAsyncStubWithDelay(delaySeconds).createWallet(request, responseObserver);
    }

    public DeleteWalletResponse deleteWallet(String userId, String walletId, String requestId, int delaySeconds) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        return getStubWithDelay(delaySeconds).deleteWallet(request);
    }

    public void deleteWalletAsync(
            String userId,
            String walletId,
            String requestId,
            int delaySeconds,
            StreamObserver<DeleteWalletResponse> responseObserver) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        getAsyncStubWithDelay(delaySeconds).deleteWallet(request, responseObserver);
    }

    public ReadBalanceResponse readBalance(String walletId, int delaySeconds) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        return getStubWithDelay(delaySeconds).readBalance(request);
    }

    public void readBalanceAsync(String walletId, int delaySeconds, StreamObserver<ReadBalanceResponse> responseObserver) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        getAsyncStubWithDelay(delaySeconds).readBalance(request, responseObserver);
    }

    public TransferResponse transfer(String srcUserId, String srcWalletId, String dstWalletId, long amount, String requestId, int delaySeconds) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(amount)
                .setRequestId(requestId)
                .build();
        return getStubWithDelay(delaySeconds).transfer(request);
    }

    public void transferAsync(
            String srcUserId,
            String srcWalletId,
            String dstWalletId,
            long amount,
            String requestId,
            int delaySeconds,
            StreamObserver<TransferResponse> responseObserver) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(amount)
                .setRequestId(requestId)
                .build();
        getAsyncStubWithDelay(delaySeconds).transfer(request, responseObserver);
    }

    public GetBlockchainStateResponse getBlockchainState() {
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        return stub.withDeadlineAfter(calculateDeadlineSeconds(0), TimeUnit.SECONDS).getBlockchainState(request);
    }

    /** Gracefully shut down the gRPC channel, forcing termination if needed. */
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                channel.shutdownNow();
                channel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
