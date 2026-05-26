package com.xqt.saas.labels;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地磁盘实现的 {@link LabelStorage}。默认根目录可通过 application.yml 配置：
 *   app.labels.storage.root  默认 ./files
 *   app.labels.base-url      默认 /api/customer-api/labels/file
 *
 * 文件布局：`<root>/<yyyyMMdd>/<sha1-hex>.<ext>`，对齐 ACC `files/YYYYMMDD/<hash>.<ext>`。
 */
@Component
public class LocalFileLabelStorage implements LabelStorage {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path root;
    private final String baseUrl;

    public LocalFileLabelStorage(
        @Value("${app.labels.storage.root:./files}") String rootDir,
        @Value("${app.labels.base-url:/api/customer-api/labels/file}") String baseUrl
    ) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
    }

    @Override
    public StoredFile save(String tenantId, byte[] content, String fileExt) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }
        String hash = sha1Hex(content);
        String date = DATE_FMT.format(LocalDate.now());
        Path dir = root.resolve(date);
        Path file = dir.resolve(hash + "." + fileExt);
        try {
            Files.createDirectories(dir);
            if (!Files.exists(file)) {
                Files.write(file, content);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("write label file failed: " + file, ex);
        }
        return new StoredFile(hash, fileExt, file.toString(), content.length, date);
    }

    @Override
    public byte[] load(StoredFile file) {
        try {
            return Files.readAllBytes(Paths.get(file.storagePath()));
        } catch (IOException ex) {
            throw new IllegalStateException("read label file failed: " + file.storagePath(), ex);
        }
    }

    @Override
    public String publicUrl(StoredFile file) {
        return baseUrl + "/" + file.createdDate() + "/" + file.fileHash() + "." + file.fileExt();
    }

    private static String sha1Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(content);
            StringBuilder hex = new StringBuilder(40);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }
}
