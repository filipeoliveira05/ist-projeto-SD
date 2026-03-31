package pt.tecnico.blockchainist.contract.crypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared cryptographic helpers for key generation and digital signatures.
 */
public final class CryptoUtils {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 2048;

    private CryptoUtils() {
        // Utility class.
    }

    /** Generate a new RSA key pair (public + private). */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize key pair generator", e);
        }
    }

    /** Sign {@code data} with the given private key using SHA256withRSA. */
    public static byte[] sign(PrivateKey key, byte[] data) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initSign(key);
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign data", e);
        }
    }

    /** Verify a SHA256withRSA signature against the given public key and data. */
    public static boolean verify(PublicKey key, byte[] data, byte[] signatureBytes) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(key);
            sig.update(data);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify signature", e);
        }
    }

    /** Load a PKCS8 private key from a Base64-encoded file. */
    public static PrivateKey loadPrivateKey(String filePath) {
        try {
            String content = Files.readString(Path.of(filePath)).trim();
            byte[] keyBytes = Base64.getDecoder().decode(content);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load private key from " + filePath, e);
        }
    }

    /** Decode a single X509 public key from a Base64 string. */
    public static PublicKey loadPublicKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load public key", e);
        }
    }

    /** Load all public keys from a file with lines in the format {@code <entityId> <base64Key>}. */
    public static Map<String, PublicKey> loadPublicKeys(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            Map<String, PublicKey> publicKeys = new HashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid public key entry: " + line);
                }
                if (publicKeys.containsKey(parts[0])) {
                    throw new IllegalArgumentException("Duplicate entity id in public keys file: " + parts[0]);
                }
                publicKeys.put(parts[0], loadPublicKey(parts[1]));
            }
            return publicKeys;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load public keys from " + filePath, e);
        }
    }

    /** Save a private key to a file as Base64-encoded PKCS8. */
    public static void savePrivateKey(PrivateKey key, String filePath) {
        try {
            Path path = Path.of(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            Files.writeString(path, encodedKey + System.lineSeparator());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save private key to " + filePath, e);
        }
    }

    /** Append a public key entry ({@code <entityId> <base64Key>}) to the given file. */
    public static void appendPublicKey(String entityId, PublicKey key, String filePath) {
        try {
            Path path = Path.of(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            String line = entityId + " " + encodedKey + System.lineSeparator();
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to append public key for " + entityId + " to " + filePath, e);
        }
    }
}
