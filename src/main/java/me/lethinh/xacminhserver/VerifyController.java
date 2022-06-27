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
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

    // TODO OPTIMIZE COMPARING LARGE BYTE ARRAYs
    @PostMapping(value = "/xacminh", headers = {"content-type=text/*"})
    public ResponseEntity<Void> verify(@RequestBody byte[] requestContent) {
        String[] content = decrypt(Base64.getDecoder().decode(Utils.decompressGzip(requestContent)));

        if (content == null) {
            throw new NullPointerException();
        }

        String username = content[0];

        LOGGER.info("Xu li xac minh player " + username + "...");

        // hack confirmed
        if (content.length != 3) {
            if (username != null && username.length() <= 16) {
                LOGGER.warn("Bad request");
            } else {
                LOGGER.warn("Bad username: " + username);
            }
            verifyFailed(username);
            return ResponseEntity.badRequest().build();
        }
        if (isNotLoggedIn(username)) {
            verifyNotFound(username);
            return ResponseEntity.notFound().build();
        }

        // read class from bytecode sent to server
        byte[] classFile = safeDecode(content[1]);

        // invalid class file, hack?
        // TODO CLASS FILE LENGTH > 5000
        if (classFile == null || new String(classFile).equals("false") || classFile.length > 14000) {
            //LOGGER.info("direct");
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
                    //LOGGER.info(className);
                    return;
                }

                // check version
                String version = split[0].replace('.', '_');
                this.mcVersion = version;

                // if version is not vanilla
                if (version.isEmpty() || !version.matches("\\d_\\d+_\\d|\\d_\\d+")) {
                    //LOGGER.info(mcVersion.replace('_', '.'));

                    // since OptiFine has a lot of updates, only get the vanilla version part
                    if (version.contains(OPTIFINE)) {
                        if (version.contains(" ")) { // TLauncher
                            // Only tested for <= 1.12.2
                            if (Character.digit(version.split(" ")[1].charAt(3), 10) <= 2) {
                                this.mcVersion = version.split(" ")[1];
                            }
                        } else {
                            this.mcVersion = version.substring(0, version.indexOf(OPTIFINE) - 1);

                            // 1.13.1-OptiFine... -> use TLauncher Optifine to check
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

        /*try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream("D:\\Workspace\\XacMinhServer\\src\\main\\resources\\a\\test.class"))) {
            out.write(writer.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        //LOGGER.info("test " + mcVersion);

        //boolean verified = Arrays.equals(writer.toByteArray(), bytecodeFromVersion(mcVersion));
        boolean verified = verifyMinecraft(writer.toByteArray());
        updateVerifyStatus(username, verified ? 1 : 2);

        if (verified) {
            LOGGER.info("Player " + username + " xac minh thanh cong! (" + mcVersion.replace('_', '.') + ')');
        } else {
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
        updateVerifyStatus(username, 2);
        LOGGER.warn("Player " + username + " su dung hack!");
    }

    private void verifyNotFound(String username) {
        LOGGER.warn("Player " + username + " chua dang nhap vao server!");
    }

    private boolean isNotLoggedIn(String username) {
        if (username == null || username.isEmpty()) {
            return true;
        }

        try (Connection conn = Utils.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT rowid FROM players WHERE name = ? AND deleted = ?")) {
            stmt.setString(1, username);
            stmt.setInt(2, 0);

            try (ResultSet result = stmt.executeQuery()) {
                return !result.next();
            }
        } catch (SQLException throwables) {
            LOGGER.error(throwables);
        }

        return true;
    }

    private void updateVerifyStatus(String username, int verified) {
        try (Connection conn = Utils.connect();
             PreparedStatement stmt = conn.prepareStatement("UPDATE players SET verified = ? WHERE name = ? AND deleted = ?")) {
            stmt.setInt(1, verified);
            stmt.setString(2, username);
            stmt.setInt(3, 0);
            stmt.executeUpdate();
        } catch (SQLException throwables) {
            LOGGER.error(throwables);
        }
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

    /*  a = 1.10.2
        b = 1.11, c = 1.11.2
        d = 1.12, e = 1.12.2
     */
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
