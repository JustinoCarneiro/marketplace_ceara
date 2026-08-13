package com.onda.marketplace.message;

import com.onda.marketplace.servicerequest.ServiceRequest;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem do chat pré-transação entre cliente e prestador de um pedido
 * (docs/BOAS_PRATICAS_UX.md §1). O conteúdo já chega mascarado (se aplicável) antes de
 * persistir — {@code mascarado} é só o sinal pra tela mostrar o aviso, o dado bruto com
 * telefone/e-mail nunca é gravado.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Column(name = "remetente_id", nullable = false)
    private UUID remetenteId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(nullable = false)
    private boolean mascarado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Message() {}

    public Message(ServiceRequest serviceRequest, UUID remetenteId, String conteudo, boolean mascarado) {
        this.serviceRequest = serviceRequest;
        this.remetenteId    = remetenteId;
        this.conteudo       = conteudo;
        this.mascarado      = mascarado;
    }

    public UUID getId()                       { return id; }
    public ServiceRequest getServiceRequest()  { return serviceRequest; }
    public UUID getRemetenteId()               { return remetenteId; }
    public String getConteudo()                { return conteudo; }
    public boolean isMascarado()               { return mascarado; }
    public Instant getCreatedAt()              { return createdAt; }
}
