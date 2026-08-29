package br.com.conectapet.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacao provisoria: registra em log em vez de enviar.
 *
 * O provedor de e-mail ainda nao foi escolhido. Trocar isto por SES, Resend ou
 * SMTP nao toca em nenhum outro arquivo — e o ponto da interface.
 */
@Component
public class EnvioEmail implements CanalEnvio {

    private static final Logger log = LoggerFactory.getLogger(EnvioEmail.class);

    /**
     * Em desenvolvimento o corpo da mensagem vai para o console.
     *
     * Sem provedor de e-mail configurado, o link de redefinir senha nao chega a
     * lugar nenhum e o fluxo fica intestavel — nem por mim, nem por quem for
     * conferir. Fica atras de propriedade, desligada por padrao, pelo mesmo
     * motivo do seed de tags: o conteudo carrega um passe de entrada na conta.
     */
    private final boolean registrarConteudo;
    private final ModeloEmail modelos;

    public EnvioEmail(
            ModeloEmail modelos,
            @org.springframework.beans.factory.annotation.Value("${conectapet.notificacao.log-conteudo:false}")
            boolean registrarConteudo) {
        this.modelos = modelos;
        this.registrarConteudo = registrarConteudo;
    }

    @Override
    public Notificacao.Canal canal() {
        return Notificacao.Canal.EMAIL;
    }

    @Override
    public void enviar(Notificacao n) {
        // A mensagem e montada aqui, e nao no provedor, para que trocar SES por
        // Resend nao leve junto o texto que o cliente le.
        ModeloEmail.Mensagem m = modelos.montar(n);

        // O destinatario e mascarado: e-mail nao entra em log em nivel INFO.
        log.info("[e-mail simulado] tipo={} para={} assunto=\"{}\"",
                n.getTipo(), mascarar(n.getDestinatario()), m.assunto());

        if (registrarConteudo) {
            log.warn("[e-mail simulado] corpo:\n{}\n<- SO EM DESENVOLVIMENTO", m.texto());
        }
    }

    private String mascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
