package com.onda.marketplace.e2e;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.UUID;

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

    @LocalServerPort int port;

    /** Lê o id que o gateway gerou — é o que o gateway real saberia ao chamar nosso webhook. */
    @Autowired JdbcTemplate jdbc;

    // Só o passo dos relatórios (US29) usa estes: cria um admin e um 2º pedido direto no banco.
    @Autowired UserRepository           userRepository;
    @Autowired ServiceRequestRepository serviceRequestRepository;
    @Autowired TransactionRepository    transactionRepository;
    @Autowired PasswordEncoder          passwordEncoder;

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
    @DisplayName("06 · Prestador envia proposta ao pedido")
    void enviarProposta() {
        proposalId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenPrestador)
                .body("""
                        {
                          "valor":           250.00,
                          "prazoDias":       1,
                          "horarioProposto": "%s"
                        }
                        """.formatted(Instant.now().plus(2, ChronoUnit.DAYS)))
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
    @DisplayName("08 · Cliente aceita proposta → pedido vira ACEITO")
    void aceitarProposta() {
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
    @DisplayName("12 · Prestador inicia o serviço → EM_ANDAMENTO")
    void iniciarServico() {
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
        // Admin direto no banco — o profile e2e não roda o seed.
        userRepository.save(User.builder().nome("Admin E2E").email("admin.e2e@onda.test")
                .senhaHash(passwordEncoder.encode("Admin@123")).role(UserRole.ROLE_ADMIN).build());
        String tokenAdmin = given()
                .contentType(ContentType.JSON)
                .body("""
                        { "email": "admin.e2e@onda.test", "senha": "Admin@123" }
                        """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");

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
}
