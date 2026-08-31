package br.com.conectapet.comum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * A configuracao boa passa; cada padrao de desenvolvimento e recusado pelo seu
 * proprio motivo, com a mensagem dizendo qual variavel definir.
 */
class ValidacaoConfiguracaoTest {

    private ValidacaoConfiguracao comTudoCerto() {
        return new ValidacaoConfiguracao(
                "https://conectapet.com.br/p/",
                "https://conectapet.com.br",
                "https://conectapet.com.br",
                true, false, false, "smtp",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");
    }

    @Test
    @DisplayName("configuracao de producao sobe sem reclamar")
    void producaoValida() {
        assertThat(comTudoCerto().problemas()).isEmpty();
    }

    @Test
    @DisplayName("url gravada na tag apontando para local e recusada")
    void urlDaTagLocal() {
        var v = new ValidacaoConfiguracao("http://localhost:4321/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "smtp",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");

        assertThat(v.problemas()).singleElement().asString()
                .contains("url-publica")
                .contains("URL_PUBLICA_TAG");
    }

    @Test
    @DisplayName("CORS local e recusado: e a falha que nao aparece")
    void corsLocal() {
        var v = new ValidacaoConfiguracao("https://conectapet.com.br/p/", "https://conectapet.com.br",
                "http://localhost:4321", true, false, false, "smtp",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");

        // A confirmacao de leitura e um fetch do navegador para outra origem.
        // Bloqueada, o tutor nunca e avisado — e nada no servidor registra erro.
        assertThat(v.problemas()).singleElement().asString().contains("nunca");
    }

    @Test
    @DisplayName("127.0.0.1 conta como local tanto quanto localhost")
    void enderecoNumerico() {
        var v = new ValidacaoConfiguracao("http://127.0.0.1:4321/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "smtp",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");
        assertThat(v.problemas()).hasSize(1);
    }

    @Test
    @DisplayName("cookie sem Secure, seed ligado e corpo de e-mail em log sao recusados")
    void demaisRiscos() {
        var v = new ValidacaoConfiguracao("https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", false, true, true, "smtp",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");

        assertThat(v.problemas()).hasSize(3);
        assertThat(String.join(" ", v.problemas()))
                .contains("cookie.seguro")
                .contains("seed.habilitado")
                .contains("log-conteudo");
    }

    @Test
    @DisplayName("o provedor em log e aviso, nao impedimento")
    void provedorEmLogNaoBloqueia() {
        var v = new ValidacaoConfiguracao("https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "log",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");

        // Da para subir de proposito sem provedor durante uma migracao; o aviso
        // no log e que nao pode faltar.
        assertThat(v.problemas()).isEmpty();
    }

    @Test
    @DisplayName("todos os erros aparecem juntos, nao um por subida")
    void reportaTudoDeUmaVez() {
        var v = new ValidacaoConfiguracao("http://localhost/p/", "http://localhost",
                "http://localhost", false, true, true, "log",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>");

        // Corrigir um, subir, descobrir o proximo, repetir — seria um ciclo de
        // deploy por variavel errada.
        assertThat(v.problemas()).hasSize(6);
    }

    /** Igual ao de producao, mas com os quatro campos de SMTP sob controle. */
    private ValidacaoConfiguracao comSmtp(String host, String usuario, String senha, String remetente) {
        return new ValidacaoConfiguracao(
                "https://conectapet.com.br/p/",
                "https://conectapet.com.br",
                "https://conectapet.com.br",
                true, false, false, "smtp",
                host, usuario, senha, remetente);
    }

    @Test
    @DisplayName("smtp escolhido sem host, usuario ou senha e recusado na subida")
    void smtpIncompleto() {
        // A falha real acontece longe daqui: a aplicacao sobe, a pessoa pede
        // "esqueci a senha", e o e-mail so falha na hora do envio. Ela fica
        // esperando uma mensagem que nunca chega.
        assertThat(comSmtp("", "c@gmail.com", "x", "ConectaPet <c@gmail.com>").problemas())
                .anyMatch(e -> e.contains("SMTP_HOST"));
        assertThat(comSmtp("smtp.gmail.com", "", "x", "ConectaPet <c@gmail.com>").problemas())
                .anyMatch(e -> e.contains("SMTP_USUARIO"));
        assertThat(comSmtp("smtp.gmail.com", "c@gmail.com", "", "ConectaPet <c@gmail.com>").problemas())
                .anyMatch(e -> e.contains("SMTP_SENHA"));
        assertThat(comSmtp("smtp.gmail.com", "c@gmail.com", "x", "").problemas())
                .anyMatch(e -> e.contains("EMAIL_REMETENTE"));
    }

    @Test
    @DisplayName("no Gmail, remetente diferente da conta autenticada e recusado")
    void gmailComRemetenteDeOutraConta() {
        // O Gmail reescreve ou recusa esse envio. Sem a checagem, o e-mail sai
        // com um remetente que ninguem configurou, ou nao sai — e o motivo nao
        // aparece em lugar nenhum.
        assertThat(comSmtp("smtp.gmail.com", "conta@gmail.com", "x",
                           "ConectaPet <contato@outrodominio.com>").problemas())
                .anyMatch(e -> e.contains("EMAIL_REMETENTE"));

        assertThat(comSmtp("smtp.gmail.com", "conta@gmail.com", "x",
                           "ConectaPet <conta@gmail.com>").problemas())
                .isEmpty();
    }

    @Test
    @DisplayName("fora do Gmail, o remetente pode ser outro endereco")
    void outroProvedorAceitaRemetenteProprio() {
        // SES, Resend e Postmark autorizam por dominio verificado, nao pela
        // conta de login: exigir que os dois batam quebraria esses casos.
        assertThat(comSmtp("smtp.resend.com", "resend", "chave",
                           "ConectaPet <nao-responda@conectapet.com.br>").problemas())
                .isEmpty();
    }
}
