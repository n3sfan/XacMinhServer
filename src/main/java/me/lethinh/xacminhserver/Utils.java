package me.lethinh.xacminhserver;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.EnumVariant;
import com.jacob.com.Variant;
import me.lethinh.xacminhserver.licensekeylinux.MessageDigestWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;

public class Utils {

    private static final Logger LOGGER = LogManager.getLogger("XacMinh");
    private static final int BUFFER_SIZE = 2048;

    /* LIcense Key */

    public static boolean checkLicense(ConfigurableApplicationContext ctx) {
        try {
            byte[] hwidBytes = getHWID();
            String localKey = java.util.Base64.getEncoder().encodeToString(hwidBytes);

            URL url = new URL("https://raw.githubusercontent.com/fynrae/license_xm/main/index.html"); // new license key link
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setUseCaches(false);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String serverKey = line.trim();

                    if (serverKey.isEmpty() || serverKey.startsWith("#")) {
                        continue;
                    }

                    if (serverKey.equals(localKey)) {
                        return true;
                    }
                }
            }
        } catch (UnsatisfiedLinkError e) {
            LOGGER.fatal("The required 'jacob-1.20-x64.dll' is missing from your system path!");
            System.exit(1);
        }  catch (Exception e) {
            System.out.println("Unexpected");
            System.exit(1);
        }


        if (ctx != null) {
            System.out.println("License Invalid or Not Found on Server.");
            ctx.close();
            System.exit(1);
        }
        return false;
    }

    public static byte[] getHWID() {
        StringBuilder hwid = new StringBuilder();

        ComThread.InitMTA();
        try {
            ActiveXComponent wmi = new ActiveXComponent("winmgmts:\\\\.");

            Variant instances = wmi.invoke("InstancesOf", "Win32_BaseBoard");
            Enumeration<Variant> en = new EnumVariant(instances.getDispatch());
            while (en.hasMoreElements()) {
                ActiveXComponent bb = new ActiveXComponent(en.nextElement().getDispatch());
                hwid.append(bb.getPropertyAsString("SerialNumber"));
            }

            en = new EnumVariant(wmi.invoke("InstancesOf", "Win32_DiskDrive").getDispatch());
            while (en.hasMoreElements()) {
                ActiveXComponent dd = new ActiveXComponent(en.nextElement().getDispatch());
                hwid.append(dd.getPropertyAsString("Model")).append(dd.getPropertyAsString("SerialNumber"));
            }

        } catch (Exception e) {
            LOGGER.error("Error generating HWID via JACOB: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            ComThread.Release();
        }

        try {
            MessageDigestWrapper md = MessageDigestWrapper.getInstance("SHA-256");
            md.update(hwid.toString().getBytes(StandardCharsets.UTF_8));
            return md.digest();
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