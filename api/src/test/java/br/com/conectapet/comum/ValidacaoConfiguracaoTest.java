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
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");
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
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");

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
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");

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
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");
        assertThat(v.problemas()).hasSize(1);
    }

    @Test
    @DisplayName("cookie sem Secure, seed ligado e corpo de e-mail em log sao recusados")
    void demaisRiscos() {
        var v = new ValidacaoConfiguracao("https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", false, true, true, "smtp",
                "smtp.gmail.com", "conta@gmail.com", "senha-de-app",
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");

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
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");

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
                "ConectaPet <conta@gmail.com>", "chave-http",
                "local", "", "", "", "");

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
                host, usuario, senha, remetente, "chave-http",
                "local", "", "", "", "");
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

    @Test
    @DisplayName("http escolhido sem a chave de API e recusado na subida")
    void httpSemChave() {
        ValidacaoConfiguracao v = new ValidacaoConfiguracao(
                "https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "http",
                "", "", "", "ConectaPet <c@gmail.com>", "",
                "local", "", "", "", "");
        assertThat(v.problemas()).anyMatch(e -> e.contains("EMAIL_HTTP_CHAVE"));
    }

    @Test
    @DisplayName("http com chave e remetente sobe, sem exigir nada de smtp")
    void httpCompleto() {
        // Nenhum campo de SMTP e preenchido aqui de proposito: quem envia por
        // API HTTP nao tem servidor SMTP nenhum para configurar, e exigir isso
        // seria pedir configuracao que nao existe.
        ValidacaoConfiguracao v = new ValidacaoConfiguracao(
                "https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "http",
                "", "", "", "ConectaPet <c@gmail.com>", "chave-de-api",
                "local", "", "", "", "");
        assertThat(v.problemas()).isEmpty();
    }

    /** Igual ao de producao, com os campos de foto sob controle. */
    private ValidacaoConfiguracao comFoto(String modo, String bucket, String chave,
                                          String segredo, String endpoint) {
        return new ValidacaoConfiguracao(
                "https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "http",
                "", "", "", "ConectaPet <c@gmail.com>", "chave-de-api",
                modo, bucket, chave, segredo, endpoint);
    }

    @Test
    @DisplayName("s3 sem bucket, chave, segredo ou endpoint e recusado na subida")
    void s3Incompleto() {
        // Sem isto o ArmazenamentoS3 sobe normal e so falha na hora do upload:
        // o tutor escolhe a foto, espera, e recebe um erro generico — enquanto
        // o problema esta numa variavel em branco desde o deploy.
        final String E = "https://abc.r2.cloudflarestorage.com";
        assertThat(comFoto("s3", "", "k", "s", E).problemas()).anyMatch(x -> x.contains("FOTO_S3_BUCKET"));
        assertThat(comFoto("s3", "b", "", "s", E).problemas()).anyMatch(x -> x.contains("FOTO_S3_CHAVE"));
        assertThat(comFoto("s3", "b", "k", "", E).problemas()).anyMatch(x -> x.contains("FOTO_S3_SEGREDO"));
        assertThat(comFoto("s3", "b", "k", "s", "").problemas()).anyMatch(x -> x.contains("FOTO_S3_ENDPOINT"));
    }

    @Test
    @DisplayName("s3 completo sobe, e local nao exige nada disso")
    void s3CompletoELocal() {
        assertThat(comFoto("s3", "conectapet-fotos", "chave", "segredo",
                           "https://abc.r2.cloudflarestorage.com").problemas()).isEmpty();
        // Em "local" as variaveis de S3 nao existem, e exigi-las seria pedir
        // configuracao para um caminho que nao vai ser usado.
        assertThat(comFoto("local", "", "", "", "").problemas()).isEmpty();
    }
}
