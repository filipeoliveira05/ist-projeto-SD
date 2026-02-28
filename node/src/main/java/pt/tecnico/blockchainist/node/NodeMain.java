package pt.tecnico.blockchainist.node;

import java.io.IOException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
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
        final NodeServiceImpl nodeService = new NodeServiceImpl(nodeState);

        // Connect to Sequencer
        ManagedChannel sequencerChannel = ManagedChannelBuilder.forAddress(sequencerHost, sequencerPort).usePlaintext().build();
        SequencerServiceGrpc.SequencerServiceBlockingStub sequencerStub = SequencerServiceGrpc.newBlockingStub(sequencerChannel);

        NodeSequencerClient sequencerClient = new NodeSequencerClient(sequencerStub, nodeState);
        new Thread(sequencerClient).start();

        Server server = ServerBuilder.forPort(port).addService(nodeService).build();

        server.start();
        System.out.println("Server started, listening on " + port);

        server.awaitTermination();
    }
}
