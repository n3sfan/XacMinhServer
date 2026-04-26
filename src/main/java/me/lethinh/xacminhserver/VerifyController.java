package me.lethinh.xacminhserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;

@RestController
public class VerifyController {

    private static final Logger LOGGER = LogManager.getLogger("XacMinh");
    private static final String OPTIFINE = "OptiFine";
    private static final String[] MINECRAFT_FILES = {"1_13.bin", "1_13_1.bin", "1_13_2.bin", "1_14.bin", "1_14_1.bin", "1_14_2.bin", "1_14_4.bin", "1_15.bin", "1_15_1.bin", "1_15_2.bin", "1_16.bin", "1_16_1.bin", "1_16_2.bin", "1_16_3.bin", "1_16_4.bin", "1_16_5.bin", "a.bin", "ao.bin", "b.bin", "bo.bin", "c.bin", "co.bin", "d.bin", "do.bin", "e.bin", "eo.bin", "OptiFine 1_13_1.bin", "OptiFine 1_13_2.bin", "OptiFine 1_14_2.bin", "OptiFine 1_14_3.bin", "OptiFine 1_14_4.bin", "OptiFine 1_15_2.bin", "OptiFine 1_16_1.bin", "OptiFine 1_16_2.bin", "OptiFine 1_16_3.bin", "OptiFine 1_16_4.bin", "OptiFine 1_16_5.bin"};
    private long lastVersionCheckTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000;
    private String cachedLatestVersion = "1.06";

