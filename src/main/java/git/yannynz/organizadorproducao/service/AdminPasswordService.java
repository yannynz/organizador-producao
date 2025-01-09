package git.yannynz.organizadorproducao.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AdminPasswordService {

    private String storedHash;

    public void setStoredHash(String hash) {
        this.storedHash = hash;
    }

    public boolean validatePassword(String password) {
        String hashedPassword = hashPassword(password);
        return hashedPassword != null && hashedPassword.equals(storedHash);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
}

