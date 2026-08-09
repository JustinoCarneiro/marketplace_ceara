package com.onda.marketplace.config;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Semeia os dados mínimos exigidos pelos testes E2E do painel admin (Playwright).
 * Ativo APENAS com o profile {@code seed} (usado no CI de E2E do admin) — nunca em
 * produção. Idempotente: cada bloco só grava se o registro-marca ainda não existir.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    // idempotencyKey fixo da transação-semente — é a marca usada para não duplicar a disputa a cada boot.
    private static final String DISPUTE_SEED_KEY = "seed-dispute-e2e";

    private final UserRepository            userRepository;
    private final PasswordEncoder           passwordEncoder;
    private final ProviderProfileRepository providerProfileRepository;
    private final ServiceRequestRepository  serviceRequestRepository;
    private final TransactionRepository     transactionRepository;

    public SeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      ProviderProfileRepository providerProfileRepository,
                      ServiceRequestRepository serviceRequestRepository,
                      TransactionRepository transactionRepository) {
        this.userRepository            = userRepository;
        this.passwordEncoder           = passwordEncoder;
        this.providerProfileRepository = providerProfileRepository;
        this.serviceRequestRepository  = serviceRequestRepository;
        this.transactionRepository     = transactionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsuario("Administrador Onda", "admin@onda.com",  "admin123",  UserRole.ROLE_ADMIN);
        User maria = seedUsuario("Maria Teste", "maria@teste.com", "Senha@123", UserRole.ROLE_CLIENT);

        seedPrestadorInconclusivo();
        seedPrestadorVerificado();
        seedDisputaAberta(maria);
    }

    private User seedUsuario(String nome, String email, String senha, UserRole role) {
        var existente = userRepository.findByEmail(email);
        if (existente.isPresent()) {
            log.info("[seed] {} já existe — ignorando", email);
            return existente.get();
        }
        User user = User.builder()
                .nome(nome)
                .email(email)
                .senhaHash(passwordEncoder.encode(senha))
                .role(role)
                .build();
        userRepository.save(user);
        log.info("[seed] usuário {} ({}) criado", email, role);
        return user;
    }

    /** Prestador em EM_VERIFICACAO — alvo dos testes de moderação (verify/reject) do painel. */
    private void seedPrestadorInconclusivo() {
        User provedor = seedUsuario("João Prestador", "joao.prestador@teste.com",
                "Senha@123", UserRole.ROLE_PROVIDER);

        if (providerProfileRepository.findByUserId(provedor.getId()).isPresent()) {
            log.info("[seed] perfil de prestador de {} já existe — ignorando", provedor.getEmail());
            return;
        }
        ProviderProfile perfil = new ProviderProfile(provedor, "Elétrica", "seed-cpf-cifrado-placeholder");
        providerProfileRepository.save(perfil);
        log.info("[seed] ProviderProfile de {} criado (EM_VERIFICACAO)", provedor.getEmail());
    }

    /**
     * Prestador VERIFICADO e COM localização — sem ele, `GET /providers/nearby` devolvia
     * lista vazia em todo ambiente semeado (a query exige VERIFICADO + localizacao NOT NULL),
     * ou seja, a busca por proximidade, que é o Épico 2, não dava para demonstrar nem testar.
     * Fica fora da fila de moderação do admin, que filtra EM_VERIFICACAO por padrão.
     */
    private void seedPrestadorVerificado() {
        User provedor = seedUsuario("Ana Eletricista", "ana.eletricista@teste.com",
                "Senha@123", UserRole.ROLE_PROVIDER);

        if (providerProfileRepository.findByUserId(provedor.getId()).isPresent()) {
            log.info("[seed] perfil de prestador de {} já existe — ignorando", provedor.getEmail());
            return;
        }
        ProviderProfile perfil = new ProviderProfile(provedor, "Elétrica", "seed-cpf-cifrado-placeholder");
        perfil.aprovar();
        // Aldeota, Fortaleza — mesma referência usada como fallback no app.
        var fabrica = new org.locationtech.jts.geom.GeometryFactory(
                new org.locationtech.jts.geom.PrecisionModel(), 4326);
        perfil.setLocalizacao(fabrica.createPoint(
                new org.locationtech.jts.geom.Coordinate(-38.5267, -3.7319)));
        providerProfileRepository.save(perfil);
        log.info("[seed] ProviderProfile de {} criado (VERIFICADO, com localização)", provedor.getEmail());
    }

    /**
     * Pedido EM_DISPUTA + transação RETIDO — alvo do teste de mediação (resolver disputa)
     * do painel. Sem Proposal/prestador associado: a fila de disputas do admin
     * (DisputaAdminDto/DisputaDetalheDto) não referencia prestador, só o pedido e a transação.
     */
    private void seedDisputaAberta(User cliente) {
        if (serviceRequestRepository
                .findByIdempotencyKeyAndCliente_Id(DISPUTE_SEED_KEY, cliente.getId()).isPresent()) {
            log.info("[seed] disputa de exemplo já existe — ignorando");
            return;
        }

        ServiceRequest sr = new ServiceRequest();
        sr.setCliente(cliente);
        sr.setCategoria("Hidráulica");
        sr.setDescricao("Vazamento no encanamento da cozinha — pedido semente para E2E do painel admin.");
        sr.setStatus(ServiceRequestStatus.EM_DISPUTA);
        sr.setIdempotencyKey(DISPUTE_SEED_KEY);
        serviceRequestRepository.save(sr);

        Transaction tx = new Transaction(sr.getId(), new BigDecimal("250.00"), new BigDecimal("25.00"),
                new BigDecimal("10"), PaymentMethod.PIX, DISPUTE_SEED_KEY);
        tx.reter();
        transactionRepository.save(tx);

        log.info("[seed] disputa de exemplo criada (serviceRequestId={})", sr.getId());
    }
}
