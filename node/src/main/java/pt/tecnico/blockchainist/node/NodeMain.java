package pt.tecnico.blockchainist.node;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.node.domain.NodeState;

public class NodeMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(NodeMain.class.getSimpleName());

        // check arguments
        if (args.length < 3) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> <org> <sequencerHost:sequencerPort>%n", NodeMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);
        final String org = args[1];
        final String sequencerAddress = args[2];
        
        String[] sequencerSplit = sequencerAddress.split(":");
        String sequencerHost = sequencerSplit[0];
        int sequencerPort = Integer.parseInt(sequencerSplit[1]);

        final NodeState nodeState = new NodeState();

        // Connect to Sequencer
        ManagedChannel sequencerChannel = ManagedChannelBuilder.forAddress(sequencerHost, sequencerPort).usePlaintext().build();
        SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub = SequencerServiceGrpc.newBlockingStub(sequencerChannel);

        Map<Transaction, CompletableFuture<Throwable>> pendingTransactions = new ConcurrentHashMap<>();

        final NodeServiceImpl nodeService = new NodeServiceImpl(nodeState, sequencerStub, pendingTransactions);
        NodeSequencerClient sequencerClient = new NodeSequencerClient(sequencerStub, nodeState, pendingTransactions);
        int nextBlockNumber = sequencerClient.syncInitialBlocks();
        sequencerClient.setNextBlockNumber(nextBlockNumber);
        System.out.println("Initial synchronization complete. Next block number: " + nextBlockNumber);

        Server server = ServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(nodeService, new DelayInterceptor()))
                .build();

        server.start();
        new Thread(sequencerClient, "sequencer-polling-thread").start();
        System.out.println("Server started, listening on " + port);

        server.awaitTermination();
    }
}
