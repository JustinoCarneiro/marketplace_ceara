package com.onda.marketplace.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static io.restassured.RestAssured.given;
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
            new PostgreSQLContainer<>("postgis/postgis:15-3.4")
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

    // Estado compartilhado entre os steps (JUnit @Order garante sequência)
    static String tokenCliente;
    static String tokenPrestador;
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
                          "senha": "Senha@123"
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
        tokenPrestador = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome":      "João Eletricista",
                          "email":     "joao.e2e@onda.test",
                          "senha":     "Senha@123",
                          "cpf":       "123.456.789-09",
                          "categoria": "eletrica"
                        }
                        """)
                .when()
                .post("/api/v1/auth/register/provider")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("role", equalTo("ROLE_PROVIDER"))
                .extract().path("accessToken");
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
                          "lng":       -38.5433
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
                          "valor":     250.00,
                          "prazoDias": 1
                        }
                        """)
                .when()
                .post("/api/v1/service-requests/{id}/proposals", requestId)
                .then()
                .statusCode(201)
                .body("valor",    equalTo(250.0f))
                .body("status",   equalTo("PENDENTE"))
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
                .body("status", equalTo("ACEITO"));
    }

    @Test @Order(9)
    @DisplayName("09 · Cliente inicia pagamento → transação PENDENTE criada")
    void iniciarPagamento() {
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
                .extract().response();

        transactionId = resp.path("id");
        gatewayTxId   = resp.path("gatewayTransactionId");
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
                        """, gatewayTxId != null ? gatewayTxId : transactionId))
                .when()
                .post("/api/v1/payments/webhook")
                .then()
                .statusCode(anyOf(is(200), is(204)));
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
                .statusCode(200)
                .body("status", equalTo("EM_ANDAMENTO"));
    }

    @Test @Order(13)
    @DisplayName("13 · Cliente confirma conclusão → CONCLUIDO")
    void confirmarConclusao() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when()
                .post("/api/v1/service-requests/{id}/confirm-completion", requestId)
                .then()
                .statusCode(200)
                .body("status", equalTo("CONCLUIDO"));
    }

    // ─── Épico 7 — Avaliação ──────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("14 · Cliente avalia o prestador (1-5 estrelas)")
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
                .body("nota", equalTo(5));
    }

    @Test @Order(15)
    @DisplayName("15 · Prestador avalia o cliente")
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
                .body("nota", equalTo(5));
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
}
