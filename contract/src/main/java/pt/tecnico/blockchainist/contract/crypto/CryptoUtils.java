package pt.tecnico.blockchainist.contract.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

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

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize key pair generator", e);
        }
    }

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
}
