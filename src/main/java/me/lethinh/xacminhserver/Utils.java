package me.lethinh.xacminhserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;

public class Utils {

    private static final Logger LOGGER = LogManager.getLogger("XacMinh");
    private static final int BUFFER_SIZE = 2048;

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
