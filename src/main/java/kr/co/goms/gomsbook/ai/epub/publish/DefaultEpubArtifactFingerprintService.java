/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.publish;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DefaultEpubArtifactFingerprintService implements EpubArtifactFingerprintService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    @Override
    public EpubArtifactFingerprint calculate(
            Path epubPath) throws IOException {

        validateEpubPath(epubPath);

        String sha256 = calculateSha256(epubPath);
        long fileSize = Files.size(epubPath);

        return new EpubArtifactFingerprint(sha256, fileSize);
    }

    private String calculateSha256(
            Path epubPath) throws IOException {

        MessageDigest digest = createMessageDigest();

        try (InputStream inputStream = Files.newInputStream(epubPath)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int length;

            while ((length = inputStream.read(buffer)) != -1) {

                digest.update(buffer, 0, length);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest createMessageDigest() {

        try {

            return MessageDigest.getInstance(HASH_ALGORITHM);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    private void validateEpubPath(
            Path epubPath) {

        if (epubPath == null) {

            throw new IllegalArgumentException("epubPath must not be null.");
        }

        if (!Files.isRegularFile(epubPath)) {

            throw new IllegalArgumentException("EPUB file does not exist: " + epubPath);
        }
    }
}