package me.lethinh.xacminhserver.licensekeylinux;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MessageDigestWrapper {
    private final MessageDigest md;

    MessageDigestWrapper(MessageDigest md) {
        this.md = md;
    }

    public static MessageDigestWrapper getInstance(String type) throws NoSuchAlgorithmException {
        return new MessageDigestWrapper(MessageDigest.getInstance("MD5"));
    }

    public void update(byte[] bytes) {
        md.update(bytes);
    }

    public byte[] digest() {
        return md.digest();
    }
}
