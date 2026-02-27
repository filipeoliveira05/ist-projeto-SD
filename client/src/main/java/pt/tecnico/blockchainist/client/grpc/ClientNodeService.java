package pt.tecnico.blockchainist.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub stub;

    public ClientNodeService(String host, int port, String organization) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = NodeServiceGrpc.newBlockingStub(channel);
    }

    public CreateWalletResponse createWallet(String userId, String walletId) {
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .build();
        return stub.createWallet(request);
    }

    public DeleteWalletResponse deleteWallet(String userId, String walletId) {
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                .setUserId(userId)
                .setWalletId(walletId)
                .build();
        return stub.deleteWallet(request);
    }

    public ReadBalanceResponse readBalance(String walletId) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
                .setWalletId(walletId)
                .build();
        return stub.readBalance(request);
    }

    public TransferResponse transfer(String srcUserId, String srcWalletId, String dstWalletId, long amount) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSrcUserId(srcUserId)
                .setSrcWalletId(srcWalletId)
                .setDstWalletId(dstWalletId)
                .setValue(amount)
                .build();
        return stub.transfer(request);
    }

    public GetBlockchainStateResponse getBlockchainState() {
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();
        return stub.getBlockchainState(request);
    }
}
