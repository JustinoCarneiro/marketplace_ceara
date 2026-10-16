package com.onda.marketplace.servicerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

    @Override
    public void delete(String reference) {
        if (reference.startsWith("s3://")) throw new StorageUnavailableException();
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        // Simulação explícita de seed/test/e2e; não armazena bytes.
        String filename = UUID.randomUUID().toString();
        String url = "https://storage.marketplace-ceara.com/" + folder + "/" + filename;
        log.debug("Upload simulado em ambiente de teste/demo.");
        return url;
    }
}
