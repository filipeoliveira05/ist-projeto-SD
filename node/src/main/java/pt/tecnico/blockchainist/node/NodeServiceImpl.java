package pt.tecnico.blockchainist.node;

import io.grpc.Status;
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
import pt.tecnico.blockchainist.node.domain.NodeState;

public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {

    private final NodeState nodeState;

    public NodeServiceImpl(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public void createWallet(CreateWalletRequest request, StreamObserver<CreateWalletResponse> responseObserver) {
        try {
            nodeState.createWallet(request.getUserId(), request.getWalletId());
            responseObserver.onNext(CreateWalletResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Wallet already exists")) {
                responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
        }
    }

    @Override
    public void deleteWallet(DeleteWalletRequest request, StreamObserver<DeleteWalletResponse> responseObserver) {
        try {
            nodeState.deleteWallet(request.getUserId(), request.getWalletId());
            responseObserver.onNext(DeleteWalletResponse.getDefaultInstance());
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
    public void readBalance(ReadBalanceRequest request, StreamObserver<ReadBalanceResponse> responseObserver) {
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
        try {
            nodeState.transfer(request.getSrcUserId(), request.getSrcWalletId(), request.getDstWalletId(), request.getValue());
            responseObserver.onNext(TransferResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("does not exist")) {
                responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            }
        }
    }

    @Override
    public void getBlockchainState(GetBlockchainStateRequest request, StreamObserver<GetBlockchainStateResponse> responseObserver) {
        // Retorna resposta vazia por enquanto, para a fase A.1
        responseObserver.onNext(GetBlockchainStateResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}