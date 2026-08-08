package com.onda.marketplace.denuncia;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Canal de denúncia (Épico 7/9): reportar prestador ou avaliação fraudulenta. A denúncia em
 * si não pune ninguém automaticamente — só entra na fila de moderação do admin (US26).
 */
@Service
@SuppressWarnings("null")
public class DenunciaService {

    private final DenunciaRepository  denunciaRepository;
    private final UserRepository      userRepository;
    private final ReviewRepository    reviewRepository;
    private final NotificationService notificationService;

    public DenunciaService(DenunciaRepository denunciaRepository,
                           UserRepository userRepository,
                           ReviewRepository reviewRepository,
                           NotificationService notificationService) {
        this.denunciaRepository  = denunciaRepository;
        this.userRepository      = userRepository;
        this.reviewRepository    = reviewRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public DenunciaDto criar(UUID denuncianteId, CreateDenunciaRequest req) {
        boolean alvoExiste = switch (req.tipo()) {
            case PRESTADOR -> userRepository.existsById(req.alvoId());
            case AVALIACAO -> reviewRepository.existsById(req.alvoId());
        };
        if (!alvoExiste) {
            throw new BusinessException("ALVO_NAO_ENCONTRADO",
                    "O prestador ou avaliação denunciado não foi encontrado.");
        }

        Denuncia denuncia = new Denuncia(req.tipo(), req.alvoId(), denuncianteId, req.motivo(), req.detalhes());
        denunciaRepository.save(denuncia);

        // Alerta ao admin — mesma central de notificações operacionais do SOS/disputa/verificação.
        notificationService.criarAlerta("DENUNCIA", denuncia.getId());

        return DenunciaDto.from(denuncia);
    }

    @Transactional(readOnly = true)
    public List<DenunciaAdminDto> listarAbertas() {
        return denunciaRepository.findByStatusOrderByCriadoEmDesc(StatusDenuncia.ABERTA).stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Transactional
    public void resolver(UUID id, UUID adminId) {
        Denuncia denuncia = denunciaRepository.findById(id)
                .orElseThrow(() -> new BusinessException("DENUNCIA_NOT_FOUND", "Denúncia não encontrada."));
        denuncia.resolver(adminId);
        denunciaRepository.save(denuncia);
    }

    private DenunciaAdminDto toAdminDto(Denuncia d) {
        return new DenunciaAdminDto(
                d.getId(),
                d.getTipo().name(),
                d.getAlvoId(),
                alvoLabel(d),
                userRepository.findById(d.getDenuncianteId()).map(User::getNome).orElse("Usuário removido"),
                d.getMotivo(),
                d.getDetalhes(),
                d.getStatus().name(),
                d.getCriadoEm());
    }

    private String alvoLabel(Denuncia d) {
        return switch (d.getTipo()) {
            case PRESTADOR -> userRepository.findById(d.getAlvoId())
                    .map(User::getNome).orElse("Prestador removido");
            case AVALIACAO -> reviewRepository.findById(d.getAlvoId())
                    .map(this::resumoAvaliacao).orElse("Avaliação removida");
        };
    }

    private String resumoAvaliacao(Review r) {
        String comentario = r.getComentario() == null ? "" : r.getComentario();
        String trecho = comentario.length() > 80 ? comentario.substring(0, 80) + "…" : comentario;
        return "Nota " + r.getNota() + (trecho.isBlank() ? "" : ": \"" + trecho + "\"");
    }
}
