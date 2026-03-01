package com.jcpineda.filestore.files.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileCreateRequestHasher {

    public String hash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(normalized(file.getOriginalFilename()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(normalized(file.getContentType()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(Long.toString(file.getSize()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(file.getBytes());
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash file request", ex);
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value;
    }

    private String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
