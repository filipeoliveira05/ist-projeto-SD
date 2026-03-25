package pt.tecnico.blockchainist.node;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.node.domain.NodeState;

/**
 * Node entry point. Connects to the sequencer, synchronizes the blockchain
 * state (B.2), then starts the gRPC server and background block-polling thread.
 */
public class NodeMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(NodeMain.class.getSimpleName());

        // Validate arguments: <port> <org> <sequencerHost:sequencerPort> <public_keys_file>
        if (args.length < 4) {
            System.err.println("Argument(s) missing!");
            System.err.printf(
                    "Usage: java %s <port> <org> <sequencerHost:sequencerPort> <public_keys_file>%n",
                    NodeMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);
        final String org = args[1];
        final String sequencerAddress = args[2];
        final String publicKeysFile = args[3];
        
        String[] sequencerSplit = sequencerAddress.split(":");
        String sequencerHost = sequencerSplit[0];
        int sequencerPort = Integer.parseInt(sequencerSplit[1]);

        final NodeState nodeState = new NodeState();

        // Connect to Sequencer
        ManagedChannel sequencerChannel = ManagedChannelBuilder.forAddress(sequencerHost, sequencerPort).usePlaintext().build();
        SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub = SequencerServiceGrpc.newBlockingStub(sequencerChannel);

        // Maps shared between NodeServiceImpl (producer) and NodeSequencerClient (consumer)
        // to coordinate transaction completion across threads.
        Map<String, CompletableFuture<Throwable>> pendingTransactions = new ConcurrentHashMap<>();
        Map<String, RequestResult> completedTransactions = new ConcurrentHashMap<>();

        // C.1: Tracks requestIds of transfers applied speculatively (before block delivery).
        Set<String> speculativeTransfers = ConcurrentHashMap.newKeySet();

        NodeSequencerClient sequencerClient = new NodeSequencerClient(sequencerStub, nodeState, pendingTransactions, completedTransactions, speculativeTransfers);
        final NodeServiceImpl nodeService = new NodeServiceImpl(
                org,
                nodeState,
                sequencerStub,
                sequencerClient,
                pendingTransactions,
                completedTransactions,
                speculativeTransfers);

        // C.1: Share the dependency lock between NodeServiceImpl and NodeSequencerClient
        // so that both can notify threads waiting for causal dependencies.
        sequencerClient.setDependencyLock(nodeService.getDependencyLock());
                
        // B.2: Synchronize with the sequencer before accepting client requests.
        // This ensures the node has the full blockchain even if it joins late.
        int nextBlockNumber = sequencerClient.syncInitialBlocks();
        sequencerClient.setNextBlockNumber(nextBlockNumber);
        System.out.println("Initial synchronization complete. Next block number: " + nextBlockNumber);

        // Register the DelayInterceptor to extract delay metadata from client requests.
        Server server = ServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(nodeService, new DelayInterceptor()))
                .build();

        // Graceful shutdown: stop accepting new RPCs, finish in-flight ones,
        // and close the channel to the sequencer on SIGINT/SIGTERM.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down node server...");
            server.shutdown();
            sequencerChannel.shutdown();
        }));

        server.start();

        // Start the background thread that polls the sequencer for new blocks.
        new Thread(sequencerClient, "sequencer-polling-thread").start();
        System.out.println("Server started, listening on " + port);

        server.awaitTermination();
    }
}
