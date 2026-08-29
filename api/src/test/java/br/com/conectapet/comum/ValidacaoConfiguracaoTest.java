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
                true, false, false, "smtp");
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
                "https://conectapet.com.br", true, false, false, "smtp");

        assertThat(v.problemas()).singleElement().asString()
                .contains("url-publica")
                .contains("URL_PUBLICA_TAG");
    }

    @Test
    @DisplayName("CORS local e recusado: e a falha que nao aparece")
    void corsLocal() {
        var v = new ValidacaoConfiguracao("https://conectapet.com.br/p/", "https://conectapet.com.br",
                "http://localhost:4321", true, false, false, "smtp");

        // A confirmacao de leitura e um fetch do navegador para outra origem.
        // Bloqueada, o tutor nunca e avisado — e nada no servidor registra erro.
        assertThat(v.problemas()).singleElement().asString().contains("nunca");
    }

    @Test
    @DisplayName("127.0.0.1 conta como local tanto quanto localhost")
    void enderecoNumerico() {
        var v = new ValidacaoConfiguracao("http://127.0.0.1:4321/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", true, false, false, "smtp");
        assertThat(v.problemas()).hasSize(1);
    }

    @Test
    @DisplayName("cookie sem Secure, seed ligado e corpo de e-mail em log sao recusados")
    void demaisRiscos() {
        var v = new ValidacaoConfiguracao("https://conectapet.com.br/p/", "https://conectapet.com.br",
                "https://conectapet.com.br", false, true, true, "smtp");

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
                "https://conectapet.com.br", true, false, false, "log");

        // Da para subir de proposito sem provedor durante uma migracao; o aviso
        // no log e que nao pode faltar.
        assertThat(v.problemas()).isEmpty();
    }

    @Test
    @DisplayName("todos os erros aparecem juntos, nao um por subida")
    void reportaTudoDeUmaVez() {
        var v = new ValidacaoConfiguracao("http://localhost/p/", "http://localhost",
                "http://localhost", false, true, true, "log");

        // Corrigir um, subir, descobrir o proximo, repetir — seria um ciclo de
        // deploy por variavel errada.
        assertThat(v.problemas()).hasSize(6);
    }
}
