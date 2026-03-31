package pt.tecnico.blockchainist.sequencer;

import java.io.IOException;
import java.security.PrivateKey;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.blockchainist.contract.crypto.CryptoUtils;
import pt.tecnico.blockchainist.sequencer.domain.SequencerState;

/**
 * Sequencer entry point. Starts the gRPC server that provides atomic broadcast
 * by grouping transactions into blocks. Accepts optional N (max transactions
 * per block) and T (block timeout in seconds) parameters.
 */
public class SequencerMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(SequencerMain.class.getSimpleName());

        // Validate arguments: <port> [N] [T] <private_key_file>
        if (args.length < 2) {
            System.err.println("Argument(s) missing!");
            System.err.printf("Usage: java %s <port> [N] [T] <private_key_file>%n", SequencerMain.class.getName());
            return;
        }

        final int port = Integer.parseInt(args[0]);

        // The last argument is always the private key file.
        String privateKeyFile = args[args.length - 1];
        PrivateKey sequencerPrivateKey = CryptoUtils.loadPrivateKey(privateKeyFile);

        // Default block creation policy: N=4 transactions, T=5 seconds timeout.
        // N and T are optional intermediate numeric arguments.
        int n = 4;
        int t = 5;
        if (args.length >= 3) {
            n = Integer.parseInt(args[1]);
        }
        if (args.length >= 4) {
            t = Integer.parseInt(args[2]);
        }

        final SequencerState sequencerState = new SequencerState(n, t, sequencerPrivateKey);
        final SequencerServiceImpl sequencerService = new SequencerServiceImpl(sequencerState);

        Server server = ServerBuilder.forPort(port).addService(sequencerService).build();

        // Graceful shutdown: stop accepting new RPCs and finish in-flight ones on SIGINT/SIGTERM.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down sequencer server...");
            server.shutdown();
        }));

        server.start();
        System.out.println("Server started, listening on " + port);

        server.awaitTermination();
    }
}