    private String getExpectedVersion() {
        long now = System.currentTimeMillis();
        if (now - lastVersionCheckTime > CACHE_DURATION) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://raw.githubusercontent.com/fynrae/license_xm/main/version.html");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                    String ver = reader.readLine();
                    if (ver != null && !ver.isEmpty()) {
                        cachedLatestVersion = ver.trim();
                        lastVersionCheckTime = now;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch version from GitHub: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return cachedLatestVersion;
    }

    @PostMapping(value = "/xacminh", headers = {"content-type=text/*"})
    public ResponseEntity<Void> verify(@RequestBody byte[] requestContent) {
        String[] content = decrypt(Base64.getDecoder().decode(Utils.decompressGzip(requestContent)));

        if (content == null) {
            throw new NullPointerException();
        }

        String username = content[0];

        LOGGER.info("Xu li xac minh player " + username + "...");

        if (content.length < 4) {
            LOGGER.warn("Player " + username + " su dung ban xac minh cu!");
            verifyFailed(username);
            return ResponseEntity.badRequest().build();
        }

        String clientVersion = content[3].trim();
        String expectedVersion = getExpectedVersion();

        if (!clientVersion.equals(expectedVersion)) {
            LOGGER.warn("Player " + username + " su dung ban xac minh cu!");
            verifyFailed(username);
            return ResponseEntity.badRequest().build();
        }

        String sessionToken = content.length > 4 ? content[4] : null;

        if (!isValidSession(username, sessionToken)) {
            verifyNotFound(username);
            return ResponseEntity.notFound().build();
        }

        // read class from bytecode sent to server
        byte[] classFile = safeDecode(content[1]);

        if (classFile == null || new String(classFile).equals("false") || classFile.length > 14000) {
            verifyFailed(username);
            return ResponseEntity.badRequest().build();
        }

        ClassWriter writer = new ClassWriter(0);
        VerifyVisitor visitor = new VerifyVisitor(Opcodes.ASM7, writer) {
            @Override
            public void changeClassName(String className) {
                String[] split = className.split(","); // version,username,time

                if (split.length < 3) {
                    failed = true;
                    return;
                }

                // check version
                String version = split[0].replace('.', '_');
                this.mcVersion = version;

                // if version is not vanilla
                if (version.isEmpty() || !version.matches("\\d_\\d+_\\d|\\d_\\d+")) {
                    if (version.contains(OPTIFINE)) {
                        if (version.contains(" ")) { // TLauncher
                            if (Character.digit(version.split(" ")[1].charAt(3), 10) <= 2) {
                                this.mcVersion = version.split(" ")[1];
                            }
                        } else {
                            this.mcVersion = version.substring(0, version.indexOf(OPTIFINE) - 1);
                            if (Character.digit(mcVersion.charAt(3), 10) >= 3) {
                                this.mcVersion = OPTIFINE + ' ' + this.mcVersion;
                            }
                        }
                    } else { // other modification => not allowed
                        failed = true;
                    }
                }
            }
        };

        ClassReader reader = new ClassReader(classFile);
        reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        String mcVersion = visitor.mcVersion;

        // invalid class name or version
        if (visitor.failed) {
            verifyFailed(username);
            LOGGER.info("version: " + mcVersion);
            return ResponseEntity.badRequest().build();
        }

        boolean verified = verifyMinecraft(writer.toByteArray());

        if (verified) {
            updateVerifyStatus(username, 1);
            LOGGER.info("Player " + username + " xac minh thanh cong! (" + mcVersion.replace('_', '.') + ')');
        } else {
            verifyFailed(username);
            LOGGER.warn("Player " + username + " su dung hack! (" + mcVersion.replace('_', '.') + ')');
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(Exception.class) // all exceptions
    public void handleException() {
        LOGGER.error("Bad request!");
    }

    private boolean verifyMinecraft(byte[] sendSrc) {
        for (String file : MINECRAFT_FILES) {
            if (Arrays.equals(sendSrc, Utils.readIO(getClass().getResourceAsStream("/a/" + file)))) {
                return true;
            }
        }
        return false;
    }

    private void verifyFailed(String username) {
        updateVerifyStatus(username, 2, true);
        LOGGER.warn("Player " + username + " su dung hack!");
    }

    private void updateVerifyStatus(String username, int verified) {
        updateVerifyStatus(username, verified, false);
    }

    private void updateVerifyStatus(String username, int verified, boolean forceActive) {
        String sql;
        if (forceActive) {
            sql = "UPDATE players SET verified = ?, deleted = 0 WHERE name = ?";
        } else {
            sql = "UPDATE players SET verified = ? WHERE name = ? AND deleted = ?";
        }

        try (Connection conn = Utils.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, verified);
            stmt.setString(2, username);

            if (!forceActive) {
                stmt.setInt(3, 0);
            }

            stmt.executeUpdate();
        } catch (SQLException throwables) {
            LOGGER.error(throwables);
        }
    }

    private void verifyNotFound(String username) {
        LOGGER.warn("Player " + username + " chua dang nhap vao server!");
    }

    private boolean isValidSession(String username, String sessionTokenStr) {
        if (username == null || username.isEmpty() || sessionTokenStr == null || sessionTokenStr.isEmpty()) {
            return false;
        }

        String[] parts = sessionTokenStr.split("\\|");
        if (parts.length != 2) return false;
        String token = parts[0];
        String hmac = parts[1];

        // verify hmac
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec hmacKeySpec = new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("Xk9pLm4VqA2wF6zT8yH1uA=="), "HmacSHA256");
            mac.init(hmacKeySpec);
            byte[] hmacBytes = mac.doFinal((token + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String calculatedHmac = java.util.Base64.getEncoder().encodeToString(hmacBytes);
            if (!java.security.MessageDigest.isEqual(hmac.getBytes(), calculatedHmac.getBytes())) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        try (java.sql.Connection conn = Utils.connect();
             java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT token_issued_at FROM players WHERE name = ? AND session_token = ? AND deleted = 0")) {
            stmt.setString(1, username);
            stmt.setString(2, token);

            try (java.sql.ResultSet result = stmt.executeQuery()) {
                if (result.next()) {
                    long issuedAt = result.getLong(1);
                    if (System.currentTimeMillis() - issuedAt > 90000 && issuedAt != 0) { // 90 seconds TTL
                        return false; 
                    }
                    // Valid! Now burn the token.
                    try (java.sql.PreparedStatement updateStmt = conn.prepareStatement("UPDATE players SET session_token = NULL WHERE name = ? AND session_token = ?")) {
                        updateStmt.setString(1, username);
                        updateStmt.setString(2, token);
                        updateStmt.executeUpdate();
                    }
                    return true;
                }
            }
        } catch (java.sql.SQLException throwables) {
            LOGGER.error(throwables);
        }

        return false;
    }

    private byte[] bytecodeFromVersion(String version) {
        return Utils.readIO(getClass().getResourceAsStream("/a/" + parseVersionFileName(version) + ".bin"));
    }

    private String[] decrypt(byte[] bytes) {
        try {
            byte[] iv = new byte[16];
            System.arraycopy(bytes, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(Base64.getDecoder().decode("ORZmeWcej5EROq1Brixv8Q=="), "AES"),
                    new IvParameterSpec(iv));
            byte[] decrypt = cipher.doFinal(Arrays.copyOfRange(bytes, 16, bytes.length));
            return new String(decrypt).split("\n");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | NullPointerException e) {
            LOGGER.error(e);
            return null;
        }
    }

    private byte[] safeDecode(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String parseVersionFileName(String mcVersion) {
        switch (mcVersion) {
            case "1_10_2":
                return "a";
            case "OptiFine 1_10_2":
            case "1_10_2-OptiFine":
                return "ao";
            case "1_11":
                return "b";
            case "OptiFine 1_11":
            case "1_11-OptiFine":
                return "bo";
            case "1_11_2":
                return "c";
            case "OptiFine 1_11_2":
            case "1_11_2-OptiFine":
                return "co";
            case "1_12":
                return "d";
            case "OptiFine 1_12":
            case "1_12-OptiFine":
                return "do";
            case "1_12_2":
                return "e";
            case "OptiFine 1_12_2":
            case "1_12_2-OptiFine":
                return "eo";
            default:
                return mcVersion;
        }
    }
}