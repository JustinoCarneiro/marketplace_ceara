package com.onda.marketplace.servicerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAiSuggestionServiceTest {

    private static final String URL =
            "https://ai.test/v1beta/models/modelo-teste:generateContent";

    private MockRestServiceServer server;
    private GeminiAiSuggestionService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GeminiAiSuggestionService(builder.build(), "modelo-teste", "chave-secreta");
    }

    private static String resposta(String textoDoModelo) {
        String escapado = textoDoModelo.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escapado + "\"}]}}]}";
    }

    @Test
    void sugestaoValida_devolveDescricaoEFaixa() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "chave-secreta"))
                .andRespond(withSuccess(resposta(
                        "{\"descricao\":\"Reparo de torneira pingando na cozinha\",\"faixaMin\":80,\"faixaMax\":200}"),
                        MediaType.APPLICATION_JSON));

        var r = service.suggest("torneira pingando", "Encanador");

        assertThat(r).isPresent();
        assertThat(r.get().descricaoSugerida()).isEqualTo("Reparo de torneira pingando na cozinha");
        assertThat(r.get().faixaMin()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(r.get().faixaMax()).isEqualByComparingTo(BigDecimal.valueOf(200));
        server.verify();
    }

    @Test
    void chaveVaiNoCabecalho_nuncaNoCorpoNemNaUrl() {
        server.expect(requestTo(URL))
                .andExpect(request -> assertThat(request.getURI().toString()).doesNotContain("chave-secreta"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("chave-secreta"))))
                .andRespond(withSuccess(resposta("{\"descricao\":\"x\",\"faixaMin\":1,\"faixaMax\":2}"),
                        MediaType.APPLICATION_JSON));

        service.suggest("algo", null);
        server.verify();
    }

    @Test
    void descricaoVaziaOuNula_naoChamaOModelo() {
        assertThat(service.suggest(null, "Encanador")).isEmpty();
        assertThat(service.suggest("   ", "Encanador")).isEmpty();
        server.verify(); // nenhuma requisição esperada nem feita
    }

    @Test
    void erroHttp_viraFallbackSemExcecao() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void cotaEsgotada429_viraFallback() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void corpoQueNaoEJson_viraFallback() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void textoDoModeloNaoEJson_viraFallback() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(resposta("Claro! Aqui vai a sugestão..."), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void semCandidatos_porBloqueioDeSeguranca_viraFallback() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                        MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void faixaInvertida_viraFallback() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"x\",\"faixaMin\":500,\"faixaMax\":100}"), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void faixaNegativaOuAbsurda_viraFallback() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"x\",\"faixaMin\":-10,\"faixaMax\":100}"), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"x\",\"faixaMin\":10,\"faixaMax\":99999999}"), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void descricaoVaziaNaResposta_viraFallback() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"  \",\"faixaMin\":10,\"faixaMax\":20}"), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void camposAusentesOuNaoNumericos_viramFallback() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"x\",\"faixaMin\":\"barato\",\"faixaMax\":20}"), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"x\"}"), MediaType.APPLICATION_JSON));
        assertThat(service.suggest("torneira", null)).isEmpty();
    }

    @Test
    void descricaoLongaDemais_ehTruncadaSemQuebrarOFluxo() {
        String longa = "a".repeat(5000);
        server.expect(requestTo(URL)).andRespond(withSuccess(
                resposta("{\"descricao\":\"" + longa + "\",\"faixaMin\":10,\"faixaMax\":20}"),
                MediaType.APPLICATION_JSON));

        var r = service.suggest("torneira", null);

        assertThat(r).isPresent();
        assertThat(r.get().descricaoSugerida().length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void entradaDoUsuarioEhLimitadaAntesDeIrAoModelo() {
        server.expect(requestTo(URL))
                .andExpect(request -> assertThat(
                        request.getBody().toString().length()).isLessThan(8000))
                .andRespond(withSuccess(resposta("{\"descricao\":\"x\",\"faixaMin\":1,\"faixaMax\":2}"),
                        MediaType.APPLICATION_JSON));

        service.suggest("x".repeat(100_000), null);
        server.verify();
    }

    @Test
    void dadosPessoais_saoSanitizadosAntesDoEnvioAoModelo() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[telefone removido]")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[e-mail removido]")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("85988887777"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("joao@gmail.com"))))
                .andRespond(withSuccess(resposta(
                        "{\"descricao\":\"Reparo hidráulico\",\"faixaMin\":80,\"faixaMax\":150}"),
                        MediaType.APPLICATION_JSON));

        var r = service.suggest("Preciso de encanador urgente. Contato: joao@gmail.com ou 85988887777", null);

        assertThat(r).isPresent();
        assertThat(r.get().descricaoSugerida()).isEqualTo("Reparo hidráulico");
        server.verify();
    }

    @Test
    void rateLimitExcedido_retornaEmptySemChamarHttp() {
        var limiter = new AiRateLimiter(1, 10, 100);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test");
        var customServer = MockRestServiceServer.bindTo(builder).build();
        var limitedService = new GeminiAiSuggestionService(builder.build(), limiter, "modelo-teste", "chave");

        // 1ª chamada é permitida e chama HTTP
        customServer.expect(requestTo(URL))
                .andRespond(withSuccess(resposta(
                        "{\"descricao\":\"Reparo ok\",\"faixaMin\":50,\"faixaMax\":100}"),
                        MediaType.APPLICATION_JSON));
        assertThat(limitedService.suggest("conserto", null, "user-1")).isPresent();
        customServer.verify();

        // 2ª chamada no mesmo usuário estoura o limite horário de 1 req -> retorna empty sem chamar HTTP
        assertThat(limitedService.suggest("conserto", null, "user-1")).isEmpty();
    }
}

