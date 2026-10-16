package com.onda.marketplace.servicerequest;

import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/** Objetos privados. A autorização deve ocorrer antes de chamar upload/readUrl. */
final class S3StorageService implements StorageService, AutoCloseable {
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern FOLDER = Pattern.compile("service-requests/" + UUID_PATTERN);
    private static final Pattern KEY = Pattern.compile("service-requests/" + UUID_PATTERN + "/" + UUID_PATTERN);
    private final S3Client client;
    private final S3Presigner signer;
    private final String bucket;
    private final Duration ttl;

    S3StorageService(S3Client client, S3Presigner signer, String bucket, Duration ttl) {
        this.client = client;
        this.signer = signer;
        this.bucket = bucket;
        this.ttl = ttl;
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        if (!FOLDER.matcher(folder).matches()) throw new IllegalArgumentException("Pasta de mídia inválida.");
        if (file.isEmpty() || file.getSize() > 15 * 1024 * 1024) {
            throw new BusinessException("INVALID_MEDIA_SIZE", "Envie um arquivo entre 1 byte e 15 MB.");
        }
        String key = folder + "/" + UUID.randomUUID();
        try (var input = file.getInputStream()) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .cacheControl("private, no-store").build(), RequestBody.fromInputStream(input, file.getSize()));
            return "s3://" + bucket + "/" + key;
        } catch (IOException | SdkException ex) {
            // Não expor corpo da resposta, credenciais ou URL assinada do provedor.
            throw new StorageUnavailableException();
        }
    }

    @Override
    public String readUrl(String reference) {
        // Compatibilidade com registros anteriores; arquivos do antigo stub não são recuperáveis.
        if (reference.startsWith("https://") || reference.startsWith("http://")) return reference;
        String prefix = "s3://" + bucket + "/";
        if (!reference.startsWith(prefix) || !KEY.matcher(reference.substring(prefix.length())).matches()) {
            throw new IllegalArgumentException("Referência de mídia inválida.");
        }
        try {
            return signer.presignGetObject(builder -> builder.signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(reference.substring(prefix.length()))
                            .responseCacheControl("private, no-store").build())).url().toExternalForm();
        } catch (SdkException ex) {
            throw new StorageUnavailableException();
        }
    }

    @Override
    public void delete(String reference) {
        String prefix = "s3://" + bucket + "/";
        if (!reference.startsWith(prefix) || !KEY.matcher(reference.substring(prefix.length())).matches()) {
            throw new IllegalArgumentException("Referência de mídia inválida.");
        }
        try {
            client.deleteObject(builder -> builder.bucket(bucket).key(reference.substring(prefix.length())));
        } catch (SdkException ex) {
            throw new StorageUnavailableException();
        }
    }

    @Override
    public void close() {
        try { client.close(); } finally { signer.close(); }
    }
}
