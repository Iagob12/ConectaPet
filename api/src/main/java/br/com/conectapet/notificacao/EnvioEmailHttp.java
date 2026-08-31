package br.com.conectapet.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envio por API HTTP, em vez de SMTP.
 *
 * Existe por uma restricao da hospedagem, nao por preferencia: desde setembro
 * de 2025 o plano gratuito do Render bloqueia as portas 25, 465 e 587, e a
 * conexao SMTP nem chega a ser recusada — ela expira. O erro que aparece
 * ("Couldn't connect to host") manda procurar em senha, remetente e
 * verificacao em duas etapas, e nenhum dos tres tem a ver.
 *
 * A porta 443 nao e bloqueada. Este canal fala com o Brevo, que entrega 300
 * e-mails por dia no plano gratuito e — o que decide a escolha — aceita
 * verificar um endereco @gmail.com como remetente, sem exigir dominio
 * proprio. O Resend, por exemplo, so entrega para o proprio dono da conta
 * enquanto nao houver dominio verificado, o que nao serve para avisar tutores.
 *
 * A excecao sobe de proposito, como no {@link EnvioEmailSmtp}: quem chama
 * registra a falha, conta a tentativa e reagenda com espera crescente. Engolir
 * aqui marcaria como enviada uma mensagem que nunca saiu.
 */
@Component
@ConditionalOnProperty(name = "conectapet.email.provedor", havingValue = "http")
public class EnvioEmailHttp implements CanalEnvio {

    private static final Logger log = LoggerFactory.getLogger(EnvioEmailHttp.class);

    /** "ConectaPet <contato@exemplo.com>" — o mesmo formato aceito pelo SMTP. */
    private static final Pattern REMETENTE = Pattern.compile("^\s*(.*?)\s*<\s*(.+?)\s*>\s*$");

    private final HttpClient cliente;
    private final ObjectMapper json = new ObjectMapper();
    private final ModeloEmail modelos;
    private final String endereco;
    private final String chave;
    private final String remetenteNome;
    private final String remetenteEmail;
    private final String responderPara;
    private final Duration tempoLimite;

    public EnvioEmailHttp(ModeloEmail modelos,
                          @Value("${conectapet.email.http.url}") String endereco,
                          @Value("${conectapet.email.http.chave}") String chave,
                          @Value("${conectapet.email.remetente}") String remetente,
                          @Value("${conectapet.email.responder-para:}") String responderPara,
                          @Value("${conectapet.email.http.tempo-limite:PT10S}") Duration tempoLimite) {
        this.modelos = modelos;
        this.endereco = endereco;
        this.chave = chave;
        this.responderPara = responderPara;
        this.tempoLimite = tempoLimite;

        Matcher m = REMETENTE.matcher(remetente);
        if (m.matches()) {
            this.remetenteNome = m.group(1).isBlank() ? "ConectaPet" : m.group(1);
            this.remetenteEmail = m.group(2);
        } else {
            // So o endereco, sem nome. Aceito: o SMTP tambem aceita.
            this.remetenteNome = "ConectaPet";
            this.remetenteEmail = remetente.trim();
        }

        // O mesmo teto do SMTP, e pelo mesmo motivo: a fila reprocessa com
        // espera crescente, e uma conexao pendurada travaria o lote inteiro.
        this.cliente = HttpClient.newBuilder().connectTimeout(tempoLimite).build();
    }

    @Override
    public Notificacao.Canal canal() {
        return Notificacao.Canal.EMAIL;
    }

    @Override
    public void enviar(Notificacao n) throws Exception {
        ModeloEmail.Mensagem m = modelos.montar(n);
        HttpResponse<String> r = cliente.send(
                HttpRequest.newBuilder(URI.create(endereco))
                        .timeout(tempoLimite)
                        .header("api-key", chave)
                        .header("content-type", "application/json")
                        .header("accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo(n, m), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (r.statusCode() / 100 != 2) {
            // O corpo entra na mensagem porque e onde o provedor diz o motivo
            // — remetente nao verificado, cota estourada, chave invalida. Sem
            // ele sobraria um numero, e a fila registraria "falhou: 400".
            throw new IllegalStateException(
                    "Provedor de e-mail recusou (HTTP " + r.statusCode() + "): " + resumo(r.body()));
        }
        // O destinatario nao entra em log em nivel INFO.
        log.info("E-mail enviado. tipo={} notificacao={}", n.getTipo(), n.getId());
    }

    private String corpo(Notificacao n, ModeloEmail.Mensagem m) throws Exception {
        ObjectNode raiz = json.createObjectNode();
        ObjectNode de = raiz.putObject("sender");
        de.put("name", remetenteNome);
        de.put("email", remetenteEmail);

        ObjectNode para = raiz.putArray("to").addObject();
        para.put("email", n.getDestinatario());

        raiz.put("subject", m.assunto());
        raiz.put("htmlContent", m.html());
        raiz.put("textContent", m.texto());

        if (!responderPara.isBlank()) {
            raiz.putObject("replyTo").put("email", responderPara);
        }
        return json.writeValueAsString(raiz);
    }

    /** Resposta do provedor numa linha so, para caber na mensagem da excecao. */
    private static String resumo(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "(sem corpo na resposta)";
        }
        String s = corpo.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
