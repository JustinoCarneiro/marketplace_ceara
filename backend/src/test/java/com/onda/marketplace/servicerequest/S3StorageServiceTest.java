package com.onda.marketplace.servicerequest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class S3StorageServiceTest {
    HttpServer server;
    S3StorageService storage;
    final AtomicReference<byte[]> bytes = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    int responseCode = 200;
    final AtomicReference<String> deletedPath = new AtomicReference<>();
    String folder = "service-requests/" + UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestMethod().equals("PUT")) {
                bytes.set(exchange.getRequestBody().readAllBytes());
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                exchange.getResponseHeaders().add("ETag", "\"test-etag\"");
                exchange.sendResponseHeaders(responseCode, -1);
            } else if (exchange.getRequestMethod().equals("DELETE")) {
                deletedPath.set(exchange.getRequestURI().getPath());
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(200, bytes.get().length);
                exchange.getResponseBody().write(bytes.get());
            }
            exchange.close();
        });
        server.start();
        var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret"));
        var configuration = S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build();
        var client = S3Client.builder().httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder()).endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(configuration)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).build();
        var signer = S3Presigner.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(credentials).serviceConfiguration(configuration).build();
        storage = new S3StorageService(client, signer, "private-media", Duration.ofMinutes(10));
    }

    @AfterEach
    void cleanup() {
        if (storage != null) storage.close();
        if (server != null) server.stop(0);
    }

    @Test
    void uploadsActualBytesAndOnlyReturnsTemporaryReadUrl() throws Exception {
        byte[] original = new byte[]{0, 1, 2, (byte) 255};
        String reference = storage.upload(new MockMultipartFile("file", "nome-pessoal.jpg", "image/jpeg", original), folder);
        assertThat(reference).startsWith("s3://private-media/" + folder + "/")
                .doesNotContain("nome-pessoal", "X-Amz", "test-access");
        assertThat(bytes.get()).isEqualTo(original);
        assertThat(contentType.get()).isEqualTo("image/jpeg");
        String signed = storage.readUrl(reference);
        assertThat(signed).contains("X-Amz-Expires=600", "X-Amz-Signature=");
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(signed)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.body()).isEqualTo(original);
    }

    @Test
    void providerFailureDoesNotReturnFakeSuccess() {
        responseCode = 403;
        assertThatThrownBy(() -> storage.upload(new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1}), folder))
                .isInstanceOf(StorageUnavailableException.class).hasMessage("Armazenamento temporariamente indisponível.");
    }

    @Test
    void cannotSignOtherBucketsOrUnexpectedKeys() {
        assertThatThrownBy(() -> storage.readUrl("s3://other-bucket/" + folder + "/" + UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.readUrl("s3://private-media/secrets/key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.upload(new MockMultipartFile("file", new byte[]{1}), "../other"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bytes.get()).isNull();
    }

    @Test
    void deletesOnlyObjectFromConfiguredBucket() {
        String reference = "s3://private-media/" + folder + "/" + UUID.randomUUID();
        storage.delete(reference);
        assertThat(deletedPath.get()).isEqualTo("/" + reference.substring("s3://".length()));
        deletedPath.set(null);
        assertThatThrownBy(() -> storage.delete(reference.replace("private-media", "another-bucket")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(deletedPath.get()).isNull();
    }

    @Test
    void legacyUrlsRemainReadable() {
        assertThat(storage.readUrl("https://storage.example.com/old.jpg")).isEqualTo("https://storage.example.com/old.jpg");
    }
}
