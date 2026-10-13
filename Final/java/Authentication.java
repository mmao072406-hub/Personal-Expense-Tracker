import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 * Authentication.java
 * Handles password hashing (PBKDF2 with Salt), registration validation,
 * credential verification, and thread-safe session token management.
 */
public class Authentication {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();

    public static class UserSession {
        private final int userId;
        private final String username;
        private final long createdAt;

        public UserSession(int userId, String username) {
            this.userId = userId;
            this.username = username;
            this.createdAt = System.currentTimeMillis();
        }

        public int getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * Hashes a raw password using PBKDF2 with a random salt.
     * Returns salt:hash format.
     */
    public static String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            SecureRandom sr = new SecureRandom();
            sr.nextBytes(salt);

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();

            String saltHex = Base64.getEncoder().encodeToString(salt);
            String hashHex = Base64.getEncoder().encodeToString(hash);
            return saltHex + ":" + hashHex;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Fallback to SHA-256 if PBKDF2 is unavailable
            return fallbackSha256(password);
        }
    }

    /**
     * Verifies a raw password against stored salt:hash string.
     */
    public static boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null || rawPassword == null) return false;
        
        if (storedHash.startsWith("SHA256:")) {
            return fallbackSha256(rawPassword).equals(storedHash);
        }

        String[] parts = storedHash.split(":");
        if (parts.length != 2) return false;

        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] actualHash = skf.generateSecret(spec).getEncoded();

            if (expectedHash.length != actualHash.length) return false;
            int diff = 0;
            for (int i = 0; i < expectedHash.length; i++) {
                diff |= expectedHash[i] ^ actualHash[i];
            }
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String fallbackSha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes());
            return "SHA256:" + Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    /**
     * Creates a new session token for authenticated user.
     */
    public static String createSession(int userId, String username) {
        String token = UUID.randomUUID().toString();
        activeSessions.put(token, new UserSession(userId, username));
        return token;
    }

    /**
     * Retrieves UserSession for a given session token.
     */
    public static UserSession getSession(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        return activeSessions.get(token.trim());
    }

    /**
     * Destroys/invalidates session.
     */
    public static void destroySession(String token) {
        if (token != null) {
            activeSessions.remove(token.trim());
        }
    }

    /**
     * Destroys all sessions for a deleted user.
     */
    public static void destroySessionsForUser(int userId) {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().getUserId() == userId);
    }
}
