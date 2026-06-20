package com.onda.marketplace.servicerequest;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ServiceRequestService {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    private final ServiceRequestRepository requestRepository;
    private final ServiceMediaRepository   mediaRepository;
    private final UserRepository           userRepository;
    private final AiSuggestionService      aiService;
    private final StorageService           storageService;

    public ServiceRequestService(
            ServiceRequestRepository requestRepository,
            ServiceMediaRepository mediaRepository,
            UserRepository userRepository,
            AiSuggestionService aiService,
            StorageService storageService) {
        this.requestRepository = requestRepository;
        this.mediaRepository   = mediaRepository;
        this.userRepository    = userRepository;
        this.aiService         = aiService;
        this.storageService    = storageService;
    }

    @Transactional
    public ServiceRequestDto create(UUID clienteId, CreateServiceRequestRequest req, String idempotencyKey) {
        return requestRepository.findByIdempotencyKey(idempotencyKey)
                .map(ServiceRequestDto::from)
                .orElseGet(() -> criarNovo(clienteId, req, idempotencyKey));
    }

    @Transactional
    public MediaUploadResponse addMedia(UUID requestId, MultipartFile file, String tipoStr) {
        ServiceRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        String url = storageService.upload(file, "service-requests/" + requestId);
        MediaType tipo = MediaType.valueOf(tipoStr.toUpperCase());
        ServiceMedia media = new ServiceMedia(sr, tipo, url);
        mediaRepository.save(media);

        return new MediaUploadResponse(media.getId(), url, tipo.name());
    }

    private ServiceRequestDto criarNovo(UUID clienteId, CreateServiceRequestRequest req, String idempotencyKey) {
        User cliente = userRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));

        var sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setCategoria(req.categoria());
        sr.setDescricao(req.descricao());
        sr.setLocalizacao(GEO.createPoint(new Coordinate(req.lng(), req.lat())));
        sr.setIdempotencyKey(idempotencyKey);

        // IA com fallback manual — nunca bloqueia o pedido (princípio CLAUDE.md)
        try {
            aiService.suggest(req.descricao(), req.categoria())
                    .ifPresent(sr::aplicarSugestaoIA);
        } catch (Exception ignored) {
            // IA indisponível — pedido prossegue sem sugestão
        }

        requestRepository.save(sr);
        return ServiceRequestDto.from(sr);
    }
}
