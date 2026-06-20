package com.onda.marketplace.servicerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

    @Override
    public String upload(MultipartFile file, String folder) {
        // Stub: substituir por S3/GCS em produção. Banco guarda apenas a URL (TS07).
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String url = "https://storage.marketplace-ceara.com/" + folder + "/" + filename;
        log.debug("Storage stub: {} → {}", file.getOriginalFilename(), url);
        return url;
    }
}
