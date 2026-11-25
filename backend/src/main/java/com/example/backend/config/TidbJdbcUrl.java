package com.example.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TidbJdbcUrl {
    private static final Set<String> TLS_PROPERTIES = Set.of(
            "sslmode", "usessl", "requiressl", "verifyservercertificate",
            "trustcertificatekeystoreurl", "trustcertificatekeystoretype",
            "trustcertificatekeystorepassword", "fallbacktosystemtruststore");

    private TidbJdbcUrl() { }

    public static String withVerifiedCa(String jdbcUrl, TidbTrustStoreFactory.TrustStoreMaterial trustStore) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("DB_URL must be a MySQL JDBC URL");
        }

        String[] parts = jdbcUrl.split("\\?", 2);
        List<String> options = new ArrayList<>();
        if (parts.length == 2 && !parts[1].isBlank()) {
            for (String option : parts[1].split("&")) {
                String key = option.split("=", 2)[0].replace("+", " ");
                if (!TLS_PROPERTIES.contains(key.toLowerCase(Locale.ROOT))) {
                    options.add(option);
                }
            }
        }

        options.add("sslMode=VERIFY_IDENTITY");
        options.add("fallbackToSystemTrustStore=false");
        options.add("trustCertificateKeyStoreUrl=" + trustStore.url());
        options.add("trustCertificateKeyStoreType=PKCS12");
        options.add("trustCertificateKeyStorePassword=" + trustStore.password());
        return parts[0] + "?" + String.join("&", options);
    }
}
