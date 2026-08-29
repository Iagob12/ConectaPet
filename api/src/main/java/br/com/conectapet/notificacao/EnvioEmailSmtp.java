package br.com.conectapet.notificacao;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Envio real, por SMTP.
 *
 * Serve para qualquer provedor que fale SMTP — SES, Resend, Postmark, ou o
 * servidor do proprio dominio. A escolha entra por configuracao, nao por
 * codigo: trocar de provedor e trocar host, usuario e senha.
 *
 * Este bean e o stub de log sao mutuamente exclusivos. O mapa de canais em
 * {@link NotificacaoServico} e montado com um canal por chave, entao dois beans
 * para EMAIL derrubariam a aplicacao na subida — o que e melhor do que subir
 * com um deles escolhido por sorteio.
 *
 * O padrao continua sendo o log. Ligar envio de verdade e uma decisao
 * explicita: um ambiente de teste que herdasse SMTP por engano mandaria
 * "alguem leu a tag do seu pet" para clientes de verdade.
 */
@Component
@ConditionalOnProperty(name = "conectapet.email.provedor", havingValue = "smtp")
public class EnvioEmailSmtp implements CanalEnvio {

    private static final Logger log = LoggerFactory.getLogger(EnvioEmailSmtp.class);

    private final JavaMailSender remetente;
    private final ModeloEmail modelos;
    private final String de;
    private final String responderPara;

    public EnvioEmailSmtp(JavaMailSender remetente, ModeloEmail modelos,
                          @Value("${conectapet.email.remetente}") String de,
                          @Value("${conectapet.email.responder-para:}") String responderPara) {
        this.remetente = remetente;
        this.modelos = modelos;
        this.de = de;
        this.responderPara = responderPara;
    }

    @Override
    public Notificacao.Canal canal() {
        return Notificacao.Canal.EMAIL;
    }

    /**
     * Excecao sobe de proposito: quem chama registra a falha, conta a tentativa
     * e reagenda com espera crescente. Engolir aqui marcaria como enviada uma
     * mensagem que nunca saiu.
     */
    @Override
    public void enviar(Notificacao n) throws Exception {
        ModeloEmail.Mensagem m = modelos.montar(n);

        MimeMessage mime = remetente.createMimeMessage();
        // multipart/alternative: o cliente escolhe. Quem bloqueia HTML fica com
        // o texto puro, que foi escrito para bastar sozinho.
        MimeMessageHelper ajuda = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

        ajuda.setFrom(de);
        ajuda.setTo(n.getDestinatario());
        ajuda.setSubject(m.assunto());
        ajuda.setText(m.texto(), m.html());
        if (!responderPara.isBlank()) {
            ajuda.setReplyTo(responderPara);
        }

        remetente.send(mime);
        // O destinatario nao entra em log em nivel INFO.
        log.info("E-mail enviado. tipo={} notificacao={}", n.getTipo(), n.getId());
    }
}
