package com.onda.marketplace.servicerequest;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
    private final ProposalRepository       proposalRepository;
    private final TransactionRepository    transactionRepository;

    public ServiceRequestService(
            ServiceRequestRepository requestRepository,
            ServiceMediaRepository mediaRepository,
            UserRepository userRepository,
            AiSuggestionService aiService,
            StorageService storageService,
            ProposalRepository proposalRepository,
            TransactionRepository transactionRepository) {
        this.requestRepository     = requestRepository;
        this.mediaRepository       = mediaRepository;
        this.userRepository        = userRepository;
        this.aiService             = aiService;
        this.storageService        = storageService;
        this.proposalRepository    = proposalRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public ServiceRequestDto create(UUID clienteId, CreateServiceRequestRequest req, String idempotencyKey) {
        return requestRepository.findByIdempotencyKey(idempotencyKey)
                .map(ServiceRequestDto::from)
                .orElseGet(() -> criarNovo(clienteId, req, idempotencyKey));
    }

    /**
     * Anexa mídia (foto/áudio) a um pedido já existente. Mesma regra de participação de
     * {@link #detalhar}: sem isso, qualquer usuário autenticado podia anexar mídia a
     * pedido de terceiros.
     */
    @Transactional
    public MediaUploadResponse addMedia(UUID requestId, MultipartFile file, String tipoStr, UUID userId) {
        ServiceRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (!requestRepository.isParticipante(requestId, userId)) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        MediaType tipo = parseTipo(tipoStr);
        validarContentType(tipo, file);

        String url = storageService.upload(file, "service-requests/" + requestId);
        ServiceMedia media = new ServiceMedia(sr, tipo, url);
        mediaRepository.save(media);

        return new MediaUploadResponse(media.getId(), url, tipo.name());
    }

    private MediaType parseTipo(String tipoStr) {
        try {
            return MediaType.valueOf(tipoStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_MEDIA_TYPE", "Tipo de mídia inválido: " + tipoStr);
        }
    }

    private void validarContentType(MediaType tipo, MultipartFile file) {
        String contentType = file.getContentType();
        boolean valido = switch (tipo) {
            case FOTO -> contentType != null && contentType.startsWith("image/");
            case AUDIO -> contentType != null && contentType.startsWith("audio/");
            case TEXTO -> true;
        };
        if (!valido) {
            throw new BusinessException("INVALID_MEDIA_CONTENT_TYPE",
                    "Arquivo não corresponde ao tipo declarado (" + tipo + ").");
        }
    }

    /** Pedidos do cliente logado, mais recentes primeiro (MyRequestsScreen). */
    @Transactional(readOnly = true)
    public List<MyRequestDto> listarMeusPedidos(UUID clienteId) {
        return requestRepository.findByClienteIdOrderByUpdatedAtDesc(clienteId).stream()
                .map(sr -> new MyRequestDto(
                        sr.getId(),
                        sr.getCategoria(),
                        sr.getDescricao(),
                        sr.getStatus().name(),
                        sr.getUpdatedAt(),
                        prestadorNome(sr.getId()),
                        proposalRepository.findByServiceRequestIdAndStatus(sr.getId(), ProposalStatus.ATIVA).size(),
                        transactionRepository.findByServiceRequestId(sr.getId())
                                .map(t -> new MyRequestDto.Transacao(t.getValorTotal()))
                                .orElse(null)))
                .toList();
    }

    /**
     * Detalhe do pedido. Autorização por participação: só cliente dono ou prestador com
     * proposta ACEITA enxergam — não vaza pedido de terceiros.
     */
    @Transactional(readOnly = true)
    public ServiceRequestDetailDto detalhar(UUID requestId, UUID userId) {
        ServiceRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (!requestRepository.isParticipante(requestId, userId)) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        UUID prestadorId = prestadorAceito(requestId).map(Proposal::getPrestadorId).orElse(null);

        return new ServiceRequestDetailDto(
                sr.getId(),
                sr.getStatus().name(),
                sr.getCategoria(),
                sr.getDescricao(),
                sr.getCliente().getId(),
                prestadorId,
                prestadorNome(requestId),
                sr.getCreatedAt(),
                sr.getUpdatedAt(),
                transactionRepository.findByServiceRequestId(requestId)
                        .map(t -> new ServiceRequestDetailDto.Transacao(
                                t.getStatusPagamento().name(), t.getValorTotal(), t.getValorComissao()))
                        .orElse(null));
    }

    /**
     * Pedido que o prestador logado está atendendo agora (ACEITO ou EM_ANDAMENTO) — resolve
     * a tab "Em Andamento" do app, que precisa de um requestId pra abrir o RequestDetail.
     * MVP não tem múltiplos atendimentos simultâneos: pega o mais recente entre os elegíveis.
     */
    @Transactional(readOnly = true)
    public UUID buscarPedidoAtivoDoPrestador(UUID prestadorId) {
        return proposalRepository.findByPrestadorIdAndStatus(prestadorId, ProposalStatus.ACEITA).stream()
                .map(p -> p.getServiceRequest().getId())
                .map(id -> requestRepository.findById(id).orElse(null))
                .filter(sr -> sr != null && (sr.getStatus() == ServiceRequestStatus.ACEITO
                        || sr.getStatus() == ServiceRequestStatus.EM_ANDAMENTO))
                .max(java.util.Comparator.comparing(ServiceRequest::getUpdatedAt))
                .map(ServiceRequest::getId)
                .orElse(null);
    }

    /** Sugestão da IA já persistida (nula quando indisponível — fallback manual na tela). */
    @Transactional(readOnly = true)
    public AiSuggestionDto buscarSugestaoIa(UUID requestId, UUID clienteId) {
        return AiSuggestionDto.from(carregarDoCliente(requestId, clienteId));
    }

    /** Confirma a descrição final (o cliente pode ter editado a sugestão da IA). */
    @Transactional
    public ServiceRequestDto publicar(UUID requestId, UUID clienteId, PublishRequest req) {
        ServiceRequest sr = carregarDoCliente(requestId, clienteId);

        if (sr.getStatus() != ServiceRequestStatus.PENDENTE) {
            throw new BusinessException("INVALID_TRANSITION",
                    "Só um pedido PENDENTE pode ser publicado.");
        }

        sr.setDescricao(req.descricao());
        requestRepository.save(sr);
        return ServiceRequestDto.from(sr);
    }

    /** Fila aberta do prestador: pedidos ainda sem proposta aceita. */
    @Transactional(readOnly = true)
    public List<AvailableRequestDto> listarDisponiveis() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(ServiceRequestStatus.PENDENTE)
                .stream()
                .map(AvailableRequestDto::from)
                .toList();
    }

    // --- privado ---

    private ServiceRequest carregarDoCliente(UUID requestId, UUID clienteId) {
        return requestRepository.findByIdAndCliente_Id(requestId, clienteId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));
    }

    private java.util.Optional<Proposal> prestadorAceito(UUID requestId) {
        return proposalRepository.findByServiceRequestIdAndStatus(requestId, ProposalStatus.ACEITA)
                .stream().findFirst();
    }

    private String prestadorNome(UUID requestId) {
        return prestadorAceito(requestId)
                .flatMap(p -> userRepository.findById(p.getPrestadorId()))
                .map(User::getNome)
                .orElse(null);
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
