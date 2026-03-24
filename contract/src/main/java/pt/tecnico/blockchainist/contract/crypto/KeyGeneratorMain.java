package pt.tecnico.blockchainist.contract.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

/**
 * Utility entry point to generate key pairs for a list of entities.
 *
 * Usage:
 * KeyGeneratorMain <output_dir> <entity1> [entity2] [entity3] ...
 */
public final class KeyGeneratorMain {

    private KeyGeneratorMain() {
        // Utility class.
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: KeyGeneratorMain <output_dir> <entity1> [entity2] [entity3] ...");
            return;
        }

        String outputDir = args[0];
        Path outputDirPath = Path.of(outputDir);
        Path publicKeysPath = outputDirPath.resolve("public_keys");

        try {
            Files.createDirectories(outputDirPath);

            // Regenerate the public keys file from scratch in each run.
            Files.deleteIfExists(publicKeysPath);
            Files.createFile(publicKeysPath);

            for (int i = 1; i < args.length; i++) {
                String entityId = args[i];
                if (entityId == null || entityId.isBlank()) {
                    throw new IllegalArgumentException("Entity identifier cannot be empty");
                }

                KeyPair keyPair = CryptoUtils.generateKeyPair();
                Path privateKeyPath = outputDirPath.resolve(entityId + ".priv");

                CryptoUtils.savePrivateKey(keyPair.getPrivate(), privateKeyPath.toString());
                CryptoUtils.appendPublicKey(entityId, keyPair.getPublic(), publicKeysPath.toString());
            }
        } catch (Exception e) {
            System.err.println("Failed to generate keys: " + e.getMessage());
            System.exit(1);
        }
    }
}
