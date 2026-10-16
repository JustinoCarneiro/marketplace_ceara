package com.onda.marketplace.servicerequest;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
class StorageConfig {
    @Bean
    StorageService storageService(Environment environment,
            @Value("${storage.provider:s3}") String provider,
            @Value("${storage.bucket:}") String bucket,
            @Value("${storage.endpoint:}") String endpoint,
            @Value("${storage.region:us-east-1}") String region,
            @Value("${storage.access-key:}") String accessKey,
            @Value("${storage.secret-key:}") String secretKey,
            @Value("${storage.read-url-ttl-seconds:600}") long ttlSeconds) {
        if (provider.equals("stub")) {
            if (!environment.acceptsProfiles(Profiles.of("seed", "test", "e2e"))) {
                throw new IllegalStateException("Storage simulado permitido apenas nos profiles seed/test/e2e.");
            }
            return new StorageServiceImpl();
        }
        if (!provider.equals("s3") || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("Configure STORAGE_PROVIDER=s3, STORAGE_BUCKET, STORAGE_ACCESS_KEY e STORAGE_SECRET_KEY.");
        }
        if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") || ttlSeconds < 60 || ttlSeconds > 900) {
            throw new IllegalStateException("Bucket inválido ou validade de URL fora de 60–900 segundos.");
        }
        URI uri = endpoint.isBlank() ? null : URI.create(endpoint);
        if (uri != null && (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)) {
            throw new IllegalStateException("STORAGE_ENDPOINT deve ser HTTPS sem credenciais ou parâmetros.");
        }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var configuration = S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build();
        var client = S3Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).region(Region.of(region)).credentialsProvider(credentials)
                .serviceConfiguration(configuration).requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)).build());
        var signer = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(configuration);
        if (uri != null) { client.endpointOverride(uri); signer.endpointOverride(uri); }
        return new S3StorageService(client.build(), signer.build(), bucket, Duration.ofSeconds(ttlSeconds));
    }
}
