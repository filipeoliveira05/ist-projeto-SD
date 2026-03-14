package pt.tecnico.blockchainist.sequencer;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.blockchainist.sequencer.domain.SequencerState;

public class SequencerMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(SequencerMain.class.getSimpleName());

        // check arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> [maxTransactionsPerBlock] [blockTimeoutSeconds]%n", SequencerMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);
        
        int n = 4;
        int t = 5;
        if (args.length >= 2) {
            n = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            t = Integer.parseInt(args[2]);
        }

        final SequencerState sequencerState = new SequencerState(n, t);
        final SequencerServiceImpl sequencerService = new SequencerServiceImpl(sequencerState);

        Server server = ServerBuilder.forPort(port).addService(sequencerService).build();

        server.start();
        System.out.println("Server started, listening on " + port);

        server.awaitTermination();
    }
}
