package com.onda.marketplace.e2e;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.onda.marketplace.auth.AuthService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.denuncia.Denuncia;
import com.onda.marketplace.denuncia.DenunciaRepository;
import com.onda.marketplace.denuncia.TipoDenuncia;
import com.onda.marketplace.message.Message;
import com.onda.marketplace.message.MessageRepository;
import com.onda.marketplace.notification.UserMailSender;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.servicerequest.MediaType;
import com.onda.marketplace.servicerequest.ServiceMedia;
import com.onda.marketplace.servicerequest.ServiceMediaRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.sos.SosAlert;
import com.onda.marketplace.sos.SosAlertRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Testes E2E do caminho do dinheiro completo.
 *
 * Sobe PostgreSQL+PostGIS real via Testcontainers e o contexto Spring completo.
 * Não usa mocks — valida a integração real entre camadas.
 *
 * Fluxo coberto:
 *   Registro cliente → Registro prestador → Criação do pedido
 *   → Proposta → Aceite → Pagamento (webhook simula gateway)
 *   → Início do serviço → Conclusão → Avaliação bidirecional
 *
 * Depois do fluxo: relatórios (US29), recuperação de senha (US35), suspensão com corte imediato do token
 * (US26) e exclusão de conta (US36) — cada tabela conferida no Postgres real.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2EFluxoPrincipalTest {

    // PostGIS image — mesma usada no docker-compose
    // @Testcontainers gerencia o ciclo de vida; IDE não enxerga isso via @Container
    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:15-3.4")
                                   .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("onda_e2e")
                    .withUsername("onda_user")
                    .withPassword("onda_pass");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",     postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Caixa de entrada de mentira (só no contexto de teste): o código de recuperação de senha só
     * existe no e-mail — não há endpoint nem log que o exponha — então o teste o lê daqui, igual
     * ao usuário lendo o e-mail. @Primary vence o remetente real/desligado do NotificationConfig.
     */
    @TestConfiguration
    static class CaixaDeEntrada {
        record Email(String para, String assunto, String corpo) {}
        static final List<Email> EMAILS = new CopyOnWriteArrayList<>();

        @Bean @Primary
        UserMailSender remetenteDeTeste() {
            return (para, assunto, texto) -> EMAILS.add(new Email(para, assunto, texto));
        }
    }

    @LocalServerPort int port;

    /** Lê o id que o gateway gerou — é o que o gateway real saberia ao chamar nosso webhook. */
    @Autowired JdbcTemplate jdbc;

    // Só o passo dos relatórios (US29) usa estes: cria um admin e um 2º pedido direto no banco.
    @Autowired UserRepository           userRepository;
    @Autowired ServiceRequestRepository serviceRequestRepository;
    @Autowired TransactionRepository    transactionRepository;
    @Autowired PasswordEncoder          passwordEncoder;
    @Autowired AuthService              authService;
    @Autowired com.onda.marketplace.auth.PasswordResetCodeRepository passwordResetCodeRepository;
    @Autowired org.springframework.transaction.support.TransactionTemplate transacao;
    // Exclusão de conta (US36): monta o estado de cada cenário direto no banco.
    @Autowired ProposalRepository       proposalRepository;
    @Autowired MessageRepository        messageRepository;
    @Autowired ReviewRepository         reviewRepository;
    @Autowired ServiceMediaRepository   mediaRepository;
    @Autowired SosAlertRepository       sosAlertRepository;
    @Autowired DenunciaRepository       denunciaRepository;

    // Estado compartilhado entre os steps (JUnit @Order garante sequência)
    static String tokenCliente;
    static String tokenPrestador;
    static String prestadorId;
    static String requestId;
    static String proposalId;
    static String transactionId;
    static String gatewayTxId;

    static final String WEBHOOK_SECRET = "test-webhook-secret";

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    // ─── Épico 1 — Identidade ──────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("01 · Registrar cliente")
    void registrarCliente() {
        tokenCliente = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome":  "Maria Fortaleza",
                          "email": "maria.e2e@onda.test",
                          "senha": "Senha@123",
                          "aceitouTermos": true
                        }
                        """)
                .when()
                .post("/api/v1/auth/register/client")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("role", equalTo("ROLE_CLIENT"))
                .extract().path("accessToken");
    }

    @Test @Order(2)
    @DisplayName("02 · Registrar prestador")
    void registrarPrestador() {
        var resp = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome":      "João Eletricista",
                          "email":     "joao.e2e@onda.test",
                          "senha":     "Senha@123",
                          "cpf":       "123.456.789-09",
                          "categoria": "eletrica",
                          "bio":       "Eletricista há 12 anos em Fortaleza.",
                          "aceitouTermos": true
                        }
                        """)
                .when()
                .post("/api/v1/auth/register/provider")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("role", equalTo("ROLE_PROVIDER"))
                .extract();

        tokenPrestador = resp.path("accessToken");
        prestadorId    = resp.path("userId");
    }

    @Test @Order(3)
    @DisplayName("03 · Login com credenciais válidas retorna token")
    void loginCliente() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "maria.e2e@onda.test",
                          "senha": "Senha@123"
                        }
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("role", equalTo("ROLE_CLIENT"));
    }

    // ─── Épico 3 — Criação do pedido ──────────────────────────────────────

    @Test @Order(4)
    @DisplayName("04 · Cliente cria pedido de serviço")
    void criarPedido() {
        requestId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .body("""
                        {
                          "categoria":  "eletrica",
                          "descricao":  "Tomada sem funcionar no quarto",
                          "lat":        -3.7172,
                          "lng":       -38.5433,
                          "bairro":     "Aldeota"
                        }
                        """)
                .when()
                .post("/api/v1/service-requests")
                .then()
                .statusCode(201)
                .body("status", equalTo("PENDENTE"))
                .body("categoria", equalTo("eletrica"))
                .extract().path("id");
    }

    @Test @Order(5)
    @DisplayName("05 · Idempotência — mesmo key retorna 200 sem duplicar pedido")
    void idempotenciaRequest() {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {
                  "categoria":  "eletrica",
                  "descricao":  "Pedido idempotente",
                  "lat":        -3.7172,
                  "lng":       -38.5433
                }
                """;

        String id1 = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .header("X-Idempotency-Key", idempotencyKey)
                .body(body)
                .when().post("/api/v1/service-requests")
                .then().statusCode(anyOf(is(200), is(201)))
                .extract().path("id");

        String id2 = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .header("X-Idempotency-Key", idempotencyKey)
                .body(body)
                .when().post("/api/v1/service-requests")
                .then().statusCode(anyOf(is(200), is(201)))
                .extract().path("id");

        Assertions.assertEquals(id1, id2, "Mesma idempotency key deve retornar o mesmo recurso");
    }

    // ─── Épico 4 — Proposta ───────────────────────────────────────────────

    @Test @Order(6)
    @DisplayName("06 · Prestador só propõe depois de aprovado pelo admin; aí envia a proposta")
    void enviarProposta() {
        String proposta = """
                {
                  "valor":           250.00,
                  "prazoDias":       1,
                  "horarioProposto": "%s"
                }
                """.formatted(Instant.now().plus(2, ChronoUnit.DAYS));

        // Recém-cadastrado, o prestador está EM_VERIFICACAO. Antes, a "aprovação manual" do admin só
        // mudava o status e nada barrava: ele propunha e recebia do mesmo jeito.
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenPrestador)
                .body(proposta)
                .when()
                .post("/api/v1/service-requests/{id}/proposals", requestId)
                .then()
                .statusCode(422)
                .body("code", equalTo("PROVIDER_NOT_VERIFIED"))
                .body("message", containsString("em verificação"));
        // a recusa não grava nada
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .get("/api/v1/service-requests/{id}/proposals", requestId)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        moderarPrestador("APROVAR");

        proposalId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenPrestador)
                .body(proposta)
                .when()
                .post("/api/v1/service-requests/{id}/proposals", requestId)
                .then()
                .statusCode(201)
                .body("valor",    equalTo(250.0f))
                .body("status",   equalTo("ATIVA"))
                .extract().path("id");
    }

    @Test @Order(7)
    @DisplayName("07 · Cliente visualiza lista de propostas do pedido")
    void listarPropostas() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .get("/api/v1/service-requests/{id}/proposals", requestId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].valor", equalTo(250.0f));
    }

    // ─── Épico 5 — Pagamento e Escrow ─────────────────────────────────────

    @Test @Order(8)
    @DisplayName("08 · Cliente aceita proposta → pedido vira ACEITO (e não aceita prestador que o admin barrou)")
    void aceitarProposta() {
        // O prestador estava VERIFICADO quando propôs, mas o admin pode suspendê-lo antes do aceite:
        // o cliente não pode pagar quem já foi barrado, e a proposta continua valendo.
        moderarPrestador("SUSPENDER");
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .put("/api/v1/proposals/{id}/accept", proposalId)
                .then()
                .statusCode(422)
                .body("code", equalTo("PROVIDER_NOT_VERIFIED"))
                .body("message", containsString("Escolha outra proposta"));
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .get("/api/v1/service-requests/{id}/proposals", requestId)
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("ATIVA"));
        moderarPrestador("APROVAR");

        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .put("/api/v1/proposals/{id}/accept", proposalId)
                .then()
                .statusCode(200)
                // resposta é o ProposalDto: status da PROPOSTA = ACEITA
                // (o service_request, por sua vez, transiciona para ACEITO)
                .body("status", equalTo("ACEITA"));
    }

    @Test @Order(9)
    @DisplayName("09 · Cliente inicia pagamento → transação PENDENTE criada")
    void iniciarPagamento() {
        // Antifraude Camada 2: o cliente confirma identidade (CPF) antes do 1º
        // pagamento — espelha o modal de CPF do app. Sem isso o pagamento é
        // bloqueado com IDENTITY_REQUIRED (422). CPF distinto do prestador.
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .body("""
                        { "cpf": "111.444.777-35" }
                        """)
                .when()
                .post("/api/v1/auth/verify-identity")
                .then()
                .statusCode(204);

        var resp = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .body("""
                        { "metodo": "PIX" }
                        """)
                .when()
                .post("/api/v1/service-requests/{id}/payment", requestId)
                .then()
                .statusCode(anyOf(is(200), is(201)))
                .body("statusPagamento", equalTo("PENDENTE"))
                // profile e2e define marketplace.comissao=0.15: 15% de 250 = 37.50 na cobrança
                .body("valorComissao", equalTo(37.5f))
                .extract().response();

        transactionId = resp.path("id");

        // O percentual que o app do prestador mostra ("Você recebe após comissão") é o MESMO
        // que a cobrança acabou de aplicar — uma fonte só. O prestador consulta com o próprio
        // token: prova que a rota está liberada pra ele no SecurityConfig real (o slice
        // @WebMvcTest libera tudo e não pegaria isso); sem token continua fechada.
        given()
                .header("Authorization", "Bearer " + tokenPrestador)
                .when()
                .get("/api/v1/payments/comissao")
                .then()
                .statusCode(200)
                .body("percentualComissao", equalTo(0.15f));
        given()
                .when()
                .get("/api/v1/payments/comissao")
                .then()
                .statusCode(401);

        // A cobrança é despachada ao gateway de forma assíncrona (OutboxProcessor, fora de
        // @Transactional) — o gateway_transaction_id só existe DEPOIS disso, nunca na
        // resposta deste POST. Antes o teste lia daqui (sempre null), caía no fallback e
        // mandava o webhook com um id inexistente: confirmava nada e mesmo assim passava.
        gatewayTxId = aguardarGatewayTxId();
        Assertions.assertNotNull(gatewayTxId, "OutboxProcessor não despachou a cobrança ao gateway");
    }

    /** Aguarda o OutboxProcessor despachar a cobrança (profile e2e: varredura a cada 500ms). */
    private String aguardarGatewayTxId() {
        for (int i = 0; i < 40; i++) {
            var ids = jdbc.queryForList(
                    "SELECT gateway_transaction_id FROM transactions WHERE id = ?::uuid",
                    String.class, transactionId);
            if (!ids.isEmpty() && ids.get(0) != null) return ids.get(0);
            try { Thread.sleep(250); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    @Test @Order(10)
    @DisplayName("10 · Webhook do gateway confirma pagamento → transação RETIDA")
    void webhookConfirmarPagamento() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Webhook-Secret", WEBHOOK_SECRET)
                .body(String.format("""
                        {
                          "gatewayTransactionId": "%s",
                          "status": "PAGO"
                        }
                        """, gatewayTxId))
                .when()
                .post("/api/v1/payments/webhook")
                .then()
                .statusCode(anyOf(is(200), is(204)));

        // O webhook responde 200 mesmo quando não acha a transação (não vaza existência de id).
        // Sem esta asserção o passo passava sem ter retido nada — foi assim que o dinheiro
        // seguia PENDENTE por todo o fluxo.
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .get("/api/v1/transactions/{srId}", requestId)
                .then()
                .statusCode(200)
                .body("statusPagamento", equalTo("RETIDO"));
    }

    @Test @Order(11)
    @DisplayName("11 · Webhook com secret errado retorna 401")
    void webhookSecretInvalido() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Webhook-Secret", "wrong-secret")
                .body("""
                        {
                          "gatewayTransactionId": "fake",
                          "status": "PAGO"
                        }
                        """)
                .when()
                .post("/api/v1/payments/webhook")
                .then()
                .statusCode(401);
    }

    // ─── Épico 6 — Execução ───────────────────────────────────────────────

    @Test @Order(12)
    @DisplayName("12 · Prestador inicia o serviço → EM_ANDAMENTO (suspenso depois do aceite, não inicia)")
    void iniciarServico() {
        // Suspenso depois do aceite e do pagamento retido, não começa o serviço: o cliente cancela e
        // é reembolsado, em vez de ter o atendimento feito por quem o admin já barrou.
        moderarPrestador("SUSPENDER");
        given()
                .header("Authorization", "Bearer " + tokenPrestador)
                .when()
                .post("/api/v1/service-requests/{id}/start", requestId)
                .then()
                .statusCode(422)
                .body("code", equalTo("PROVIDER_NOT_VERIFIED"));
        moderarPrestador("APROVAR");

        given()
                .header("Authorization", "Bearer " + tokenPrestador)
                .when()
                .post("/api/v1/service-requests/{id}/start", requestId)
                .then()
                // endpoint /start retorna 200 sem corpo (ResponseEntity<Void>)
                .statusCode(200);
    }

    @Test @Order(13)
    @DisplayName("13 · Cliente confirma conclusão → CONCLUIDO")
    void confirmarConclusao() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .post("/api/v1/service-requests/{id}/confirm-completion", requestId)
                .then()
                // endpoint /confirm-completion retorna 200 sem corpo (ResponseEntity<Void>)
                .statusCode(200);
    }

    // ─── Épico 7 — Avaliação ──────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("14 · Cliente avalia o prestador — fica OCULTA até o prestador avaliar (double-blind)")
    void clienteAvaliaPresador() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .body("""
                        {
                          "nota":       5,
                          "comentario": "Excelente serviço! Rápido e limpo."
                        }
                        """)
                .when()
                .post("/api/v1/service-requests/{id}/review", requestId)
                .then()
                .statusCode(201)
                .body("nota", equalTo(5))
                // ninguém vê essa nota ainda — o prestador não avaliou
                .body("revelada", equalTo(false))
                .body("prazoRevelacao", notNullValue());

        // e enquanto oculta não pode aparecer no perfil público nem mexer na reputação
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .get("/api/v1/providers/{id}", prestadorId)
                .then()
                .statusCode(200)
                .body("totalAvaliacoes", equalTo(0))
                .body("avaliacoes", hasSize(0));
    }

    @Test @Order(15)
    @DisplayName("15 · Prestador avalia o cliente — revela as DUAS ao mesmo tempo")
    void prestadorAvaliaCliente() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenPrestador)
                .body("""
                        {
                          "nota":       5,
                          "comentario": "Cliente pontual e prestativo."
                        }
                        """)
                .when()
                .post("/api/v1/service-requests/{id}/review", requestId)
                .then()
                .statusCode(201)
                .body("nota", equalTo(5))
                .body("revelada", equalTo(true));

        // agora a avaliação do cliente (feita no passo 14) virou pública e conta na nota
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .get("/api/v1/providers/{id}", prestadorId)
                .then()
                .statusCode(200)
                .body("totalAvaliacoes", equalTo(1))
                .body("notaMedia", equalTo(5.0f));
    }

    @Test @Order(16)
    @DisplayName("16 · Segunda avaliação no mesmo pedido retorna 422")
    void avaliacaoDuplicadaRejeitada() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenCliente)
                .body("""
                        { "nota": 3, "comentario": "Tentativa duplicada" }
                        """)
                .when()
                .post("/api/v1/service-requests/{id}/review", requestId)
                .then()
                .statusCode(422);
    }

    // ─── Épico 8 — SOS ────────────────────────────────────────────────────

    @Test @Order(17)
    @DisplayName("17 · Acesso sem token retorna 401")
    void semTokenRetorna401() {
        given()
                .when()
                .post("/api/v1/sos")
                .then()
                .statusCode(401);
    }

    // ─── Geobusca ─────────────────────────────────────────────────────────

    @Test @Order(18)
    @DisplayName("18 · Geobusca de prestadores próximos retorna lista")
    void geobuscaPrestadores() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .queryParam("lat",      -3.7172)
                .queryParam("lng",     -38.5433)
                .queryParam("raioKm",   50)
                .queryParam("categoria", "eletrica")
                .when()
                .get("/api/v1/providers/nearby")
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }

    // ─── Épico 9 — Relatórios (US29) ──────────────────────────────────────

    @Test @Order(19)
    @DisplayName("19 · Relatórios CSV/PDF respeitam período e bairro — consultas reais no Postgres")
    void relatoriosRespeitamPeriodoEBairro() throws Exception {
        // Os testes de serviço mockam o repositório e não executam JPQL: o recorte por período
        // e o join transação→pedido (o bairro é do pedido) só rodam de verdade aqui.
        // Admin direto no banco — o profile e2e não roda o seed (já criado pelos passos de moderação).
        String tokenAdmin = tokenAdmin();

        // 2º pedido, em OUTRO bairro, com a própria transação. Com um pedido só, um join que
        // ligasse a transação ao pedido errado passaria despercebido.
        User cliente = userRepository.findByEmail("maria.e2e@onda.test").orElseThrow();
        ServiceRequest outro = new ServiceRequest();
        outro.setCliente(cliente);
        outro.setCategoria("pintura");
        outro.setStatus(ServiceRequestStatus.PENDENTE);
        outro.setBairro("Meireles");
        serviceRequestRepository.save(outro);
        transactionRepository.save(new Transaction(outro.getId(), new BigDecimal("100.00"),
                new BigDecimal("10.00"), new BigDecimal("0.10"), PaymentMethod.PIX, "idem-e2e-meireles"));
        String idAldeota  = requestId;                 // criado no passo 04, bairro Aldeota
        String idMeireles = outro.getId().toString();

        LocalDate hoje   = LocalDate.now(ZoneId.of("America/Fortaleza"));
        String ontem     = hoje.minusDays(1).toString();
        String amanha    = hoje.plusDays(1).toString();
        String cabecalhoTransacoes = "id,serviceRequestId,valorTotal,valorComissao,metodo,statusPagamento,criadoEm";

        // ── transactions.csv: bairro (join) ──
        String todas = csvAdmin(tokenAdmin, "transactions");
        assertThat(todas, containsString(idAldeota));
        assertThat(todas, containsString(idMeireles));

        String aldeota = csvAdmin(tokenAdmin, "transactions", "bairro", "Aldeota");
        assertThat(aldeota, containsString(idAldeota));
        assertThat("a transação do outro bairro não pode vazar", aldeota, not(containsString(idMeireles)));

        String meireles = csvAdmin(tokenAdmin, "transactions", "bairro", "Meireles");
        assertThat(meireles, containsString(idMeireles));
        assertThat(meireles, not(containsString(idAldeota)));

        assertThat("bairro sem pedidos devolve só o cabeçalho",
                csvAdmin(tokenAdmin, "transactions", "bairro", "Centro"), equalTo(cabecalhoTransacoes));

        // ── transactions.csv: período (de inclusivo, ate inclusivo → fim exclusivo no dia seguinte) ──
        String naJanela = csvAdmin(tokenAdmin, "transactions", "de", ontem, "ate", amanha);
        assertThat(naJanela, containsString(idAldeota));
        assertThat(naJanela, containsString(idMeireles));
        assertThat(csvAdmin(tokenAdmin, "transactions", "de", hoje.toString(), "ate", hoje.toString()),
                allOf(containsString(idAldeota), containsString(idMeireles)));   // o dia de "ate" entra inteiro
        assertThat("período no passado não traz nada",
                csvAdmin(tokenAdmin, "transactions", "de", "2000-01-01", "ate", "2000-01-02"),
                equalTo(cabecalhoTransacoes));
        assertThat("só 'de' no futuro não traz nada",
                csvAdmin(tokenAdmin, "transactions", "de", amanha), equalTo(cabecalhoTransacoes));
        assertThat("só 'ate' antes de hoje não traz nada",
                csvAdmin(tokenAdmin, "transactions", "ate", ontem), equalTo(cabecalhoTransacoes));

        // ── período e bairro juntos ──
        String meireleshoje = csvAdmin(tokenAdmin, "transactions", "de", ontem, "ate", amanha, "bairro", "Meireles");
        assertThat(meireleshoje, containsString(idMeireles));
        assertThat(meireleshoje, not(containsString(idAldeota)));
        assertThat(csvAdmin(tokenAdmin, "transactions", "de", "2000-01-01", "ate", "2000-01-02", "bairro", "Meireles"),
                equalTo(cabecalhoTransacoes));

        // ── requests.csv ──
        String pedidosAldeota = csvAdmin(tokenAdmin, "requests", "bairro", "Aldeota");
        assertThat(pedidosAldeota, containsString(idAldeota));
        assertThat(pedidosAldeota, not(containsString(idMeireles)));
        String pedidosNaJanela = csvAdmin(tokenAdmin, "requests", "de", ontem, "ate", amanha);
        assertThat(pedidosNaJanela, allOf(containsString(idAldeota), containsString(idMeireles)));
        assertThat(csvAdmin(tokenAdmin, "requests", "de", "2000-01-01", "ate", "2000-01-02"),
                equalTo("id,categoria,bairro,status,criadoEm"));

        // ── metrics.pdf: o recorte chega nas métricas e o arquivo diz qual foi ──
        String pdfAldeota = textoDoPdf(pdfAdmin(tokenAdmin, "de", ontem, "ate", amanha, "bairro", "Aldeota"));
        assertThat(pdfAldeota, containsString("Bairro: Aldeota"));
        assertThat("só o pedido de Aldeota conta", pdfAldeota, containsString("Total de pedidos: 1"));
        String pdfCentro = textoDoPdf(pdfAdmin(tokenAdmin, "bairro", "Centro"));
        assertThat(pdfCentro, containsString("Total de pedidos: 0"));
        String pdfPassado = textoDoPdf(pdfAdmin(tokenAdmin, "de", "2000-01-01", "ate", "2000-01-02"));
        assertThat(pdfPassado, containsString("Período: 01/01/2000 a 02/01/2000"));
        assertThat(pdfPassado, containsString("Total de pedidos: 0"));
        assertThat(textoDoPdf(pdfAdmin(tokenAdmin)), containsString("Período: todo o histórico"));

        // quem não é admin continua sem acesso
        given().header("Authorization", "Bearer " + tokenCliente)
                .when().get("/api/v1/admin/reports/transactions.csv")
                .then().statusCode(403);
    }

    private String csvAdmin(String token, String recurso, String... paramsNomeValor) {
        return comParametros(given().header("Authorization", "Bearer " + token), paramsNomeValor)
                .when().get("/api/v1/admin/reports/{recurso}.csv", recurso)
                .then().statusCode(200)
                .extract().asString().strip();
    }

    private byte[] pdfAdmin(String token, String... paramsNomeValor) {
        return comParametros(given().header("Authorization", "Bearer " + token), paramsNomeValor)
                .when().get("/api/v1/admin/reports/metrics.pdf")
                .then().statusCode(200).contentType("application/pdf")
                .extract().asByteArray();
    }

    private static RequestSpecification comParametros(RequestSpecification spec, String... nomeValor) {
        for (int i = 0; i < nomeValor.length; i += 2) {
            spec = spec.queryParam(nomeValor[i], nomeValor[i + 1]);
        }
        return spec;
    }

    /** Texto que o leitor de PDF enxerga (os bytes do arquivo vêm comprimidos). */
    private static String textoDoPdf(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    // ─── Épico 1 — Recuperação de senha (US35) ────────────────────────────
    //
    // Estes passos rodam contra o Postgres e o SecurityConfig reais, sem token nas rotas
    // (quem esqueceu a senha não tem sessão). O que os testes de serviço, com repositório
    // mockado, não conseguem provar: o contador de tentativas PERSISTE mesmo com a exceção
    // (noRollbackFor), as consultas com trava e os UPDATE em lote funcionam, e o e-mail só
    // sai depois do commit.

    private static final Pattern CODIGO_NO_EMAIL = Pattern.compile("\\b([0-9A-HJKMNP-TV-Z]{4})-([0-9A-HJKMNP-TV-Z]{4})\\b");
    static String codigoDoPrimeiroPedido;
    static String codigoVigente;
    static final String MARIA = "maria.e2e@onda.test";

    private static String extrairCodigo(String corpoDoEmail) {
        Matcher m = CODIGO_NO_EMAIL.matcher(corpoDoEmail);
        Assertions.assertTrue(m.find(), "o e-mail deveria conter o código no formato XXXX-XXXX");
        return m.group(1) + m.group(2);
    }

    /** O envio é assíncrono (depois do commit): espera o e-mail chegar na caixa de teste. */
    private static void aguardarEmails(int quantidade) throws InterruptedException {
        for (int i = 0; i < 60 && CaixaDeEntrada.EMAILS.size() < quantidade; i++) Thread.sleep(100);
        assertThat("e-mails recebidos", CaixaDeEntrada.EMAILS.size(), greaterThanOrEqualTo(quantidade));
    }

    private String pedirCodigo(String email) {
        return given().contentType(ContentType.JSON).body("{\"email\":\"" + email + "\"}")
                .when().post("/api/v1/auth/forgot-password")
                .then().statusCode(202).extract().asString();
    }

    private io.restassured.response.ValidatableResponse redefinir(String email, String codigo, String novaSenha) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"%s\",\"codigo\":\"%s\",\"novaSenha\":\"%s\"}".formatted(email, codigo, novaSenha))
                .when().post("/api/v1/auth/reset-password").then();
    }

    private io.restassured.response.Response login(String email, String senha) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .when().post("/api/v1/auth/login");
    }

    /** Admin direto no banco (o profile e2e não roda o seed), criado na 1ª chamada e reaproveitado. */
    private String tokenAdmin() {
        if (userRepository.findByEmail("admin.e2e@onda.test").isEmpty()) {
            userRepository.save(User.builder().nome("Admin E2E").email("admin.e2e@onda.test")
                    .senhaHash(passwordEncoder.encode("Admin@123")).role(UserRole.ROLE_ADMIN).build());
        }
        return login("admin.e2e@onda.test", "Admin@123").then().statusCode(200).extract().path("accessToken");
    }

    /** Moderação real do prestador do fluxo pela API do admin: APROVAR, REPROVAR ou SUSPENDER. */
    private void moderarPrestador(String acao) {
        moderarPrestador(prestadorId, acao);
    }

    private void moderarPrestador(String userId, String acao) {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenAdmin())
                .body("{\"action\":\"%s\"}".formatted(acao))
                .when().post("/api/v1/admin/providers/{id}/moderate", userId)
                .then().statusCode(200);
    }

    @Test @Order(20)
    @DisplayName("20 · Recuperação de senha: resposta idêntica p/ e-mail cadastrado e desconhecido; o código só chega por e-mail e não fica em claro")
    void recuperacaoDeSenha_pedidoDeCodigo() throws Exception {
        CaixaDeEntrada.EMAILS.clear();

        String desconhecido = pedirCodigo("ninguem@onda.test");
        String cadastrado   = pedirCodigo(MARIA);
        assertThat("a resposta não pode revelar se o e-mail existe", cadastrado, equalTo(desconhecido));

        aguardarEmails(1);
        Thread.sleep(500);   // dá tempo de um e-mail indevido (p/ o desconhecido) aparecer
        assertThat("só a conta que existe recebe e-mail", CaixaDeEntrada.EMAILS, hasSize(1));
        var email = CaixaDeEntrada.EMAILS.get(0);
        assertThat(email.para(), equalTo(MARIA));
        codigoDoPrimeiroPedido = extrairCodigo(email.corpo());
        codigoVigente = codigoDoPrimeiroPedido;

        // no banco só o HMAC: 64 hex, sem o código
        String mariaId = userRepository.findByEmail(MARIA).orElseThrow().getId().toString();
        String hashGravado = jdbc.queryForObject(
                "SELECT code_hash FROM password_reset_codes WHERE user_id = ?::uuid", String.class, mariaId);
        assertThat(hashGravado, allOf(hasLength(64), not(containsString(codigoVigente))));
    }

    @Test @Order(21)
    @DisplayName("21 · Cinco erros invalidam o código (as tentativas persistem mesmo com a exceção) e o certo deixa de servir")
    void recuperacaoDeSenha_cincoErrosInvalidamOCodigo() {
        for (int i = 0; i < 5; i++) {
            redefinir(MARIA, "ZZZZ-999" + i, "NovaSenha@1")
                    .statusCode(422)
                    .body("code", equalTo("INVALID_RESET_CODE"))
                    .body("message", equalTo("Código inválido ou expirado."));
        }

        // As 5 tentativas FORAM gravadas apesar de cada requisição ter terminado em exceção:
        // sem noRollbackFor a transação voltava inteira e o contador ficava em 0 para sempre.
        String mariaId = userRepository.findByEmail(MARIA).orElseThrow().getId().toString();
        var linha = jdbc.queryForMap(
                "SELECT attempts, closed_at IS NOT NULL AS fechado FROM password_reset_codes WHERE user_id = ?::uuid", mariaId);
        assertThat(linha.get("attempts"), equalTo(5));
        assertThat(linha.get("fechado"), equalTo(true));

        // e agora nem o código CERTO serve: é preciso pedir outro
        redefinir(MARIA, codigoDoPrimeiroPedido, "NovaSenha@1")
                .statusCode(422).body("code", equalTo("INVALID_RESET_CODE"));
    }

    @Test @Order(22)
    @DisplayName("22 · No máximo 3 códigos por hora (o 4º pedido responde igual, mas não envia) e só o último vale")
    void recuperacaoDeSenha_limitePorHoraESoOUltimoVale() throws Exception {
        CaixaDeEntrada.EMAILS.clear();

        // o passo 20 já usou 1 dos 3: os pedidos 2 e 3 enviam; o 4º e o 5º respondem igual e não enviam
        String r2 = pedirCodigo(MARIA);
        String r3 = pedirCodigo(MARIA);
        String r4 = pedirCodigo(MARIA);
        String r5 = pedirCodigo(MARIA);
        assertThat(r4, allOf(equalTo(r2), equalTo(r3), equalTo(r5)));

        aguardarEmails(2);
        Thread.sleep(500);
        assertThat("o limite por hora segurou o 4º e o 5º", CaixaDeEntrada.EMAILS, hasSize(2));
        String codigoDoPedido2 = extrairCodigo(CaixaDeEntrada.EMAILS.get(0).corpo());
        codigoVigente = extrairCodigo(CaixaDeEntrada.EMAILS.get(1).corpo());

        // um pedido novo invalida o anterior
        redefinir(MARIA, codigoDoPedido2, "NovaSenha@1")
                .statusCode(422).body("code", equalTo("INVALID_RESET_CODE"));
    }

    @Test @Order(23)
    @DisplayName("23 · Código certo troca a senha, encerra TODAS as sessões, avisa por e-mail e é de uso único")
    void recuperacaoDeSenha_trocaASenhaEEncerraSessoes() throws Exception {
        // sessão aberta ANTES da troca
        String refreshAntigo = login(MARIA, "Senha@123").then().statusCode(200).extract().path("refreshToken");
        CaixaDeEntrada.EMAILS.clear();

        redefinir(MARIA, codigoVigente, "NovaSenha@1").statusCode(204);

        login(MARIA, "Senha@123").then().statusCode(422).body("code", equalTo("INVALID_CREDENTIALS"));
        login(MARIA, "NovaSenha@1").then().statusCode(200).body("accessToken", notNullValue());

        // a sessão que estava aberta com a senha antiga foi encerrada
        given().contentType(ContentType.JSON).body("{\"refreshToken\":\"" + refreshAntigo + "\"}")
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(422).body("code", equalTo("INVALID_REFRESH_TOKEN"));

        // aviso de senha alterada
        aguardarEmails(1);
        assertThat(CaixaDeEntrada.EMAILS.get(0).assunto(), containsString("senha foi alterada"));
        assertThat("o aviso não leva código nenhum", CaixaDeEntrada.EMAILS.get(0).corpo(),
                not(matchesPattern("(?s).*\\b[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}\\b.*")));

        // uso único: o mesmo código não troca a senha de novo
        redefinir(MARIA, codigoVigente, "OutraSenha@1").statusCode(422).body("code", equalTo("INVALID_RESET_CODE"));
        login(MARIA, "NovaSenha@1").then().statusCode(200);
    }

    @Test @Order(24)
    @DisplayName("24 · Conta suspensa não entra, não renova a sessão e não recebe código; ao reativar, volta (US26)")
    void contaSuspensa_naoEntraNaoRenovaNaoRecebeCodigo() throws Exception {
        String tokenAdmin = login("admin.e2e@onda.test", "Admin@123").then().statusCode(200).extract().path("accessToken");
        String joao = "joao.e2e@onda.test";
        String refreshAberto = login(joao, "Senha@123").then().statusCode(200).extract().path("refreshToken");

        // o access token emitido no cadastro (ainda dentro dos 15 min) funciona...
        given().header("Authorization", "Bearer " + tokenPrestador)
                .when().get("/api/v1/providers/me/chave-pix").then().statusCode(200);

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().post("/api/v1/admin/users/{id}/suspend", prestadorId).then().statusCode(200);

        // ...e para NA HORA com a suspensão: o filtro confere a conta a cada requisição. Antes, o token
        // já emitido seguia valendo até expirar — conta suspensa continuava usando o app por até 15 min.
        given().header("Authorization", "Bearer " + tokenPrestador)
                .when().get("/api/v1/providers/me/chave-pix").then().statusCode(401);

        // com a senha CERTA a conta suspensa não entra; com a errada nem fica sabendo que está suspensa
        login(joao, "Senha@123").then().statusCode(422).body("code", equalTo("ACCOUNT_SUSPENDED"));
        login(joao, "errada").then().statusCode(422).body("code", equalTo("INVALID_CREDENTIALS"));
        // a sessão que já estava aberta não se renova
        given().contentType(ContentType.JSON).body("{\"refreshToken\":\"" + refreshAberto + "\"}")
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(422).body("code", equalTo("INVALID_REFRESH_TOKEN"));
        // e não recebe código de recuperação (resposta igual à de qualquer e-mail)
        CaixaDeEntrada.EMAILS.clear();
        assertThat(pedirCodigo(joao), equalTo(pedirCodigo("ninguem@onda.test")));
        Thread.sleep(700);
        assertThat(CaixaDeEntrada.EMAILS, empty());

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().post("/api/v1/admin/users/{id}/reactivate", prestadorId).then().statusCode(200);
        login(joao, "Senha@123").then().statusCode(200);
        // reativada, o MESMO token volta a valer sem novo login: o corte é pelo estado da conta, não por revogação
        given().header("Authorization", "Bearer " + tokenPrestador)
                .when().get("/api/v1/providers/me/chave-pix").then().statusCode(200);
    }

    @Test @Order(25)
    @DisplayName("25 · 30 palpites SIMULTÂNEOS contam exatamente 5 tentativas e fecham o código")
    void recuperacaoDeSenha_palpitesSimultaneosNaoFuramOLimite() throws Exception {
        // Teste de fumaça do comportamento visto de fora. ATENÇÃO: sozinho ele NÃO prova a trava de
        // escrita — as requisições chegam espaçadas demais para se sobreporem de verdade (removi a
        // trava de propósito e este passo seguiu verde). Quem prova a trava é o passo 26.
        String joao = "joao.e2e@onda.test";
        CaixaDeEntrada.EMAILS.clear();
        pedirCodigo(joao);
        aguardarEmails(1);
        String codigoCerto = extrairCodigo(CaixaDeEntrada.EMAILS.get(0).corpo());

        int palpites = 30;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(palpites);
        var largada = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> respostas = new java.util.ArrayList<>();
        for (int i = 0; i < palpites; i++) {
            String palpite = "ZZZZ-%04d".formatted(i);
            respostas.add(pool.submit(() -> {
                largada.await();
                return redefinir(joao, palpite, "NovaSenha@1").extract().statusCode();
            }));
        }
        largada.countDown();
        for (var r : respostas) {
            assertThat(r.get(60, java.util.concurrent.TimeUnit.SECONDS), equalTo(422));
        }
        pool.shutdown();

        String joaoId = userRepository.findByEmail(joao).orElseThrow().getId().toString();
        var linha = jdbc.queryForMap("""
                SELECT attempts, closed_at IS NOT NULL AS fechado FROM password_reset_codes
                 WHERE user_id = ?::uuid ORDER BY created_at DESC LIMIT 1
                """, joaoId);
        assertThat("30 palpites paralelos contam exatamente 5 antes de fechar o código",
                linha.get("attempts"), equalTo(5));
        assertThat(linha.get("fechado"), equalTo(true));

        redefinir(joao, codigoCerto, "NovaSenha@1").statusCode(422);   // fechado: nem o certo serve
        login(joao, "Senha@123").then().statusCode(200);               // e a senha NÃO foi trocada
    }

    @Test @Order(26)
    @DisplayName("26 · A consulta do código TRAVA a linha: uma 2ª transação espera a 1ª terminar")
    void recuperacaoDeSenha_aConsultaDoCodigoTravaALinha() throws Exception {
        // Prova determinística da trava de escrita (@Lock PESSIMISTIC_WRITE). Sem ela, palpites
        // simultâneos leem o MESMO contador de tentativas e o limite de 5 deixa de valer. Uma
        // transação segura a linha; a segunda, na mesma consulta, TEM que ficar esperando.
        String joao = "joao.e2e@onda.test";
        pedirCodigo(joao);                                           // garante um código aberto
        UUID joaoId = userRepository.findByEmail(joao).orElseThrow().getId();

        var travaObtida = new java.util.concurrent.CountDownLatch(1);
        var liberar     = new java.util.concurrent.CountDownLatch(1);
        var segundaTerminou = new java.util.concurrent.atomic.AtomicBoolean(false);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);

        var primeira = pool.submit(() -> transacao.executeWithoutResult(s -> {
            passwordResetCodeRepository.ativosDoUsuarioComTrava(joaoId, Instant.now());   // pega a trava
            travaObtida.countDown();
            try { liberar.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        Assertions.assertTrue(travaObtida.await(10, java.util.concurrent.TimeUnit.SECONDS), "a 1ª transação deveria ter a trava");

        var segunda = pool.submit(() -> {
            transacao.executeWithoutResult(s ->
                    passwordResetCodeRepository.ativosDoUsuarioComTrava(joaoId, Instant.now()));
            segundaTerminou.set(true);
        });
        Thread.sleep(800);
        assertThat("a 2ª transação deveria estar ESPERANDO a trava, não ter terminado",
                segundaTerminou.get(), is(false));

        liberar.countDown();                                         // a 1ª termina e solta a linha
        primeira.get(10, java.util.concurrent.TimeUnit.SECONDS);
        segunda.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(segundaTerminou.get(), is(true));
        pool.shutdown();
    }

    // ─── US36 — Exclusão de conta ──────────────────────────────────────────
    //
    // Estes passos rodam contra o Postgres e o SecurityConfig reais. É aqui que se prova o que o teste de
    // serviço, com repositório mockado, não alcança: que cada UPDATE/DELETE em lote atinge a tabela certa
    // e só ela, que a anonimização passa pelas restrições (FK, NOT NULL, UNIQUE) e que o histórico fica de
    // pé. Cada cenário usa contas NOVAS e monta o estado por API/repositório/SQL — não depende dos passos 1–26.

    static final String SENHA_PADRAO = "Senha@123";
    static final String CPF_PAULO = "111.444.777-35";
    static final String CPF_RUI   = "390.533.447-05";
    static final String CPF_PAULA = "168.995.350-09";
    static final String CPF_QUIM  = "935.411.347-80";
    static final String CPF_MARTA = "529.982.247-25";
    static final String PIX_PAULO = "paulo.exclusao@pix.com";
    static final String PIX_PAULA = "paula.exclusao@pix.com";

    static UUID martaId;
    static UUID paulaId;

    record Conta(UUID id, String email, String token, String refresh) {}

    private Conta cadastrarCliente(String nome, String email) {
        var r = given().contentType(ContentType.JSON)
                .body("{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\",\"aceitouTermos\":true}"
                        .formatted(nome, email, SENHA_PADRAO))
                .when().post("/api/v1/auth/register/client").then().statusCode(201).extract();
        return new Conta(UUID.fromString(r.path("userId")), email, r.path("accessToken"), r.path("refreshToken"));
    }

    private Conta cadastrarPrestador(String nome, String email, String cpf) {
        var r = given().contentType(ContentType.JSON)
                .body("""
                        {"nome":"%s","email":"%s","senha":"%s","cpf":"%s","categoria":"eletrica",
                         "bio":"Eletricista, moro na Rua das Palmeiras 45","aceitouTermos":true}
                        """.formatted(nome, email, SENHA_PADRAO, cpf))
                .when().post("/api/v1/auth/register/provider").then().statusCode(201).extract();
        return new Conta(UUID.fromString(r.path("userId")), email, r.path("accessToken"), r.path("refreshToken"));
    }

    private void definirChavePix(Conta prestador, String chave) {
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + prestador.token())
                .body("{\"chavePix\":\"%s\"}".formatted(chave))
                .when().put("/api/v1/providers/me/chave-pix").then().statusCode(200);
    }

    /** Pedido criado pela API como o app o envia (descrição, localização, bairro); o status é ajustado no banco. */
    private UUID pedidoDe(Conta cliente, String descricao, String status) {
        UUID id = UUID.fromString(given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + cliente.token())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .body("""
                        {"categoria":"eletrica","descricao":"%s","lat":-3.7319,"lng":-38.5267,"bairro":"Meireles"}
                        """.formatted(descricao))
                .when().post("/api/v1/service-requests").then().statusCode(201).extract().path("id"));
        definirStatus(id, status);
        return id;
    }

    private void definirStatus(UUID pedido, String status) {
        jdbc.update("UPDATE service_requests SET status = ? WHERE id = ?::uuid", status, pedido.toString());
    }

    private void proposta(UUID pedido, UUID prestador, ProposalStatus status) {
        proposalRepository.save(new Proposal(serviceRequestRepository.findById(pedido).orElseThrow(),
                prestador, new BigDecimal("200.00"), 1, null, status));
    }

    private void pagamento(UUID pedido, TransactionStatus status) {
        var tx = new Transaction(pedido, new BigDecimal("200.00"), new BigDecimal("30.00"),
                new BigDecimal("15.00"), PaymentMethod.PIX, UUID.randomUUID().toString());
        if (status != TransactionStatus.PENDENTE) tx.reter();
        if (status == TransactionStatus.LIBERADO) tx.liberar();
        if (status == TransactionStatus.REEMBOLSADO) tx.reembolsar();
        transactionRepository.save(tx);
    }

    private void definirPagamento(UUID pedido, TransactionStatus status) {
        jdbc.update("UPDATE transactions SET status_pagamento = ? WHERE service_request_id = ?::uuid",
                status.name(), pedido.toString());
    }

    private void midia(UUID pedido) {
        mediaRepository.save(new ServiceMedia(serviceRequestRepository.findById(pedido).orElseThrow(),
                MediaType.FOTO, "https://storage.invalid/foto-" + pedido + ".jpg"));
    }

    private void mensagem(UUID pedido, UUID remetente, String texto) {
        messageRepository.save(new Message(serviceRequestRepository.findById(pedido).orElseThrow(),
                remetente, texto, false));
    }

    private void avaliacao(UUID pedido, UUID avaliador, UUID avaliado, ReviewType tipo, int nota, String comentario) {
        var r = new Review(pedido, avaliador, avaliado, tipo, nota, comentario);
        r.revelar();
        reviewRepository.save(r);
    }

    private io.restassured.response.Response excluirConta(String token, String senha) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token)
                .body("{\"senha\":\"%s\"}".formatted(senha))
                .when().post("/api/v1/users/me/delete");
    }

    private void verificarIdentidade(Conta conta, String cpf) {
        verificarIdentidade(conta.token(), cpf).then().statusCode(204);
    }

    private io.restassured.response.Response verificarIdentidade(String token, String cpf) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token)
                .body("{\"cpf\":\"%s\"}".formatted(cpf))
                .when().post("/api/v1/auth/verify-identity");
    }

    private java.util.Map<String, Object> conta(UUID id) {
        return jdbc.queryForMap(
                "SELECT nome, email, cpf_hash, senha_hash, ativo, excluido_em FROM users WHERE id = ?::uuid", id.toString());
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String texto(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    /** A coluna está NULL na linha com este id? (só nomes fixos do próprio teste entram no SQL) */
    private boolean vazio(String coluna, String tabela, UUID id) {
        return jdbc.queryForObject("SELECT %s IS NULL FROM %s WHERE id = ?::uuid".formatted(coluna, tabela),
                Boolean.class, id.toString());
    }

    @Test @Order(27)
    @DisplayName("27 · Exclusão de conta: senha errada, administrador, corpo vazio e sem token são recusados e nada muda")
    void exclusaoDeConta_recusasSimples() {
        var ana = cadastrarCliente("Ana Cuidadosa", "ana.exclusao@onda.test");
        int sessoes = contar("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", ana.id().toString());

        excluirConta(ana.token(), "senha-errada")
                .then().statusCode(422).body("code", equalTo("INVALID_PASSWORD"));

        // nada mudou: a conta segue inteira, a sessão aberta também e nenhuma sessão foi derrubada
        var linha = conta(ana.id());
        assertThat(linha.get("nome"), equalTo("Ana Cuidadosa"));
        assertThat(linha.get("excluido_em"), nullValue());
        assertThat(contar("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", ana.id().toString()),
                equalTo(sessoes));
        login(ana.email(), SENHA_PADRAO).then().statusCode(200);
        given().header("Authorization", "Bearer " + ana.token())
                .when().get("/api/v1/service-requests/my").then().statusCode(200);

        excluirConta(tokenAdmin(), "Admin@123")
                .then().statusCode(422).body("code", equalTo("ADMIN_CANNOT_DELETE"));
        login("admin.e2e@onda.test", "Admin@123").then().statusCode(200);

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + ana.token()).body("{}")
                .when().post("/api/v1/users/me/delete").then().statusCode(422);
        given().contentType(ContentType.JSON).body("{\"senha\":\"x\"}")
                .when().post("/api/v1/users/me/delete").then().statusCode(401);
        assertThat(conta(ana.id()).get("excluido_em"), nullValue());
    }

    @Test @Order(28)
    @DisplayName("28 · Pendências impedem: pedido em curso, reembolso pendente (cliente), serviço em curso, repasse a receber (prestador)")
    void exclusaoDeConta_pendenciasImpedem() {
        // --- A) pedido ACEITO / EM_ANDAMENTO / EM_DISPUTA: cliente e prestador ficam, e nada é apagado
        var caio  = cadastrarCliente("Caio Com Pedido", "caio.exclusao@onda.test");
        var paulo = cadastrarPrestador("Paulo Prestador", "paulo.exclusao@onda.test", CPF_PAULO);
        definirChavePix(paulo, PIX_PAULO);
        UUID emCurso = pedidoDe(caio, "Pedido em curso, Rua A 10", "ACEITO");
        proposta(emCurso, paulo.id(), ProposalStatus.ACEITA);
        int sessoesCaio = contar("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", caio.id().toString());

        for (String status : List.of("ACEITO", "EM_ANDAMENTO", "EM_DISPUTA")) {
            definirStatus(emCurso, status);
            for (Conta quem : List.of(caio, paulo)) {
                excluirConta(quem.token(), SENHA_PADRAO).then().statusCode(422)
                        .body("code", equalTo("ACCOUNT_HAS_ACTIVE_ORDERS"))
                        .body("message", containsString("aceitos, em andamento ou em disputa"));
            }
        }
        assertThat(conta(caio.id()).get("excluido_em"), nullValue());
        assertThat(conta(paulo.id()).get("excluido_em"), nullValue());
        assertThat(texto("SELECT descricao FROM service_requests WHERE id = ?::uuid", emCurso.toString()),
                equalTo("Pedido em curso, Rua A 10"));
        assertThat(contar("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", caio.id().toString()),
                equalTo(sessoesCaio));

        // --- B) serviço CONCLUÍDO com o dinheiro ainda retido (repasse manual pendente): o PRESTADOR espera
        //        receber e fica; o CLIENTE já cumpriu a parte dele e pode sair
        definirStatus(emCurso, "CONCLUIDO");
        pagamento(emCurso, TransactionStatus.RETIDO);
        excluirConta(paulo.token(), SENHA_PADRAO).then().statusCode(422)
                .body("code", equalTo("ACCOUNT_HAS_ACTIVE_ORDERS"))
                .body("message", containsString("repasse"));
        assertThat("a chave Pix de quem tem repasse a receber NÃO pode ser apagada",
                texto("SELECT chave_pix_cifrada FROM providers_profile WHERE user_id = ?::uuid", paulo.id().toString()),
                notNullValue());

        excluirConta(caio.token(), SENHA_PADRAO).then().statusCode(204);
        assertThat("a transação do pedido concluído fica intacta",
                texto("SELECT status_pagamento FROM transactions WHERE service_request_id = ?::uuid", emCurso.toString()),
                equalTo("RETIDO"));

        definirPagamento(emCurso, TransactionStatus.LIBERADO);          // o admin fez o repasse
        excluirConta(paulo.token(), SENHA_PADRAO).then().statusCode(204);

        // --- C) pedido CANCELADO com o dinheiro ainda retido (reembolso a caminho): o CLIENTE espera;
        //        o prestador não tem nada a receber nem trabalho em curso, então sai
        var rita = cadastrarCliente("Rita Reembolso", "rita.exclusao@onda.test");
        var rui  = cadastrarPrestador("Rui Prestador", "rui.exclusao@onda.test", CPF_RUI);
        UUID cancelado = pedidoDe(rita, "Pedido cancelado, Rua B 20", "CANCELADO");
        proposta(cancelado, rui.id(), ProposalStatus.ACEITA);
        pagamento(cancelado, TransactionStatus.RETIDO);

        excluirConta(rita.token(), SENHA_PADRAO).then().statusCode(422)
                .body("code", equalTo("ACCOUNT_HAS_ACTIVE_ORDERS"))
                .body("message", containsString("reembolso"));
        assertThat(conta(rita.id()).get("excluido_em"), nullValue());

        excluirConta(rui.token(), SENHA_PADRAO).then().statusCode(204);
        definirPagamento(cancelado, TransactionStatus.REEMBOLSADO);     // o reembolso saiu
        excluirConta(rita.token(), SENHA_PADRAO).then().statusCode(204);
    }

    @Test @Order(29)
    @DisplayName("29 · Cliente exclui: cada tabela no estado certo — dado pessoal fora, histórico de pé, acesso cortado na hora")
    void exclusaoDeConta_clienteTabelaPorTabela() throws Exception {
        var marta = cadastrarCliente("Marta Removível", "marta.exclusao@onda.test");
        var pedro = cadastrarPrestador("Pedro Parceiro", "pedro.exclusao@onda.test", "745.649.490-00");
        martaId = marta.id();
        verificarIdentidade(marta, CPF_MARTA);   // vínculo do CPF (só o hash) — a "conta limpa" o perde

        UUID aberto    = pedidoDe(marta, "Vazamento na cozinha, Rua das Flores 123", "PENDENTE");
        UUID concluido = pedidoDe(marta, "Troca de chuveiro, Rua das Flores 123", "CONCLUIDO");
        jdbc.update("""
                UPDATE service_requests SET ai_descricao_sugerida = 'texto da IA',
                       motivo_disputa = 'motivo da disputa', detalhes_disputa = 'detalhe da disputa'
                 WHERE id IN (?::uuid, ?::uuid)
                """, aberto.toString(), concluido.toString());
        // bairro de antes da validação de entrada existir (ou de um caminho que não passasse por ela):
        // texto livre direto no banco, como a API nunca deixaria entrar hoje
        UUID comBairroLegado = pedidoDe(marta, "Pedido com bairro legado, fora da lista", "PENDENTE");
        jdbc.update("UPDATE service_requests SET bairro = 'Rua das Flores, 123' WHERE id = ?::uuid",
                comBairroLegado.toString());
        proposta(aberto, pedro.id(), ProposalStatus.ATIVA);
        proposta(concluido, pedro.id(), ProposalStatus.ACEITA);
        pagamento(concluido, TransactionStatus.LIBERADO);
        midia(aberto);
        midia(concluido);
        mensagem(aberto, marta.id(), "me liga no 85 99999-0000");
        mensagem(aberto, pedro.id(), "Posso ir amanhã");
        avaliacao(concluido, marta.id(), pedro.id(), ReviewType.CLIENTE_AVALIA_PRESTADOR, 5, "Ótimo, mora na Rua das Flores");
        avaliacao(concluido, pedro.id(), marta.id(), ReviewType.PRESTADOR_AVALIA_CLIENTE, 4, "Cliente educada");
        sosAlertRepository.save(new SosAlert(marta.id(), concluido, new BigDecimal("-3.7319000"), new BigDecimal("-38.5267000")));
        denunciaRepository.save(new Denuncia(TipoDenuncia.PRESTADOR, pedro.id(), marta.id(), "FRAUDE", "Cobrou fora do combinado"));
        login(marta.email(), SENHA_PADRAO).then().statusCode(200);             // 2ª sessão aberta
        CaixaDeEntrada.EMAILS.clear();
        pedirCodigo(marta.email());                                            // um código de recuperação em aberto
        aguardarEmails(1);
        CaixaDeEntrada.EMAILS.clear();

        String senhaAntes = (String) conta(marta.id()).get("senha_hash");
        assertThat(contar("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", marta.id().toString()), greaterThanOrEqualTo(2));
        assertThat(contar("SELECT count(*) FROM password_reset_codes WHERE user_id = ?::uuid", marta.id().toString()), equalTo(1));

        excluirConta(marta.token(), SENHA_PADRAO).then().statusCode(204).body(emptyString());

        // users: nada que identifique, conta bloqueada e fora do login
        var u = conta(marta.id());
        assertThat(u.get("nome"), equalTo("Usuário removido"));
        assertThat((String) u.get("email"), matchesPattern("removido-[0-9a-f-]{36}@excluido\\.invalid"));
        assertThat("aleatório, não derivado do id (público): ninguém cadastra o endereço antes e trava a exclusão",
                (String) u.get("email"), not(containsString(marta.id().toString())));
        assertThat("conta limpa: o hash do CPF também sai", u.get("cpf_hash"), nullValue());
        assertThat(u.get("ativo"), equalTo(false));
        assertThat(u.get("excluido_em"), notNullValue());
        assertThat("a senha antiga deixou de existir", u.get("senha_hash"), not(equalTo(senhaAntes)));

        // service_requests: o sem compromisso foi cancelado; texto livre e localização saíram dos DOIS;
        // categoria, bairro e status do histórico ficaram
        assertThat(texto("SELECT status FROM service_requests WHERE id = ?::uuid", aberto.toString()), equalTo("CANCELADO"));
        assertThat(texto("SELECT status FROM service_requests WHERE id = ?::uuid", concluido.toString()), equalTo("CONCLUIDO"));
        for (UUID pedido : List.of(aberto, concluido)) {
            assertThat(vazio("descricao", "service_requests", pedido), is(true));
            assertThat(vazio("ai_descricao_sugerida", "service_requests", pedido), is(true));
            assertThat("achado da revisão cruzada: motivo_disputa ficava, só detalhes_disputa saía",
                    vazio("motivo_disputa", "service_requests", pedido), is(true));
            assertThat(vazio("detalhes_disputa", "service_requests", pedido), is(true));
            assertThat(vazio("localizacao", "service_requests", pedido), is(true));
            assertThat(texto("SELECT bairro FROM service_requests WHERE id = ?::uuid", pedido.toString()), equalTo("Meireles"));
            assertThat(texto("SELECT categoria FROM service_requests WHERE id = ?::uuid", pedido.toString()), equalTo("eletrica"));
        }
        // achado da revisão cruzada: bairro fora da lista oficial (dado de antes da validação existir) é
        // saneado na exclusão, não só bloqueado em pedido novo
        assertThat("bairro fora da lista oficial sai na exclusão",
                vazio("bairro", "service_requests", comBairroLegado), is(true));

        // proposals: a aberta foi encerrada; a aceita do pedido concluído é histórico e fica
        assertThat(texto("SELECT status FROM proposals WHERE service_request_id = ?::uuid", aberto.toString()), equalTo("ENCERRADA"));
        assertThat(texto("SELECT status FROM proposals WHERE service_request_id = ?::uuid", concluido.toString()), equalTo("ACEITA"));

        // mídia dos pedidos: toda removida
        assertThat(contar("SELECT count(*) FROM service_media WHERE service_request_id IN (?::uuid, ?::uuid)",
                aberto.toString(), concluido.toString()), equalTo(0));

        // mensagens: o texto dela saiu e a linha ficou; a do prestador não foi tocada
        assertThat(texto("SELECT conteudo FROM messages WHERE service_request_id = ?::uuid AND remetente_id = ?::uuid",
                aberto.toString(), marta.id().toString()), equalTo("[mensagem removida]"));
        assertThat(texto("SELECT conteudo FROM messages WHERE service_request_id = ?::uuid AND remetente_id = ?::uuid",
                aberto.toString(), pedro.id().toString()), equalTo("Posso ir amanhã"));

        // reviews: o comentário DELA saiu e a nota ficou (é reputação do avaliado); a avaliação feita sobre ela não foi tocada
        assertThat(texto("SELECT comentario FROM reviews WHERE service_request_id = ?::uuid AND avaliador_id = ?::uuid",
                concluido.toString(), marta.id().toString()), nullValue());
        assertThat(contar("SELECT nota FROM reviews WHERE service_request_id = ?::uuid AND avaliador_id = ?::uuid",
                concluido.toString(), marta.id().toString()), equalTo(5));
        assertThat(texto("SELECT comentario FROM reviews WHERE service_request_id = ?::uuid AND avaliador_id = ?::uuid",
                concluido.toString(), pedro.id().toString()), equalTo("Cliente educada"));

        // transactions: histórico financeiro intacto
        var tx = jdbc.queryForMap("SELECT status_pagamento, valor_total FROM transactions WHERE service_request_id = ?::uuid",
                concluido.toString());
        assertThat(tx.get("status_pagamento"), equalTo("LIBERADO"));
        assertThat((BigDecimal) tx.get("valor_total"), comparesEqualTo(new BigDecimal("200.00")));

        // sessões e códigos: todos apagados
        assertThat(contar("SELECT count(*) FROM refresh_tokens WHERE user_id = ?::uuid", marta.id().toString()), equalTo(0));
        assertThat(contar("SELECT count(*) FROM password_reset_codes WHERE user_id = ?::uuid", marta.id().toString()), equalTo(0));

        // o que se mantém por premissa jurídica (a confirmar — docs/PENDENCIAS_JURIDICAS.md): aceite de termos,
        // SOS e denúncia
        assertThat(contar("SELECT count(*) FROM terms_acceptance WHERE user_id = ?::uuid", marta.id().toString()), greaterThanOrEqualTo(1));
        assertThat(contar("SELECT count(*) FROM sos_alerts WHERE user_id = ?::uuid", marta.id().toString()), equalTo(1));
        assertThat(contar("SELECT count(*) FROM denuncias WHERE denunciante_id = ?::uuid", marta.id().toString()), equalTo(1));

        // acesso cortado: o access token que ela tinha na mão (ainda dentro dos 15 min) já não vale...
        given().header("Authorization", "Bearer " + marta.token())
                .when().get("/api/v1/service-requests/my").then().statusCode(401);
        // ...nem o refresh, nem o login, e repetir a exclusão com o token velho também dá 401
        given().contentType(ContentType.JSON).body("{\"refreshToken\":\"" + marta.refresh() + "\"}")
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(422).body("code", equalTo("INVALID_REFRESH_TOKEN"));
        login(marta.email(), SENHA_PADRAO).then().statusCode(422).body("code", equalTo("INVALID_CREDENTIALS"));
        excluirConta(marta.token(), SENHA_PADRAO).then().statusCode(401);

        // aviso por e-mail, para o endereço de ANTES — e só esse
        aguardarEmails(1);
        Thread.sleep(500);
        assertThat(CaixaDeEntrada.EMAILS, hasSize(1));
        assertThat(CaixaDeEntrada.EMAILS.get(0).para(), equalTo(marta.email()));
        assertThat(CaixaDeEntrada.EMAILS.get(0).assunto(), containsString("excluída"));

        // conta "limpa" volta: o e-mail e o CPF estão livres de novo
        var volta = cadastrarCliente("Marta Voltou", marta.email());
        verificarIdentidade(volta, CPF_MARTA);
    }

    @Test @Order(30)
    @DisplayName("30 · Prestador exclui: perfil, chave Pix, localização e propostas abertas; sai da busca; não leva dado do cliente junto")
    void exclusaoDeConta_prestadorTabelaPorTabela() {
        var paula = cadastrarPrestador("Paula Prestadora", "paula.exclusao@onda.test", CPF_PAULA);
        paulaId = paula.id();
        moderarPrestador(paula.id().toString(), "APROVAR");
        definirChavePix(paula, PIX_PAULA);
        jdbc.update("""
                UPDATE providers_profile
                   SET localizacao = ST_SetSRID(ST_MakePoint(-38.5267, -3.7319), 4326)::geography, nota_media = 4.5
                 WHERE user_id = ?::uuid
                """, paula.id().toString());

        var cliente = cadastrarCliente("Cliente da Paula", "cliente.paula@onda.test");
        UUID aberto = pedidoDe(cliente, "Pedido aberto, Rua C 30", "PENDENTE");
        UUID feito  = pedidoDe(cliente, "Pedido feito, Rua C 30", "CONCLUIDO");
        // proposta ATIVA pela API real (ela está VERIFICADA); a do pedido concluído é histórico
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + paula.token())
                .body("{\"valor\":180.00,\"prazoDias\":1,\"horarioProposto\":\"%s\"}"
                        .formatted(Instant.now().plus(2, ChronoUnit.DAYS)))
                .when().post("/api/v1/service-requests/{id}/proposals", aberto)
                .then().statusCode(201).body("status", equalTo("ATIVA"));
        proposta(feito, paula.id(), ProposalStatus.ACEITA);
        pagamento(feito, TransactionStatus.LIBERADO);
        // histórico de uma disputa já mediada (o pedido está CONCLUIDO, não EM_DISPUTA — senão ela nem excluiria):
        // o motivo/detalhes foi escrito por ELA, prestadora, não pelo cliente
        jdbc.update("""
                UPDATE service_requests SET motivo_disputa = 'motivo escrito pela prestadora',
                       detalhes_disputa = 'detalhe escrito pela prestadora'
                 WHERE id = ?::uuid
                """, feito.toString());
        mensagem(feito, paula.id(), "Chego às 14h, meu telefone é 85 98888-0000");
        avaliacao(feito, paula.id(), cliente.id(), ReviewType.PRESTADOR_AVALIA_CLIENTE, 5, "Cliente pontual");
        avaliacao(feito, cliente.id(), paula.id(), ReviewType.CLIENTE_AVALIA_PRESTADOR, 5, "Excelente");

        // antes: aparece na busca por proximidade
        var antes = given().header("Authorization", "Bearer " + cliente.token())
                .queryParam("lat", -3.7319).queryParam("lng", -38.5267)
                .queryParam("raioKm", 50).queryParam("categoria", "eletrica")
                .when().get("/api/v1/providers/nearby").then().statusCode(200).extract().jsonPath().getList("id");
        assertThat(antes, hasItem(paula.id().toString()));

        excluirConta(paula.token(), SENHA_PADRAO).then().statusCode(204);

        // users + perfil: nada pessoal; sem status de operação; categoria e nota são do histórico
        var u = conta(paula.id());
        assertThat(u.get("nome"), equalTo("Usuário removido"));
        assertThat((String) u.get("email"), matchesPattern("removido-[0-9a-f-]{36}@excluido\\.invalid"));
        var perfil = jdbc.queryForMap("""
                SELECT bio, chave_pix_cifrada, cpf_cifrado, localizacao IS NULL AS sem_local, status_verificacao,
                       categoria, nota_media
                  FROM providers_profile WHERE user_id = ?::uuid
                """, paula.id().toString());
        assertThat(perfil.get("bio"), nullValue());
        assertThat(perfil.get("chave_pix_cifrada"), nullValue());
        assertThat(perfil.get("cpf_cifrado"), nullValue());
        assertThat(perfil.get("sem_local"), equalTo(true));
        assertThat(perfil.get("status_verificacao"), equalTo("SUSPENSO"));
        assertThat(perfil.get("categoria"), equalTo("eletrica"));
        assertThat((BigDecimal) perfil.get("nota_media"), comparesEqualTo(new BigDecimal("4.5")));

        // propostas: a aberta foi encerrada; a do serviço concluído fica
        assertThat(texto("SELECT status FROM proposals WHERE service_request_id = ?::uuid AND prestador_id = ?::uuid",
                aberto.toString(), paula.id().toString()), equalTo("ENCERRADA"));
        assertThat(texto("SELECT status FROM proposals WHERE service_request_id = ?::uuid AND prestador_id = ?::uuid",
                feito.toString(), paula.id().toString()), equalTo("ACEITA"));

        // mensagens e avaliações: o texto DELA saiu; o que o cliente escreveu sobre ela fica
        assertThat(texto("SELECT conteudo FROM messages WHERE remetente_id = ?::uuid", paula.id().toString()),
                equalTo("[mensagem removida]"));
        assertThat(texto("SELECT comentario FROM reviews WHERE avaliador_id = ?::uuid", paula.id().toString()), nullValue());
        assertThat(texto("SELECT comentario FROM reviews WHERE avaliador_id = ?::uuid", cliente.id().toString()), equalTo("Excelente"));

        // os dados do CLIENTE não vão junto com os do prestador. O pedido segue PROPOSTO (a proposta dela
        // virou ENCERRADA): é o mesmo estado de quando o cliente recusa a única proposta — lacuna anterior,
        // ver o ADR da exclusão de conta. Nem cancelado nem apagado.
        assertThat(texto("SELECT status FROM service_requests WHERE id = ?::uuid", aberto.toString()), equalTo("PROPOSTO"));
        assertThat(texto("SELECT descricao FROM service_requests WHERE id = ?::uuid", feito.toString()), equalTo("Pedido feito, Rua C 30"));
        assertThat(conta(cliente.id()).get("nome"), equalTo("Cliente da Paula"));

        // achado da revisão cruzada: o texto de disputa que ELA (prestadora) escreveu some junto, mesmo não
        // sendo a cliente do pedido; a descrição acima (dela, cliente) prova que nada do pedido do cliente foi tocado
        assertThat("motivo escrito pela prestadora sai da base quando ela exclui a conta",
                vazio("motivo_disputa", "service_requests", feito), is(true));
        assertThat("detalhes escritos pela prestadora saem junto",
                vazio("detalhes_disputa", "service_requests", feito), is(true));

        // depois: sumiu da busca por proximidade
        var depois = given().header("Authorization", "Bearer " + cliente.token())
                .queryParam("lat", -3.7319).queryParam("lng", -38.5267)
                .queryParam("raioKm", 50).queryParam("categoria", "eletrica")
                .when().get("/api/v1/providers/nearby").then().statusCode(200).extract().jsonPath().getList("id");
        assertThat(depois, not(hasItem(paula.id().toString())));

        // o e-mail e o CPF dela ficam livres
        cadastrarPrestador("Paula Voltou", paula.email(), CPF_PAULA);
    }

    @Test @Order(31)
    @DisplayName("31 · Antifraude: prestador reprovado que exclui a conta mantém o hash do CPF — o vínculo não se desfaz")
    void exclusaoDeConta_prestadorReprovadoMantemOHashDoCpf() {
        // Hoje o hash do CPF só é gravado pelo fluxo de verificação de identidade (o cadastro de prestador
        // não o grava nem o consulta — ver ADR). Por isso o prestador passa por ele aqui: é o único caminho
        // em que a retenção do hash tem o que reter.
        var quim = cadastrarPrestador("Quim Reprovado", "quim.exclusao@onda.test", CPF_QUIM);
        moderarPrestador(quim.id().toString(), "REPROVAR");
        verificarIdentidade(quim, CPF_QUIM);
        String hash = (String) conta(quim.id()).get("cpf_hash");
        assertThat(hash, notNullValue());

        excluirConta(quim.token(), SENHA_PADRAO).then().statusCode(204);

        var u = conta(quim.id());
        assertThat(u.get("nome"), equalTo("Usuário removido"));
        assertThat("reprovado pela moderação: o hash fica", u.get("cpf_hash"), equalTo(hash));

        // outra conta não consegue vincular o mesmo CPF: excluir não burla o banimento
        var outro = cadastrarCliente("Outra Conta", "outra.conta.exclusao@onda.test");
        verificarIdentidade(outro.token(), CPF_QUIM).then().statusCode(422)
                .body("code", equalTo("CPF_ALREADY_REGISTERED"));
    }

    @Test @Order(32)
    @DisplayName("32 · O painel admin mostra a conta excluída como EXCLUIDO e não deixa suspender, reativar nem moderar")
    void exclusaoDeConta_adminVeExcluidoENaoAltera() {
        String admin = tokenAdmin();
        String marta = martaId.toString();
        String paula = paulaId.toString();

        var lista = given().header("Authorization", "Bearer " + admin)
                .when().get("/api/v1/admin/users").then().statusCode(200).extract().jsonPath();
        for (String id : List.of(marta, paula)) {
            assertThat(lista.getString("find { it.id == '%s' }.status".formatted(id)), equalTo("EXCLUIDO"));
            assertThat(lista.getString("find { it.id == '%s' }.nome".formatted(id)), equalTo("Usuário removido"));
        }

        // erro de negócio (422 com código), não 500 — User.reativar() lança IllegalStateException para conta excluída
        for (String acao : List.of("suspend", "reactivate")) {
            given().header("Authorization", "Bearer " + admin)
                    .when().post("/api/v1/admin/users/{id}/" + acao, marta)
                    .then().statusCode(422).body("code", equalTo("ACCOUNT_DELETED"));
        }
        for (String acao : List.of("APROVAR", "REPROVAR", "SUSPENDER")) {
            given().contentType(ContentType.JSON).header("Authorization", "Bearer " + admin)
                    .body("{\"action\":\"%s\"}".formatted(acao))
                    .when().post("/api/v1/admin/providers/{id}/moderate", paula)
                    .then().statusCode(422).body("code", equalTo("ACCOUNT_DELETED"));
        }
        // a recusa não deixa trilha de auditoria de uma ação que não aconteceu
        assertThat(contar("SELECT count(*) FROM admin_audit_log WHERE entidade_id = ?::uuid", marta), equalTo(0));
        assertThat(texto("SELECT status_verificacao FROM providers_profile WHERE user_id = ?::uuid", paula), equalTo("SUSPENSO"));
        assertThat(conta(martaId).get("ativo"), equalTo(false));
    }

    @Test @Order(33)
    @DisplayName("33 · A leitura do usuário na exclusão TRAVA a linha: uma 2ª transação espera a 1ª terminar")
    void exclusaoDeConta_aLeituraDoUsuarioTravaALinha() throws Exception {
        // Prova determinística da trava (@Lock PESSIMISTIC_WRITE), no mesmo molde do passo 26: sem ela, dois
        // pedidos de exclusão simultâneos leem a conta "viva" e rodam a limpeza (e mandam o e-mail) duas vezes.
        var bia = cadastrarCliente("Bia Trava", "bia.exclusao@onda.test");

        var travaObtida = new java.util.concurrent.CountDownLatch(1);
        var liberar     = new java.util.concurrent.CountDownLatch(1);
        var segundaTerminou = new java.util.concurrent.atomic.AtomicBoolean(false);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);

        var primeira = pool.submit(() -> transacao.executeWithoutResult(s -> {
            userRepository.findByIdComTrava(bia.id());
            travaObtida.countDown();
            try { liberar.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        Assertions.assertTrue(travaObtida.await(10, java.util.concurrent.TimeUnit.SECONDS), "a 1ª transação deveria ter a trava");

        var segunda = pool.submit(() -> {
            transacao.executeWithoutResult(s -> userRepository.findByIdComTrava(bia.id()));
            segundaTerminou.set(true);
        });
        Thread.sleep(800);
        assertThat("a 2ª transação deveria estar ESPERANDO a trava, não ter terminado",
                segundaTerminou.get(), is(false));

        liberar.countDown();
        primeira.get(10, java.util.concurrent.TimeUnit.SECONDS);
        segunda.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(segundaTerminou.get(), is(true));
        pool.shutdown();
    }

    @Test @Order(34)
    @DisplayName("34 · Toque duplo: dois pedidos de exclusão simultâneos excluem UMA vez e mandam UM e-mail")
    void exclusaoDeConta_toqueDuplo() throws Exception {
        var duda = cadastrarCliente("Duda Dedo Nervoso", "duda.exclusao@onda.test");
        CaixaDeEntrada.EMAILS.clear();

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var largada = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> respostas = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            respostas.add(pool.submit(() -> {
                largada.await();
                return excluirConta(duda.token(), SENHA_PADRAO).statusCode();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new java.util.ArrayList<>();
        for (var r : respostas) codigos.add(r.get(60, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();

        // um dos dois pode chegar depois do commit do outro e dar 401 (conta já inativa); o que não pode é 5xx
        assertThat(codigos, everyItem(anyOf(equalTo(204), equalTo(401))));
        assertThat(codigos, hasItem(204));
        assertThat(conta(duda.id()).get("excluido_em"), notNullValue());

        aguardarEmails(1);
        Thread.sleep(700);
        long avisos = CaixaDeEntrada.EMAILS.stream().filter(e -> e.para().equals(duda.email())).count();
        assertThat("um só aviso de exclusão", avisos, equalTo(1L));
    }

    // ─── Limite de tentativas de senha (login e exclusão de conta) ─────────
    //
    // Sem ele a senha de uma conta podia ser testada sem fim. O que só o Postgres real prova: o contador PERSISTE mesmo
    // com a exceção (noRollbackFor), e a leitura com trava de linha faz palpites simultâneos contarem certo.
    // O tempo passa por UPDATE em senha_bloqueada_ate (esperar 15 min não cabe num teste).

    private java.util.Map<String, Object> tentativas(UUID id) {
        return jdbc.queryForMap("SELECT senha_falhas, senha_bloqueada_ate FROM users WHERE id = ?::uuid", id.toString());
    }

    private void passarOBloqueio(UUID id) {
        jdbc.update("UPDATE users SET senha_bloqueada_ate = now() - interval '1 second' WHERE id = ?::uuid", id.toString());
    }

    @Test @Order(35)
    @DisplayName("35 · Login: 5 senhas erradas bloqueiam a conta (429 + Retry-After), nem a certa entra, e libera passado o bloqueio")
    void limiteDeTentativas_loginBloqueiaELibera() {
        var vera = cadastrarCliente("Vera Tentativas", "vera.tentativas@onda.test");
        var outra = cadastrarCliente("Outra Tentativas", "outra.tentativas@onda.test");

        for (int i = 1; i <= 5; i++) {
            login(vera.email(), "errada-" + i).then().statusCode(422).body("code", equalTo("INVALID_CREDENTIALS"));
        }
        // o contador PERSISTIU apesar de cada requisição ter terminado em exceção
        assertThat(tentativas(vera.id()).get("senha_falhas"), equalTo(5));
        assertThat(tentativas(vera.id()).get("senha_bloqueada_ate"), notNullValue());

        // bloqueada: 429 com Retry-After, MESMO com a senha certa (senão o limite não limitaria nada)
        login(vera.email(), SENHA_PADRAO).then().statusCode(429)
                .header("Retry-After", matchesPattern("\\d+"))
                .body("code", equalTo("TOO_MANY_ATTEMPTS"))
                .body("message", containsString("Muitas tentativas"));
        // e o bloqueio é da conta, não do sistema: outra conta entra normalmente
        login(outra.email(), SENHA_PADRAO).then().statusCode(200);

        passarOBloqueio(vera.id());
        login(vera.email(), SENHA_PADRAO).then().statusCode(200);
        assertThat("o acerto zera o contador", tentativas(vera.id()).get("senha_falhas"), equalTo(0));
        assertThat(tentativas(vera.id()).get("senha_bloqueada_ate"), nullValue());
    }

    @Test @Order(36)
    @DisplayName("36 · A recuperação de senha por e-mail encerra o bloqueio (a saída de quem foi bloqueado por palpites alheios)")
    void limiteDeTentativas_recuperacaoPorEmailEncerraOBloqueio() throws Exception {
        var rita = cadastrarCliente("Rita Bloqueada", "rita.bloqueada@onda.test");
        for (int i = 1; i <= 5; i++) login(rita.email(), "errada-" + i).then().statusCode(422);
        login(rita.email(), SENHA_PADRAO).then().statusCode(429);

        CaixaDeEntrada.EMAILS.clear();
        pedirCodigo(rita.email());
        aguardarEmails(1);
        String codigo = extrairCodigo(CaixaDeEntrada.EMAILS.get(0).corpo());
        redefinir(rita.email(), codigo, "NovaSenha@2").statusCode(204);

        // sem esperar o bloqueio acabar: a posse do e-mail foi provada
        login(rita.email(), "NovaSenha@2").then().statusCode(200);
        assertThat(tentativas(rita.id()).get("senha_falhas"), equalTo(0));
        assertThat(tentativas(rita.id()).get("senha_bloqueada_ate"), nullValue());
    }

    @Test @Order(37)
    @DisplayName("37 · 30 palpites SIMULTÂNEOS de senha: só 5 são avaliados, os outros 25 batem no bloqueio (a trava da linha)")
    void limiteDeTentativas_palpitesSimultaneosNaoFuramOLimite() throws Exception {
        var nina = cadastrarCliente("Nina Palpites", "nina.palpites@onda.test");
        int palpites = 30;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(palpites);
        var largada = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> respostas = new java.util.ArrayList<>();
        for (int i = 0; i < palpites; i++) {
            String palpite = "errada-" + i;
            respostas.add(pool.submit(() -> {
                largada.await();
                return login(nina.email(), palpite).statusCode();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new java.util.ArrayList<>();
        for (var r : respostas) codigos.add(r.get(120, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();

        // Com a linha travada as tentativas se enfileiram: as 5 primeiras são avaliadas (422) e fecham o bloqueio, as
        // outras 25 já o encontram (429). Sem a trava, leituras do mesmo contador deixariam passar mais de 5.
        assertThat(codigos.stream().filter(c -> c == 422).count(), equalTo(5L));
        assertThat(codigos.stream().filter(c -> c == 429).count(), equalTo(25L));
        assertThat(tentativas(nina.id()).get("senha_falhas"), equalTo(5));
    }

    @Test @Order(38)
    @DisplayName("38 · Exclusão de conta: 5 senhas erradas bloqueiam (429), nem a certa exclui; o bloqueio é da conta e vale para o login")
    void limiteDeTentativas_exclusaoDeConta() {
        var dora = cadastrarCliente("Dora Token Roubado", "dora.token@onda.test");

        for (int i = 1; i <= 5; i++) {
            excluirConta(dora.token(), "errada-" + i).then().statusCode(422).body("code", equalTo("INVALID_PASSWORD"));
        }
        // quem tem o token roubado não adivinha a senha: a certa também é recusada enquanto durar o bloqueio
        excluirConta(dora.token(), SENHA_PADRAO).then().statusCode(429).body("code", equalTo("TOO_MANY_ATTEMPTS"));
        assertThat(conta(dora.id()).get("excluido_em"), nullValue());
        // o contador é da conta: o login também fica bloqueado (e a dona tem a recuperação por e-mail como saída)
        login(dora.email(), SENHA_PADRAO).then().statusCode(429);

        passarOBloqueio(dora.id());
        excluirConta(dora.token(), SENHA_PADRAO).then().statusCode(204);
        assertThat(conta(dora.id()).get("excluido_em"), notNullValue());
    }

    @Test @Order(39)
    @DisplayName("39 · Confirmar identidade durante uma exclusão em voo ESPERA a trava e não desfaz a anonimização (lost update)")
    void exclusaoDeConta_confirmarIdentidadeEmVoo_naoDesfazAAnonimizacao() throws Exception {
        // Achado da revisão cruzada (2026-10-05): verifyIdentity lia o usuário sem trava. Se uma exclusão
        // estivesse em voo (já com a trava, ainda sem commitar), verifyIdentity lia a conta "viva" antes,
        // e ao salvar depois gravava de volta TODOS os campos do objeto em memória — nome, e-mail, ativo,
        // excluido_em — na forma antiga: a anonimização desfeita (lost update; UPDATE não é por coluna).
        // Prova determinística: a exclusão fica parada ANTES do commit (como o passo 33), e só então a
        // confirmação é disparada — com a trava, ela TEM de esperar; sem a trava, ela correria na frente.
        var bia = cadastrarCliente("Bia Corrida", "bia.corrida.exclusao@onda.test");
        String emailOriginal = bia.email();

        var travaObtida = new java.util.concurrent.CountDownLatch(1);
        var liberar     = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);

        // "exclusão em voo": já com a trava da linha, parada antes do commit da anonimização
        var exclusaoEmVoo = pool.submit(() -> transacao.executeWithoutResult(status -> {
            User u = userRepository.findByIdComTrava(bia.id()).orElseThrow();
            u.anonimizar("removido-corrida@excluido.invalid",
                    passwordEncoder.encode(UUID.randomUUID().toString()), false);
            travaObtida.countDown();
            try { liberar.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        Assertions.assertTrue(travaObtida.await(10, java.util.concurrent.TimeUnit.SECONDS),
                "a exclusão em voo deveria ter a trava");

        var confirmacaoTerminou = new java.util.concurrent.atomic.AtomicBoolean(false);
        var confirmacao = pool.submit(() -> {
            authService.verifyIdentity("900.110.220-33", bia.id());
            confirmacaoTerminou.set(true);
        });
        Thread.sleep(800);
        assertThat("a confirmação deveria estar ESPERANDO a trava da exclusão em voo, não ter terminado",
                confirmacaoTerminou.get(), is(false));

        liberar.countDown();   // a exclusão termina e COMMITA a anonimização
        exclusaoEmVoo.get(10, java.util.concurrent.TimeUnit.SECONDS);
        confirmacao.get(10, java.util.concurrent.TimeUnit.SECONDS);   // só agora, sobre o estado JÁ anonimizado
        pool.shutdown();

        var u = conta(bia.id());
        assertThat("o nome anonimizado não pode voltar ao original", u.get("nome"), equalTo("Usuário removido"));
        assertThat("o e-mail anonimizado não pode voltar ao original", u.get("email"), not(equalTo(emailOriginal)));
        assertThat("ativo continua false: a confirmação não pode reabrir a conta", u.get("ativo"), equalTo(false));
        assertThat("excluido_em continua preenchido", u.get("excluido_em"), notNullValue());
    }

    @Test @Order(40)
    @DisplayName("40 · O acerto de senha na exclusão PERSISTE mesmo quando ela é recusada por pendência logo depois")
    void limiteDeTentativas_acertoPersisteQuandoAExclusaoEhRecusadaDepois() {
        // Achado da revisão cruzada (2026-10-05): excluir() lia a senha certa, zerava o contador, e SÓ DEPOIS
        // conferia pendências (pedido em curso). ACCOUNT_HAS_ACTIVE_ORDERS tem de desfazer tudo o mais — mas,
        // na mesma transação, "tudo o mais" incluía o próprio zeramento do contador: ele voltava a valer os
        // 4 erros de antes, e o PRÓXIMO erro já bloqueava a conta com só 1 erro depois do "acerto".
        var caio  = cadastrarCliente("Caio Pendencia Tentativas", "caio.tentativas.pendencia@onda.test");
        var paulo = cadastrarPrestador("Paulo Pendencia Tentativas", "paulo.tentativas.pendencia@onda.test", "635.084.325-00");
        UUID emCurso = pedidoDe(caio, "Pedido em curso, Rua K 70", "ACEITO");
        proposta(emCurso, paulo.id(), ProposalStatus.ACEITA);

        for (int i = 1; i <= 4; i++) {
            excluirConta(caio.token(), "errada-" + i).then().statusCode(422).body("code", equalTo("INVALID_PASSWORD"));
        }
        assertThat(tentativas(caio.id()).get("senha_falhas"), equalTo(4));

        // senha CERTA, mas a conta tem pedido em curso: a exclusão é recusada, não a senha
        excluirConta(caio.token(), SENHA_PADRAO).then().statusCode(422).body("code", equalTo("ACCOUNT_HAS_ACTIVE_ORDERS"));
        assertThat(conta(caio.id()).get("excluido_em"), nullValue());

        // o acerto PERSISTIU: o contador está em 0, não em 4
        assertThat("o acerto de senha não pode ser desfeito pela recusa de negócio que vem depois",
                tentativas(caio.id()).get("senha_falhas"), equalTo(0));

        // prova final: UM erro agora bloqueia com 5 no total só se o contador NÃO tivesse zerado; com 0, falta muito
        excluirConta(caio.token(), "errada-de-novo").then().statusCode(422).body("code", equalTo("INVALID_PASSWORD"));
        assertThat(tentativas(caio.id()).get("senha_falhas"), equalTo(1));
    }

    @Test @Order(41)
    @DisplayName("41 · O acerto de senha no LOGIN também persiste quando a conta é recusada por estar suspensa logo depois")
    void limiteDeTentativas_acertoPersisteQuandoOLoginEhRecusadoPorContaSuspensaDepois() {
        var greg = cadastrarCliente("Greg Suspenso Tentativas", "greg.tentativas.suspenso@onda.test");
        for (int i = 1; i <= 4; i++) {
            login(greg.email(), "errada-" + i).then().statusCode(422).body("code", equalTo("INVALID_CREDENTIALS"));
        }
        assertThat(tentativas(greg.id()).get("senha_falhas"), equalTo(4));

        given().header("Authorization", "Bearer " + tokenAdmin())
                .when().post("/api/v1/admin/users/{id}/suspend", greg.id()).then().statusCode(200);

        // senha CERTA, mas a conta está suspensa: o login é recusado por isso, não pela senha
        login(greg.email(), SENHA_PADRAO).then().statusCode(422).body("code", equalTo("ACCOUNT_SUSPENDED"));

        assertThat("o acerto de senha não pode ser desfeito pela recusa de negócio (conta suspensa) que vem depois",
                tentativas(greg.id()).get("senha_falhas"), equalTo(0));
    }

    // ─── Revisão cruzada, 2ª rodada: quem grava a entidade inteira não pode desfazer a anonimização ───
    //
    // suspender/reativar, moderar, chave Pix e nota média liam a linha sem trava e a regravam inteira. Uma exclusão de conta
    // que commitasse nesse intervalo era desfeita (lost update: o UPDATE não é por coluna). A trava só vale como PRIMEIRA
    // leitura da linha — sobre uma entidade já carregada o Hibernate devolve a instância antiga.

    @Autowired com.onda.marketplace.provider.ProviderProfileRepository perfilPrestadorRepository;

    /** "Exclusão em voo": já com a trava do usuário e a anonimização feita, parada ANTES do commit (molde dos passos 33/42). */
    private java.util.concurrent.Future<?> exclusaoEmVoo(java.util.concurrent.ExecutorService pool, UUID userId, String emailAnonimo,
                                                         boolean comPerfil, java.util.concurrent.CountDownLatch travaObtida,
                                                         java.util.concurrent.CountDownLatch liberar) {
        return pool.submit(() -> transacao.executeWithoutResult(status -> {
            User u = userRepository.findByIdComTrava(userId).orElseThrow();
            u.anonimizar(emailAnonimo, passwordEncoder.encode(UUID.randomUUID().toString()), false);
            if (comPerfil) {
                perfilPrestadorRepository.findByUserId(userId).ifPresent(p -> {
                    p.anonimizar();
                    perfilPrestadorRepository.save(p);
                });
            }
            travaObtida.countDown();
            try { liberar.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
    }

    @Test @Order(42)
    @DisplayName("42 · Admin suspende/reativa durante uma exclusão em voo: espera, recusa (ACCOUNT_DELETED) e NÃO desfaz a anonimização")
    void admin_suspenderDuranteExclusaoEmVoo_naoDesfazAAnonimizacao() throws Exception {
        var gil = cadastrarCliente("Gil Corrida Admin", "gil.corrida.admin@onda.test");
        var travaObtida = new java.util.concurrent.CountDownLatch(1);
        var liberar     = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var exclusao = exclusaoEmVoo(pool, gil.id(), "removido-gil-admin@excluido.invalid", false, travaObtida, liberar);
        Assertions.assertTrue(travaObtida.await(10, java.util.concurrent.TimeUnit.SECONDS), "a exclusão em voo deveria ter a trava");

        var suspensao = pool.submit(() -> given().header("Authorization", "Bearer " + tokenAdmin())
                .when().post("/api/v1/admin/users/{id}/suspend", gil.id()).then().extract());
        Thread.sleep(800);
        assertThat("o admin deveria estar ESPERANDO a trava da exclusão em voo", suspensao.isDone(), is(false));

        liberar.countDown();
        exclusao.get(10, java.util.concurrent.TimeUnit.SECONDS);
        var resposta = suspensao.get(15, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(resposta.statusCode(), equalTo(422));
        assertThat(resposta.path("code"), equalTo("ACCOUNT_DELETED"));
        var u = conta(gil.id());
        assertThat("o nome anonimizado não pode voltar ao original", u.get("nome"), equalTo("Usuário removido"));
        assertThat("o e-mail anonimizado não pode voltar ao original", u.get("email"), not(equalTo(gil.email())));
        assertThat("excluido_em continua preenchido", u.get("excluido_em"), notNullValue());
        assertThat("ativo continua false", u.get("ativo"), equalTo(false));
    }

    @Test @Order(43)
    @DisplayName("43 · Moderar o prestador durante uma exclusão em voo: espera, recusa (ACCOUNT_DELETED) e o perfil continua anonimizado")
    void admin_moderarDuranteExclusaoEmVoo_naoDesfazAAnonimizacaoDoPerfil() throws Exception {
        var iris = cadastrarPrestador("Iris Corrida Moderacao", "iris.corrida.moderacao@onda.test", "325.342.440-51");
        var travaObtida = new java.util.concurrent.CountDownLatch(1);
        var liberar     = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var exclusao = exclusaoEmVoo(pool, iris.id(), "removido-iris-mod@excluido.invalid", true, travaObtida, liberar);
        Assertions.assertTrue(travaObtida.await(10, java.util.concurrent.TimeUnit.SECONDS), "a exclusão em voo deveria ter a trava");

        var moderacao = pool.submit(() -> given().contentType(ContentType.JSON).header("Authorization", "Bearer " + tokenAdmin())
                .body("{\"action\":\"APROVAR\"}")
                .when().post("/api/v1/admin/providers/{id}/moderate", iris.id()).then().extract());
        Thread.sleep(800);
        assertThat("a moderação deveria estar ESPERANDO a trava da exclusão em voo", moderacao.isDone(), is(false));

        liberar.countDown();
        exclusao.get(10, java.util.concurrent.TimeUnit.SECONDS);
        var resposta = moderacao.get(15, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(resposta.statusCode(), equalTo(422));
        assertThat(resposta.path("code"), equalTo("ACCOUNT_DELETED"));
        var perfil = jdbc.queryForMap("SELECT bio, cpf_cifrado, status_verificacao FROM providers_profile WHERE user_id = ?::uuid", iris.id().toString());
        assertThat("o perfil continua SUSPENSO, não vira VERIFICADO", perfil.get("status_verificacao"), equalTo("SUSPENSO"));
        assertThat("a bio apagada não volta", perfil.get("bio"), nullValue());
        assertThat("o CPF cifrado apagado não volta", perfil.get("cpf_cifrado"), nullValue());
    }

    @Test @Order(44)
    @DisplayName("44 · Cadastrar chave Pix durante a exclusão em voo: espera, recusa (PROVIDER_NOT_FOUND) e nada do perfil volta")
    void prestador_chavePixDuranteExclusaoEmVoo_naoRessuscitaOPerfil() throws Exception {
        var jonas = cadastrarPrestador("Jonas Corrida Pix", "jonas.corrida.pix@onda.test", "701.974.216-52");
        var travaObtida = new java.util.concurrent.CountDownLatch(1);
        var liberar     = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var exclusao = exclusaoEmVoo(pool, jonas.id(), "removido-jonas-pix@excluido.invalid", true, travaObtida, liberar);
        Assertions.assertTrue(travaObtida.await(10, java.util.concurrent.TimeUnit.SECONDS), "a exclusão em voo deveria ter a trava");

        var pix = pool.submit(() -> given().contentType(ContentType.JSON).header("Authorization", "Bearer " + jonas.token())
                .body("{\"chavePix\":\"jonas.pix@pix.com\"}")
                .when().put("/api/v1/providers/me/chave-pix").then().extract());
        Thread.sleep(800);
        assertThat("o cadastro da chave deveria estar ESPERANDO a trava da exclusão em voo", pix.isDone(), is(false));

        liberar.countDown();
        exclusao.get(10, java.util.concurrent.TimeUnit.SECONDS);
        var resposta = pix.get(15, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(resposta.statusCode(), equalTo(422));
        assertThat(resposta.path("code"), equalTo("PROVIDER_NOT_FOUND"));
        var perfil = jdbc.queryForMap("SELECT bio, cpf_cifrado, chave_pix_cifrada, status_verificacao FROM providers_profile WHERE user_id = ?::uuid", jonas.id().toString());
        assertThat("a chave Pix nova não é gravada na conta excluída", perfil.get("chave_pix_cifrada"), nullValue());
        assertThat("a bio apagada não volta", perfil.get("bio"), nullValue());
        assertThat("o CPF cifrado apagado não volta", perfil.get("cpf_cifrado"), nullValue());
        assertThat(perfil.get("status_verificacao"), equalTo("SUSPENSO"));
    }

    @Test @Order(45)
    @DisplayName("45 · A nota média é gravada por UPDATE só da coluna: uma anonimização commitada antes não é desfeita pelo perfil velho em memória")
    void notaMedia_updateSoDaColuna_naoDesfazAnonimizacaoCommitadaNoMeio() throws Exception {
        var kaue = cadastrarPrestador("Kaue Nota Media", "kaue.nota.media@onda.test", "497.736.187-30");
        transacao.executeWithoutResult(status -> {
            var emMemoria = perfilPrestadorRepository.findByUserId(kaue.id()).orElseThrow();   // perfil "velho" na sessão
            assertThat(emMemoria.getBio(), notNullValue());
            // outra transação anonimiza e commita (thread própria = transação própria)
            var t = new Thread(() -> transacao.executeWithoutResult(s2 -> {
                User u = userRepository.findByIdComTrava(kaue.id()).orElseThrow();
                u.anonimizar("removido-kaue-nota@excluido.invalid", passwordEncoder.encode(UUID.randomUUID().toString()), false);
                perfilPrestadorRepository.findByUserId(kaue.id()).ifPresent(p -> { p.anonimizar(); perfilPrestadorRepository.save(p); });
            }));
            t.start();
            try { t.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            perfilPrestadorRepository.atualizarNotaMedia(kaue.id(), new BigDecimal("4.5"), Instant.now());
        });
        var perfil = jdbc.queryForMap("SELECT bio, cpf_cifrado, status_verificacao, nota_media FROM providers_profile WHERE user_id = ?::uuid", kaue.id().toString());
        assertThat("a nota é gravada (é do histórico)", (BigDecimal) perfil.get("nota_media"), comparesEqualTo(new BigDecimal("4.5")));
        assertThat("a anonimização do perfil não é desfeita", perfil.get("bio"), nullValue());
        assertThat(perfil.get("cpf_cifrado"), nullValue());
        assertThat(perfil.get("status_verificacao"), equalTo("SUSPENSO"));
    }

    @Test @Order(46)
    @DisplayName("46 · 20 exclusões SIMULTÂNEAS (contas diferentes) terminam todas em 204 — a transação externa não segura conexão ociosa e o pool não esgota")
    void exclusaoDeConta_vinteSimultaneas_naoEsgotamOPool() throws Exception {
        // Revisão cruzada (2ª rodada): excluir() era @Transactional e chamava autenticarPorId (REQUIRES_NEW) logo na 1ª linha. O
        // Spring abre a conexão da transação externa na entrada e a segura ociosa; com mais requisições que conexões (o pool é 10),
        // todas seguram a externa e nenhuma consegue a interna — esperam o timeout do pool (30 s) e viram 500. Foi o defeito do login.
        int quantas = 20;
        var contas = new java.util.ArrayList<Conta>();
        for (int i = 0; i < quantas; i++) contas.add(cadastrarCliente("Pool " + i, "pool.exclusao." + i + "@onda.test"));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(quantas);
        var largada = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> respostas = new java.util.ArrayList<>();
        for (var c : contas) {
            respostas.add(pool.submit(() -> {
                largada.await();
                return excluirConta(c.token(), SENHA_PADRAO).statusCode();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new java.util.ArrayList<>();
        for (var r : respostas) codigos.add(r.get(20, java.util.concurrent.TimeUnit.SECONDS));   // < o timeout de 30 s do pool
        pool.shutdown();

        assertThat(codigos, everyItem(equalTo(204)));
        for (var c : contas) assertThat(conta(c.id()).get("excluido_em"), notNullValue());
    }
}
