package pt.tecnico.blockchainist.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
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

    public ClientNodeService(String host, int port, String organization) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = NodeServiceGrpc.newBlockingStub(channel);
    }

    private NodeServiceGrpc.NodeServiceBlockingStub getStubWithDelay(int delaySeconds) {
        if (delaySeconds <= 0) {
            return this.stub;
        }
        Metadata metadata = new Metadata();
        metadata.put(DELAY_KEY, String.valueOf(delaySeconds));
        return this.stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    public CreateWalletResponse createWallet(String userId, String walletId, int delaySeconds) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .build();
        return getStubWithDelay(delaySeconds).createWallet(request);
    }

    public DeleteWalletResponse deleteWallet(String userId, String walletId, int delaySeconds) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .build();
        return getStubWithDelay(delaySeconds).deleteWallet(request);
    }

    public ReadBalanceResponse readBalance(String walletId, int delaySeconds) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        return getStubWithDelay(delaySeconds).readBalance(request);
    }

    public TransferResponse transfer(String srcUserId, String srcWalletId, String dstWalletId, long amount, int delaySeconds) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(amount)
                .build();
        return getStubWithDelay(delaySeconds).transfer(request);
    }

    public GetBlockchainStateResponse getBlockchainState() {
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        return stub.getBlockchainState(request);
    }
}
