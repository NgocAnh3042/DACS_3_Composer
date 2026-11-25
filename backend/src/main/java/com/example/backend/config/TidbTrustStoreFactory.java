package com.example.backend.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TidbTrustStoreFactory {
    public TrustStoreMaterial create(Path caCertificate) {
        try {
            if (!Files.isRegularFile(caCertificate)) {
                throw new IllegalStateException("TiDB CA certificate file is missing");
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            List<? extends Certificate> certificates;
            try (InputStream input = Files.newInputStream(caCertificate)) {
                certificates = List.copyOf(certificateFactory.generateCertificates(input));
            }
            if (certificates.isEmpty()) {
                throw new IllegalStateException("TiDB CA certificate file is empty");
            }

            char[] password = new char[32];
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            char[] hex = HexFormat.of().formatHex(random).toCharArray();
            System.arraycopy(hex, 0, password, 0, password.length);

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            for (int i = 0; i < certificates.size(); i++) {
                keyStore.setCertificateEntry("tidb-ca-" + i, certificates.get(i));
            }

            Path trustStore = Files.createTempFile("foodhub-tidb-ca-", ".p12");
            try (OutputStream output = Files.newOutputStream(trustStore)) {
                keyStore.store(output, password);
            }
            trustStore.toFile().deleteOnExit();
            return new TrustStoreMaterial(trustStore.toUri().toString(), new String(password));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize the TiDB TLS trust store");
        }
    }

    public record TrustStoreMaterial(String url, String password) { }
}
