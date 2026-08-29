package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Object storage dublado, falando HTTP de verdade.
 *
 * Sem bucket contratado, o que se pode medir e exatamente o que importa: qual
 * chave e escrita, o que volta quando o objeto nao existe, e o que acontece
 * quando o servico responde erro. Um dublê de objeto (mock do S3Client) nao
 * pegaria nada disso — ele confirmaria apenas que chamei os metodos que eu
 * mesmo escolhi chamar.
 */
class ArmazenamentoS3Test {

    private HttpServer servidor;
    private ArmazenamentoS3 armazenamento;

    /** Objetos gravados: caminho -> bytes. */
    private final Map<String, byte[]> objetos = new HashMap<>();
    /** Tudo o que o servidor viu, para conferir metodo e caminho. */
    private final List<String> requisicoes = new CopyOnWriteArrayList<>();
    /** Quando > 0, o servidor responde este status em vez de operar. */
    private volatile int falharCom = 0;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String metodo = troca.getRequestMethod();
            String caminho = troca.getRequestURI().getPath();
            requisicoes.add(metodo + " " + caminho);

            if (falharCom > 0) {
                troca.sendResponseHeaders(falharCom, -1);
                troca.close();
                return;
            }

            switch (metodo) {
                case "PUT" -> {
                    objetos.put(caminho, troca.getRequestBody().readAllBytes());
                    troca.sendResponseHeaders(200, -1);
                }
                case "GET" -> {
                    byte[] corpo = objetos.get(caminho);
                    if (corpo == null) {
                        troca.sendResponseHeaders(404, -1);
                    } else {
                        troca.sendResponseHeaders(200, corpo.length);
                        troca.getResponseBody().write(corpo);
                    }
                }
                case "DELETE" -> {
                    objetos.remove(caminho);
                    troca.sendResponseHeaders(204, -1);
                }
                default -> troca.sendResponseHeaders(405, -1);
            }
            troca.close();
        });
        servidor.start();

        // A MESMA fabrica que a producao usa: um cliente montado a parte aqui
        // poderia divergir da configuracao real sem ninguem notar.
        S3Client cliente = ArmazenamentoS3.criarCliente(
                "auto", "http://127.0.0.1:" + servidor.getAddress().getPort(), "chave", "segredo");

        armazenamento = new ArmazenamentoS3(cliente, "fotos-conectapet");
    }

    @AfterEach
    void derrubar() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("guarda e le de volta os mesmos bytes")
    void idaEVolta() {
        byte[] imagem = "conteudo-da-foto".getBytes(StandardCharsets.UTF_8);
        armazenamento.guardar("abc123", ArmazenamentoFotos.Variante.MEDIA, imagem);

        // .orElseThrow antes de comparar: Optional.contains usa equals, e para
        // array isso e identidade de referencia — passaria a impressao de
        // comparar conteudo sem comparar nada.
        assertThat(armazenamento.ler("abc123", ArmazenamentoFotos.Variante.MEDIA).orElseThrow())
                .isEqualTo(imagem);
    }

    @Test
    @DisplayName("a chave segue o mesmo layout do disco: uma pasta por foto")
    void layoutDaChave() {
        armazenamento.guardar("abc123", ArmazenamentoFotos.Variante.PEQUENA, new byte[] { 1 });

        // Trocar o layout entre as duas implementacoes tornaria a migracao de
        // disco para bucket uma reescrita de chaves.
        assertThat(objetos).containsOnlyKeys("/fotos-conectapet/abc123/p.jpg");
    }

    @Test
    @DisplayName("objeto ausente devolve vazio, sem estourar")
    void ausenteDevolveVazio() {
        assertThat(armazenamento.ler("nunca-gravada", ArmazenamentoFotos.Variante.MEDIA))
                .isEmpty();
    }

    @Test
    @DisplayName("falha de leitura que NAO e 404 estoura em vez de virar vazio")
    void erroDeLeituraNaoViraAusente() {
        armazenamento.guardar("abc123", ArmazenamentoFotos.Variante.MEDIA, new byte[] { 1 });
        falharCom = 500;

        // Tratar erro de rede como "nao existe" faria a foto sumir da tela
        // durante uma instabilidade, e o tutor concluiria que ela foi apagada.
        assertThatThrownBy(() -> armazenamento.ler("abc123", ArmazenamentoFotos.Variante.MEDIA))
                .isNotInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("falha de escrita vira erro para quem chamou")
    void erroDeEscritaSobe() {
        falharCom = 503;

        // Sucesso silencioso deixaria o pet com um perfil que promete foto e
        // devolve nada.
        assertThatThrownBy(() -> armazenamento.guardar(
                "abc123", ArmazenamentoFotos.Variante.MEDIA, new byte[] { 1 }))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("apagar remove todas as variantes")
    void apagaTudo() {
        for (var v : ArmazenamentoFotos.Variante.values()) {
            armazenamento.guardar("abc123", v, new byte[] { 1 });
        }
        assertThat(objetos).hasSize(3);

        armazenamento.apagar("abc123");

        // Sobrar a variante publica seria o pior caso: o tutor apaga a foto e
        // ela continua servida a quem le a tag.
        assertThat(objetos).isEmpty();
    }

    @Test
    @DisplayName("apagar nao para na primeira falha")
    void apagarSegueApesarDeFalha() {
        for (var v : ArmazenamentoFotos.Variante.values()) {
            armazenamento.guardar("abc123", v, new byte[] { 1 });
        }
        requisicoes.clear();
        falharCom = 500;

        armazenamento.apagar("abc123");

        // As tres variantes sao tentadas, mesmo com todas falhando. Conta-se
        // caminhos distintos, e nao requisicoes: o SDK repete cada chamada por
        // conta propria diante de um 5xx, e travar o numero de tentativas
        // seria travar a politica de retry da biblioteca, nao o meu codigo.
        assertThat(requisicoes).allSatisfy(r -> assertThat(r).startsWith("DELETE"));
        assertThat(requisicoes.stream().distinct().toList()).hasSize(3);
    }

    @Test
    @DisplayName("nada aqui devolve URL: os bytes voltam para a API")
    void naoExpoeUrl() {
        // O bucket e privado. Se algum dia esta classe passar a devolver
        // endereco, a foto escaparia da regra de visibilidade do perfil.
        for (var metodo : ArmazenamentoS3.class.getDeclaredMethods()) {
            assertThat(metodo.getReturnType().getName())
                    .as("metodo %s", metodo.getName())
                    .doesNotContain("URL")
                    .doesNotContain("URI");
        }
    }
}
