package br.com.conectapet.notificacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A condicao precisa ser estreita: mexer numa senha por conta propria so se
 * justifica quando nao ha duvida do que ela e.
 */
class SenhaAppGmailTest {

    private final SenhaAppGmail processador = new SenhaAppGmail();

    private String depoisDe(String senha) {
        JavaMailSenderImpl envio = new JavaMailSenderImpl();
        envio.setPassword(senha);
        processador.postProcessAfterInitialization(envio, "mailSender");
        return envio.getPassword();
    }

    @Test
    @DisplayName("a forma que o Google exibe perde os espacos")
    void formaDoGoogle() {
        assertThat(depoisDe("abcd efgh ijkl mnop")).isEqualTo("abcdefghijklmnop");
    }

    @Test
    @DisplayName("ja sem espacos, nao mexe")
    void jaCorrida() {
        assertThat(depoisDe("abcdefghijklmnop")).isEqualTo("abcdefghijklmnop");
    }

    @Test
    @DisplayName("senha de pessoa com espaco continua intacta")
    void senhaDeGenteComEspaco() {
        // O risco de tirar espaco automaticamente e este. A forma exigida —
        // quatro grupos de quatro letras minusculas — nao acontece por acaso.
        for (String s : new String[]{
                "meu cachorro corre muito",   // quatro palavras, tamanhos errados
                "Abcd efgh ijkl mnop",        // maiuscula
                "abcd efgh ijkl mno1",        // digito
                "abcd  efgh ijkl mnop",       // dois espacos
                "abcd efgh ijkl",             // tres grupos
                "abcd efgh ijkl mnop qrst",   // cinco grupos
                " abcd efgh ijkl mnop"}) {    // espaco na ponta
            assertThat(depoisDe(s)).as(s).isEqualTo(s);
        }
    }

    @Test
    @DisplayName("sem senha configurada, nao quebra")
    void semSenha() {
        assertThat(depoisDe(null)).isNull();
    }

    @Test
    @DisplayName("ignora qualquer bean que nao seja o remetente de e-mail")
    void outroBean() {
        Object qualquer = new Object();
        assertThat(processador.postProcessAfterInitialization(qualquer, "x")).isSameAs(qualquer);
    }
}
