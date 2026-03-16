package pt.tecnico.blockchainist.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
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

public class ClientNodeService {

    public static final Metadata.Key<String> DELAY_KEY =
            Metadata.Key.of("delay-seconds", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub stub;
    private final NodeServiceGrpc.NodeServiceStub asyncStub;

    public ClientNodeService(String host, int port, String organization) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = NodeServiceGrpc.newBlockingStub(channel);
        this.asyncStub = NodeServiceGrpc.newStub(channel);
    }

    private NodeServiceGrpc.NodeServiceBlockingStub getStubWithDelay(int delaySeconds) {
        if (delaySeconds <= 0) {
            return this.stub;
        }
        Metadata metadata = new Metadata();
        metadata.put(DELAY_KEY, String.valueOf(delaySeconds));
        return this.stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private NodeServiceGrpc.NodeServiceStub getAsyncStubWithDelay(int delaySeconds) {
        if (delaySeconds <= 0) {
            return this.asyncStub;
        }
        Metadata metadata = new Metadata();
        metadata.put(DELAY_KEY, String.valueOf(delaySeconds));
        return this.asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    public CreateWalletResponse createWallet(String userId, String walletId, String requestId, int delaySeconds) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        return getStubWithDelay(delaySeconds).createWallet(request);
    }

    public void createWalletAsync(String userId, String walletId, String requestId, int delaySeconds, long commandNumber) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        getAsyncStubWithDelay(delaySeconds).createWallet(request, new StreamObserver<CreateWalletResponse>() {
            @Override
            public void onNext(CreateWalletResponse response) {
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response);
                }
            }
            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                synchronized (System.out) {
                    System.out.println();
                    System.err.println(commandNumber + " " + status.getDescription());
                }
            }
            @Override
            public void onCompleted() {}
        });
    }

    public DeleteWalletResponse deleteWallet(String userId, String walletId, String requestId, int delaySeconds) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        return getStubWithDelay(delaySeconds).deleteWallet(request);
    }

    public void deleteWalletAsync(String userId, String walletId, String requestId, int delaySeconds, long commandNumber) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .setRequestId(requestId)
                .build();
        getAsyncStubWithDelay(delaySeconds).deleteWallet(request, new StreamObserver<DeleteWalletResponse>() {
            @Override
            public void onNext(DeleteWalletResponse response) {
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response);
                }
            }
            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                synchronized (System.out) {
                    System.out.println();
                    System.err.println(commandNumber + " " + status.getDescription());
                }
            }
            @Override
            public void onCompleted() {}
        });
    }

    public ReadBalanceResponse readBalance(String walletId, int delaySeconds) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        return getStubWithDelay(delaySeconds).readBalance(request);
    }

    public void readBalanceAsync(String walletId, int delaySeconds, long commandNumber) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        getAsyncStubWithDelay(delaySeconds).readBalance(request, new StreamObserver<ReadBalanceResponse>() {
            @Override
            public void onNext(ReadBalanceResponse response) {
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response.getBalance());
                    System.out.println();
                }
            }
            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                synchronized (System.out) {
                    System.out.println();
                    System.err.println(commandNumber + " " + status.getDescription());
                }
            }
            @Override
            public void onCompleted() {}
        });
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

    public void transferAsync(String srcUserId, String srcWalletId, String dstWalletId, long amount, String requestId, int delaySeconds, long commandNumber) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(amount)
                .setRequestId(requestId)
                .build();
        getAsyncStubWithDelay(delaySeconds).transfer(request, new StreamObserver<TransferResponse>() {
            @Override
            public void onNext(TransferResponse response) {
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response);
                }
            }
            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                synchronized (System.out) {
                    System.out.println();
                    System.err.println(commandNumber + " " + status.getDescription());
                }
            }
            @Override
            public void onCompleted() {}
        });
    }

    public GetBlockchainStateResponse getBlockchainState() {
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        return stub.getBlockchainState(request);
    }
}
