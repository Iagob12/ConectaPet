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

    @Override
    public Notificacao.Canal canal() {
        return Notificacao.Canal.EMAIL;
    }

    @Override
    public void enviar(Notificacao n) {
        // O destinatario e mascarado: e-mail nao entra em log em nivel INFO.
        log.info("[e-mail simulado] tipo={} para={}", n.getTipo(), mascarar(n.getDestinatario()));
    }

    private String mascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
