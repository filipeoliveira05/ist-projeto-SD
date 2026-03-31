package pt.tecnico.blockchainist.client;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.security.PrivateKey;

import pt.tecnico.blockchainist.client.grpc.ClientNodeService;
import pt.tecnico.blockchainist.contract.crypto.CryptoUtils;

/**
 * Client entry point. Parses key directory and node addresses from command-line
 * arguments (format: <keys_dir> <host:port:organization> [...]) and starts the
 * interactive command loop.
 */
public class ClientMain {

    public static void main(String[] args) {

        System.out.println(ClientMain.class.getSimpleName());

        // check arguments
        if (args.length < 2) {
            System.err.println("Argument(s) missing!");
            printUsage();
            return;
        }

        String keysDirPath = args[0];
        Map<String, PrivateKey> privateKeys = loadPrivateKeys(keysDirPath);
        if (privateKeys == null) {
            return;
        }

        // parse arguments
        ArrayList<ClientNodeService> nodes = new ArrayList<>(args.length - 1);
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            String[] split = arg.split(":");
            if (split.length != 3) {
                System.err.println("Invalid argument: " + arg);
                printUsage();
                return;
            }
            String host = split[0];
            int port = -1;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port (" + split[1] + ") in argument: " + arg);
                printUsage();
                return;
            }
            if (port > 65535 || port < 0) {
                System.err.println("Port number out of range (0-65535): " + port);
                printUsage();
                return;
            }
            String organization = split[2];

            nodes.add(new ClientNodeService(host, port, organization));
        }

        CommandProcessor processor = new CommandProcessor(nodes, privateKeys);
        processor.userInputLoop();
    }

    /** Load all user private keys from {@code *.priv} files in the given directory. */
    private static Map<String, PrivateKey> loadPrivateKeys(String keysDirPath) {
        File keysDir = new File(keysDirPath);
        if (!keysDir.exists() || !keysDir.isDirectory()) {
            System.err.println("Invalid keys directory: " + keysDirPath);
            printUsage();
            return null;
        }

        File[] keyFiles = keysDir.listFiles((dir, name) -> name.endsWith(".priv"));
        if (keyFiles == null) {
            System.err.println("Failed to list private key files in directory: " + keysDirPath);
            return null;
        }

        Map<String, PrivateKey> privateKeys = new HashMap<>();
        for (File file : keyFiles) {
            String fileName = file.getName();
            String entityId = fileName.substring(0, fileName.length() - ".priv".length());
            try {
                privateKeys.put(entityId, CryptoUtils.loadPrivateKey(file.getPath()));
            } catch (Exception e) {
                System.err.println("Failed to load private key from " + file.getPath() + ": " + e.getMessage());
                return null;
            }
        }

        return privateKeys;
    }

    private static void printUsage() {
        System.err.println("Usage: mvn exec:java -Dexec.args=\"<keys_dir> <host>:<port>:<organization> [<host>:<port>:<organization> ...]\"");
    }
}
