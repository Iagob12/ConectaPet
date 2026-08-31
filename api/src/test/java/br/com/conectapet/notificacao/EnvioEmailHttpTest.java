package br.com.conectapet.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contra um servidor HTTP de verdade, e nao um mock.
 *
 * O que interessa aqui e o que sai pela rede: o cabecalho da chave, a forma do
 * JSON e o que acontece quando o provedor recusa. Um mock de HttpClient
 * confirmaria apenas que o metodo foi chamado.
 */
class EnvioEmailHttpTest {

    private HttpServer servidor;
    private final AtomicReference<String> ultimoCorpo = new AtomicReference<>();
    private final AtomicReference<String> ultimaChave = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(201);
    private final AtomicReference<String> resposta = new AtomicReference<>("{\"messageId\":\"<abc>\"}");
    private String url;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v3/smtp/email", troca -> {
            ultimaChave.set(troca.getRequestHeaders().getFirst("api-key"));
            try (InputStream in = troca.getRequestBody()) {
                ultimoCorpo.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] corpo = resposta.get().getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(status.get(), corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        servidor.start();
        url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v3/smtp/email";
    }

    @AfterEach
    void derrubar() {
        servidor.stop(0);
    }

    private EnvioEmailHttp canal(String remetente, String responderPara) {
        return new EnvioEmailHttp(new ModeloEmail(new ObjectMapper(), "https://conectapet.com.br"),
                url, "chave-secreta", remetente, responderPara, Duration.ofSeconds(5));
    }

    private Notificacao notificacao() {
        Notificacao n = new Notificacao();
        n.setTipo(Notificacao.Tipo.RESET_SENHA);
        n.setDestinatario("tutor@exemplo.com");
        n.setConteudo("""
                {"nome":"Bruno","link":"https://conectapet.com.br/x","validadeMinutos":30}""");
        return n;
    }

    @Test
    @DisplayName("manda a chave no cabecalho que o provedor espera")
    void chaveNoCabecalho() throws Exception {
        canal("ConectaPet <contato@conectapet.com.br>", "").enviar(notificacao());
        assertThat(ultimaChave.get()).isEqualTo("chave-secreta");
    }

    @Test
    @DisplayName("separa nome e endereco do remetente")
    void remetenteSeparado() throws Exception {
        canal("ConectaPet <contato@conectapet.com.br>", "").enviar(notificacao());
        JsonNode j = new ObjectMapper().readTree(ultimoCorpo.get());
        assertThat(j.at("/sender/name").asText()).isEqualTo("ConectaPet");
        assertThat(j.at("/sender/email").asText()).isEqualTo("contato@conectapet.com.br");
    }

    @Test
    @DisplayName("remetente sem nome tambem funciona")
    void remetenteSoEndereco() throws Exception {
        canal("contato@conectapet.com.br", "").enviar(notificacao());
        JsonNode j = new ObjectMapper().readTree(ultimoCorpo.get());
        assertThat(j.at("/sender/email").asText()).isEqualTo("contato@conectapet.com.br");
    }

    @Test
    @DisplayName("manda as duas versoes, texto e html")
    void duasVersoes() throws Exception {
        canal("ConectaPet <c@x.com>", "").enviar(notificacao());
        JsonNode j = new ObjectMapper().readTree(ultimoCorpo.get());
        assertThat(j.get("subject").asText()).isNotBlank();
        assertThat(j.get("textContent").asText()).contains("Bruno");
        assertThat(j.get("htmlContent").asText()).contains("<");
        assertThat(j.at("/to/0/email").asText()).isEqualTo("tutor@exemplo.com");
    }

    @Test
    @DisplayName("responder-para so aparece quando configurado")
    void responderPara() throws Exception {
        canal("ConectaPet <c@x.com>", "").enviar(notificacao());
        assertThat(new ObjectMapper().readTree(ultimoCorpo.get()).has("replyTo")).isFalse();

        canal("ConectaPet <c@x.com>", "ajuda@x.com").enviar(notificacao());
        assertThat(new ObjectMapper().readTree(ultimoCorpo.get()).at("/replyTo/email").asText())
                .isEqualTo("ajuda@x.com");
    }

    @Test
    @DisplayName("recusa do provedor vira excecao, com o motivo dele junto")
    void recusaSobe() {
        // Sem o corpo da resposta sobraria um numero, e a fila registraria
        // "falhou: 400" — que nao diz se e cota, chave ou remetente nao
        // verificado. E o remetente nao verificado e o erro mais provavel de
        // quem esta configurando pela primeira vez.
        status.set(400);
        resposta.set("{\"code\":\"invalid_parameter\",\"message\":\"sender not valid\"}");

        assertThatThrownBy(() -> canal("ConectaPet <c@x.com>", "").enviar(notificacao()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("sender not valid");
    }

    @Test
    @DisplayName("a excecao sobe para a fila poder tentar de novo")
    void naoEngoleFalha() {
        status.set(500);
        resposta.set("erro interno");
        assertThatThrownBy(() -> canal("ConectaPet <c@x.com>", "").enviar(notificacao()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("e o canal de e-mail")
    void canalCorreto() {
        assertThat(canal("c@x.com", "").canal()).isEqualTo(Notificacao.Canal.EMAIL);
    }
}
