package br.com.conectapet.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Tira os espacos da senha de app do Gmail.
 *
 * O Google mostra a senha em quatro grupos de quatro — "abcd efgh ijkl mnop" —
 * e e assim que ela vai parar na area de transferencia. Mas o que ele guarda
 * sao os 16 caracteres sem espaco: mandando com espaco, a autenticacao falha.
 *
 * A falha nao ajuda ninguem. O servidor responde "Username and Password not
 * accepted", que manda procurar o erro na conta, na senha, na verificacao em
 * duas etapas — em tudo, menos em tres espacos invisiveis colados junto.
 * Aconteceu aqui: a variavel entrou com 19 caracteres.
 *
 * Mexer numa senha automaticamente e normalmente errado, porque senha de gente
 * pode ter espaco de proposito. Por isso a condicao e estreita: so quando o
 * valor tem EXATAMENTE a forma da senha de app do Gmail — quatro grupos de
 * quatro letras minusculas separados por um espaco. Nenhuma senha escolhida
 * por uma pessoa cai nesse molde por acidente, e o que sobra depois de tirar
 * os espacos e exatamente o que o Google espera receber.
 */
@Component
public class SenhaAppGmail implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SenhaAppGmail.class);

    /** "abcd efgh ijkl mnop", e nada mais. */
    private static final Pattern FORMA = Pattern.compile("^[a-z]{4} [a-z]{4} [a-z]{4} [a-z]{4}$");

    @Override
    public Object postProcessAfterInitialization(Object bean, String nome) throws BeansException {
        if (bean instanceof JavaMailSenderImpl envio) {
            String senha = envio.getPassword();
            if (senha != null && FORMA.matcher(senha).matches()) {
                envio.setPassword(senha.replace(" ", ""));
                log.info("SMTP_SENHA veio no formato que o Google exibe, com espacos. "
                       + "Espacos removidos — o Gmail espera os 16 caracteres corridos.");
            }
        }
        return bean;
    }
}
