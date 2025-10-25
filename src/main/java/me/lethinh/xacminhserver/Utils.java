package me.lethinh.xacminhserver;

import me.lethinh.xacminhserver.licensekeylinux.MessageDigestWrapper;
import me.lethinh.xacminhserver.licensekeylinux.StreamGobbler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public class Utils {

    private static final Logger LOGGER = LogManager.getLogger("XacMinh");
    private static final int BUFFER_SIZE = 2048;

    /* LIcense Key */

    public static boolean checkLicense(ConfigurableApplicationContext ctx) {
        byte[] hwid = new byte[0];
        try {
            hwid = getUserID();

            HttpURLConnection conn = null;
            byte[] buf = new byte[16];

            try {
                conn = (HttpURLConnection) new URL("https://n3sfan.github.io/license_xm.html").openConnection();
                conn.setRequestMethod("GET");
                conn.setReadTimeout(15000);
                conn.setConnectTimeout(15000);
                conn.setUseCaches(false);
                conn.setDoOutput(false);

                try (DataInputStream dis = new DataInputStream(conn.getInputStream())) {
                    int l;
                    while ((l = dis.read(buf, 0, 16)) != -1) {
                        if (l < 16)
                            break;

                        if (Arrays.equals(hwid, buf)) {
                            return true;
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                throw new IllegalArgumentException("Kiem tra cap nhat mat qua nhieu thoi gian, vui long thu lai.");
            } catch (IOException e) {
                throw new IllegalArgumentException("Ko the kiem tra cap nhat phien ban!");
            } finally {
                if (conn != null) conn.disconnect();
            }
//            System.exit(0;

        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("Unexpected");
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Waited too long");
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
//        if (Arrays.equals(Base64.getDecoder().decode("YGi1lZ6wEdtoX+vz9K/5sw=="), hwid)) {
//            return true;
//        }
        ctx.close();
        return false;
    }

    public static byte[] getUserID() throws IOException, InterruptedException {
        byte[] res = execCmd("lsblk -dno name,serial");
        // App Must runs as sudo
        byte[] res2 = execCmd("dmidecode -t baseboard");
        byte[] input = Arrays.copyOf(res, res.length + res2.length);
        System.arraycopy(res2, 0, input, res.length, res2.length);

        input = hash(input);
        return input;
    }

    public static byte[] execCmd(String cmd) throws InterruptedException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(cmd);
        // any error message?
        StreamGobbler errorGobbler = new
                StreamGobbler(proc.getErrorStream(), "ERROR", error);

        // any output?
        StreamGobbler outputGobbler = new
                StreamGobbler(proc.getInputStream(), "OUTPUT", baos);

        // kick them off
        errorGobbler.start();
        outputGobbler.start();

        // any error???
        int exitVal = proc.waitFor();
        while (outputGobbler.isAlive() || errorGobbler.isAlive()) {
            // wait
        }

        // TODO DEBUG
//        System.out.println(error.toString());
//        System.out.println(baos.toString());

        if (exitVal != 0) {
            throw new IllegalStateException("Waited exit error");
        }
        return baos.toByteArray();
    }

    public static byte[] hash(byte[] hwid) {
        try {
            MessageDigestWrapper md = MessageDigestWrapper.getInstance("SHA-256");
            md.update(hwid);
            byte[] encrypted = md.digest();
            return encrypted;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection connect() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + XacMinhApplication.DATABASE_PATH);
        } catch (SQLException throwables) {
            LOGGER.fatal(throwables);
            System.exit(1);
            return null;
        }
    }

    public static byte[] decompressGzip(byte[] src) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(src))) {
            int len;
            byte[] buf = new byte[BUFFER_SIZE];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((len = in.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.error(e);
            return null;
        }
    }

    public static byte[] readIO(InputStream in) {
        try {
            if (in == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

}
