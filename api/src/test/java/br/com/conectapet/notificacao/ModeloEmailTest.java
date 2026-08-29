package br.com.conectapet.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Texto de e-mail e a unica parte do sistema que ninguem revisa depois que sai.
 * O que este teste segura sao as promessas que o texto faz e os segredos que
 * ele nao pode carregar.
 */
class ModeloEmailTest {

    private final ModeloEmail modelos =
            new ModeloEmail(new ObjectMapper(), "https://conectapet.com.br");

    private ModeloEmail.Mensagem montar(Notificacao.Tipo tipo, String conteudo) {
        Notificacao n = new Notificacao();
        n.setTipo(tipo);
        n.setDestinatario("tutora@exemplo.com");
        n.setConteudo(conteudo);
        return modelos.montar(n);
    }

    @Test
    @DisplayName("leitura comum e factual, sem prometer resgate")
    void leituraComum() {
        var m = montar(Notificacao.Tipo.LEITURA_TAG, """
                {"petNome":"Thor","petUuid":"abc","ocorridaEm":"2026-08-29T14:05:00Z",
                 "temLocalizacao":false,"temMensagem":false,"modoPerdido":false}""");

        assertThat(m.assunto()).isEqualTo("A página do Thor foi aberta");
        // O aviso dispara ate quando o dono testa a propria tag. Prometer
        // "seu pet foi encontrado" toda vez ensina a ignorar o e-mail.
        assertThat(m.texto()).doesNotContain("encontrado");
        assertThat(m.texto()).contains("Se foi você testando a tag");
    }

    @Test
    @DisplayName("com modo perdido o assunto muda de tom")
    void leituraComModoPerdido() {
        var m = montar(Notificacao.Tipo.LEITURA_TAG, """
                {"petNome":"Thor","petUuid":"abc","ocorridaEm":"2026-08-29T14:05:00Z",
                 "temLocalizacao":true,"temMensagem":true,"modoPerdido":true}""");

        assertThat(m.assunto()).isEqualTo("Alguém acabou de ler a tag do Thor");
        assertThat(m.texto()).contains("enviou a localização e deixou um recado");
        // Sem o convite a testar: aqui a pessoa nao esta testando nada.
        assertThat(m.texto()).doesNotContain("testando");
    }

    @Test
    @DisplayName("a data sai legivel, no fuso de Sao Paulo")
    void dataLegivel() {
        var m = montar(Notificacao.Tipo.LEITURA_TAG,
                "{\"petNome\":\"Thor\",\"ocorridaEm\":\"2026-08-29T14:05:00Z\"}");
        // 14:05 UTC = 11h05 em Sao Paulo
        assertThat(m.texto()).contains("29 de agosto às 11h05");
    }

    @Test
    @DisplayName("o codigo de transferencia nunca vai no e-mail")
    void transferenciaSemCodigo() {
        var m = montar(Notificacao.Tipo.TRANSFERENCIA_SOLICITADA,
                "{\"tagUuid\":\"tag-123\",\"validadeMinutos\":15}");

        assertThat(m.texto())
                .contains("o código não vai neste e-mail")
                .contains("15 minutos")
                .contains("https://conectapet.com.br/app/tag/tag-123");
        // O e-mail existe para o caso de NAO ter sido o dono.
        assertThat(m.texto()).contains("Se não foi, cancele agora");
    }

    @Test
    @DisplayName("reset de senha diz o prazo em palavras e o que fazer se nao foi voce")
    void resetSenha() {
        var m = montar(Notificacao.Tipo.RESET_SENHA, """
                {"nome":"Renata Souza","link":"https://conectapet.com.br/redefinir-senha?token=xyz",
                 "validadeMinutos":60}""");

        assertThat(m.assunto()).isEqualTo("Criar uma nova senha na ConectaPet");
        assertThat(m.texto())
                .contains("Olá, Renata!")           // primeiro nome, nao o nome inteiro
                .contains("vale por 1 hora")        // nao "60 minutos"
                .contains("token=xyz")
                .contains("Se não foi você que pediu");
    }

    @Test
    @DisplayName("verificacao deixa claro que a tag ja funciona")
    void verificacaoEmail() {
        var m = montar(Notificacao.Tipo.VERIFICACAO_EMAIL,
                "{\"nome\":\"Bruno\",\"link\":\"https://conectapet.com.br/confirmar-email?token=abc\"}");

        assertThat(m.texto())
                .contains("Olá, Bruno!")
                // Sem isto a pessoa acha que o pet esta desprotegido ate clicar.
                .contains("Sua tag já funciona normalmente");
    }

    @Test
    @DisplayName("o link aparece como texto, nao so dentro do botao")
    void linkVisivelNoHtml() {
        var m = montar(Notificacao.Tipo.RESET_SENHA,
                "{\"nome\":\"Ana\",\"link\":\"https://conectapet.com.br/redefinir-senha?token=xyz\",\"validadeMinutos\":60}");

        // Cliente que bloqueia HTML apaga o botao; sem a URL escrita a pessoa
        // fica sem saida.
        assertThat(m.html()).contains("href=\"https://conectapet.com.br/redefinir-senha?token=xyz\"");
        assertThat(m.html().replace("href=\"https://conectapet.com.br/redefinir-senha?token=xyz\"", ""))
                .contains("https://conectapet.com.br/redefinir-senha?token=xyz");
    }

    @Test
    @DisplayName("nome de pet com HTML nao escapa para dentro da mensagem")
    void escapaNomeDoPet() {
        var m = montar(Notificacao.Tipo.LEITURA_TAG,
                "{\"petNome\":\"<script>alerta</script>\",\"petUuid\":\"x\"}");

        assertThat(m.html()).doesNotContain("<script>");
        assertThat(m.html()).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("conteudo ausente ou ilegivel nao derruba o envio")
    void conteudoRuim() {
        assertThat(montar(Notificacao.Tipo.LEITURA_TAG, null).assunto())
                .isEqualTo("A página do seu pet foi aberta");
        assertThat(montar(Notificacao.Tipo.RESET_SENHA, "{isso nao e json").texto())
                .contains("Olá!");
    }

    @Test
    @DisplayName("todo tipo tem texto, assunto e rodape")
    void todosOsTipos() {
        for (Notificacao.Tipo tipo : Notificacao.Tipo.values()) {
            var m = montar(tipo, "{}");
            assertThat(m.assunto()).as("assunto de %s", tipo).isNotBlank();
            assertThat(m.texto()).as("texto de %s", tipo).isNotBlank()
                    .contains("ConectaPet · Aproxima e Protege");
            assertThat(m.html()).as("html de %s", tipo).contains("<!doctype html>");
        }
    }
}
