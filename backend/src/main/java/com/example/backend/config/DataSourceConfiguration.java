package com.example.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(FoodhubDatabaseProperties.class)
public class DataSourceConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    @Profile("!test")
    public HikariConfig foodhubHikariConfig() {
        return new HikariConfig();
    }

    @Bean
    @Profile("!test")
    public DataSource dataSource(
            FoodhubDatabaseProperties properties,
            TidbTrustStoreFactory trustStoreFactory,
            HikariConfig foodhubHikariConfig) {
        TidbTrustStoreFactory.TrustStoreMaterial trustStore =
                trustStoreFactory.create(Path.of(properties.getCaCert()));

        Properties dataSourceProperties = new Properties();
        dataSourceProperties.setProperty("sslMode", "VERIFY_IDENTITY");
        dataSourceProperties.setProperty("fallbackToSystemTrustStore", "false");
        dataSourceProperties.setProperty("trustCertificateKeyStoreUrl", trustStore.url());
        dataSourceProperties.setProperty("trustCertificateKeyStoreType", "PKCS12");
        dataSourceProperties.setProperty("trustCertificateKeyStorePassword", trustStore.password());

        foodhubHikariConfig.setPoolName("FoodHubTiDBPool");
        foodhubHikariConfig.setJdbcUrl(TidbJdbcUrl.withVerifiedCa(properties.getUrl(), trustStore));
        foodhubHikariConfig.setUsername(properties.getUsername());
        foodhubHikariConfig.setPassword(properties.getPassword());
        foodhubHikariConfig.setDataSourceProperties(dataSourceProperties);
        foodhubHikariConfig.setMaximumPoolSize(5);
        foodhubHikariConfig.setMaxLifetime(300_000);
        foodhubHikariConfig.setConnectionTimeout(10_000);
        foodhubHikariConfig.setConnectionInitSql("SET time_zone = '+07:00'");
        return new HikariDataSource(foodhubHikariConfig);
    }
}
