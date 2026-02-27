package pt.tecnico.blockchainist.node;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.blockchainist.node.domain.NodeState;

public class NodeMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(NodeMain.class.getSimpleName());

        // check arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port>%n", NodeMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);
        final NodeState nodeState = new NodeState();
        final NodeServiceImpl nodeService = new NodeServiceImpl(nodeState);

        Server server = ServerBuilder.forPort(port).addService(nodeService).build();

        server.start();
        System.out.println("Server started, listening on " + port);

        server.awaitTermination();
    }
}
