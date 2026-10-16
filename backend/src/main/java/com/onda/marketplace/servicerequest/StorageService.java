package com.onda.marketplace.servicerequest;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /** Referência persistente; nunca persistir uma URL assinada com expiração. */
    String upload(MultipartFile file, String folder);
    void delete(String reference);
    default String readUrl(String reference) { return reference; }
}
