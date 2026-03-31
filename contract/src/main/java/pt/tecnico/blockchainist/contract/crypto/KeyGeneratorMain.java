package pt.tecnico.blockchainist.contract.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility entry point to generate key pairs for a list of entities.
 *
 * Usage:
 * KeyGeneratorMain <output_dir> <entity1> [entity2] [entity3] ...
 */
public final class KeyGeneratorMain {

    private static final List<String> REQUIRED_ENTITIES = List.of(
            "BC",
            "Alice",
            "Bob",
            "Charlie",
            "David",
            "Emma",
            "Fred",
            "Ginger",
            "Henry",
            "Iris",
            "sequencer");

    private KeyGeneratorMain() {
        // Utility class.
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: KeyGeneratorMain <output_dir> <entity1> [entity2] [entity3] ...");
            return;
        }

        String outputDir = args[0];
        String[] entities = new String[args.length - 1];
        System.arraycopy(args, 1, entities, 0, entities.length);
        Path outputDirPath = Path.of(outputDir);
        Path publicKeysPath = outputDirPath.resolve("public_keys");

        try {
            validateRequiredEntities(entities);

            Files.createDirectories(outputDirPath);

            // Regenerate the public keys file from scratch in each run.
            Files.deleteIfExists(publicKeysPath);
            Files.createFile(publicKeysPath);

            for (String entityId : entities) {
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

    /** Ensure all required entities are present and none are duplicated. */
    private static void validateRequiredEntities(String[] entities) {
        Set<String> provided = new LinkedHashSet<>();
        for (String entityId : entities) {
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalArgumentException("Entity identifier cannot be empty");
            }
            if (!provided.add(entityId)) {
                throw new IllegalArgumentException("Duplicated entity identifier: " + entityId);
            }
        }

        List<String> missing = REQUIRED_ENTITIES.stream()
                .filter(entityId -> !provided.contains(entityId))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required entities: " + String.join(", ", missing));
        }
    }
}
