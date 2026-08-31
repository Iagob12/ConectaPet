package br.com.conectapet.comum;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recusa subir com configuracao de desenvolvimento fora do desenvolvimento.
 *
 * Existe por causa de uma classe de defeito que ja apareceu duas vezes neste
 * projeto: coisas que funcionam em dev, falham em producao, e falham CALADAS.
 * Nenhuma delas dava erro na subida — o servidor ficava de pe, as telas
 * abriam, e o que quebrava so aparecia com cliente na frente.
 *
 * O criterio para entrar nesta lista e esse: se o padrao errado nao produz erro
 * visivel, ele precisa impedir a subida. Ficar de pe funcionando pela metade e
 * pior do que nao subir, porque ninguem vai investigar um servico que
 * responde 200.
 *
 * Nao roda em dev nem em test, onde os valores locais sao justamente os certos.
 */
@Configuration
@Profile("!dev & !test")
public class ValidacaoConfiguracao {

    private static final Logger log = LoggerFactory.getLogger(ValidacaoConfiguracao.class);

    private final String urlPublicaTag;
    private final String urlSite;
    private final String corsOrigens;
    private final boolean cookieSeguro;
    private final boolean seedHabilitado;
    private final boolean logConteudo;
    private final String provedorEmail;
    private final String smtpHost;
    private final String smtpUsuario;
    private final String smtpSenha;
    private final String remetente;

    public ValidacaoConfiguracao(
            @Value("${conectapet.tag.url-publica}") String urlPublicaTag,
            @Value("${conectapet.site.url}") String urlSite,
            @Value("${conectapet.cors.origens}") String corsOrigens,
            @Value("${conectapet.cookie.seguro:true}") boolean cookieSeguro,
            @Value("${conectapet.seed.habilitado:false}") boolean seedHabilitado,
            @Value("${conectapet.notificacao.log-conteudo:false}") boolean logConteudo,
            @Value("${conectapet.email.provedor:log}") String provedorEmail,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.username:}") String smtpUsuario,
            @Value("${spring.mail.password:}") String smtpSenha,
            @Value("${conectapet.email.remetente:}") String remetente) {
        this.urlPublicaTag = urlPublicaTag;
        this.urlSite = urlSite;
        this.corsOrigens = corsOrigens;
        this.cookieSeguro = cookieSeguro;
        this.seedHabilitado = seedHabilitado;
        this.logConteudo = logConteudo;
        this.provedorEmail = provedorEmail;
        this.smtpHost = smtpHost;
        this.smtpUsuario = smtpUsuario;
        this.smtpSenha = smtpSenha;
        this.remetente = remetente;
    }

    @PostConstruct
    void verificar() {
        List<String> erros = problemas();
        if (!erros.isEmpty()) {
            throw new IllegalStateException(
                    "Configuracao insegura para este ambiente:\n  - " + String.join("\n  - ", erros));
        }
        if ("log".equalsIgnoreCase(provedorEmail)) {
            // Aviso, nao erro: da para subir de proposito sem provedor durante
            // uma migracao. Mas quem sobe assim precisa saber que ninguem sera
            // avisado quando a tag de um pet perdido for lida.
            log.warn("EMAIL_PROVEDOR=log: nenhuma notificacao sera entregue. "
                   + "O tutor nao recebe aviso de leitura da tag, nem link de recuperar senha.");
        }
    }

    /** Separado do verificar() para o teste poder ler a lista sem subir contexto. */
    public List<String> problemas() {
        List<String> erros = new ArrayList<>();

        // Gravado no chip e entregue ao cliente: erro aqui nao tem conserto por
        // software, so regravando ou trocando a tag.
        if (local(urlPublicaTag)) {
            erros.add("conectapet.tag.url-publica aponta para a maquina local ("
                    + urlPublicaTag + "). Toda tag gravada com este endereco nasce inutil. "
                    + "Defina URL_PUBLICA_TAG.");
        }

        // Vai nos links de e-mail: sem isso, quem esquece a senha recebe um link
        // que nao abre em lugar nenhum.
        if (local(urlSite)) {
            erros.add("conectapet.site.url aponta para a maquina local (" + urlSite
                    + "). Os links de e-mail chegariam quebrados. Defina URL_SITE.");
        }

        // O pior dos calados: a confirmacao de leitura e um fetch do navegador
        // para a API, de outra origem. Com o CORS errado o navegador bloqueia,
        // o front engole o erro, e o tutor simplesmente nunca e avisado.
        if (local(corsOrigens)) {
            erros.add("conectapet.cors.origens aponta para a maquina local (" + corsOrigens
                    + "). O navegador bloquearia a confirmacao de leitura e o tutor nunca "
                    + "seria avisado, sem nenhum erro visivel. Defina CORS_ORIGENS.");
        }

        if (!cookieSeguro) {
            erros.add("conectapet.cookie.seguro=false: o cookie de sessao trafegaria "
                    + "fora de HTTPS.");
        }

        if (seedHabilitado) {
            erros.add("conectapet.seed.habilitado=true: o seed imprime codigos de ativacao "
                    + "no console.");
        }

        if (logConteudo) {
            erros.add("conectapet.notificacao.log-conteudo=true: o corpo dos e-mails vai "
                    + "para o log, e ele carrega o link de redefinir senha.");
        }


        // SMTP escolhido, mas sem como falar com o servidor. Isto e erro, e nao
        // aviso, porque a falha acontece longe daqui: a aplicacao sobe, o
        // usuario pede "esqueci a senha", a notificacao entra na fila, e so na
        // hora do envio a conexao falha. A pessoa fica esperando um e-mail que
        // nunca vai chegar, e o unico sinal e uma excecao no log do servidor.
        if ("smtp".equalsIgnoreCase(provedorEmail)) {
            if (smtpHost.isBlank()) {
                erros.add("EMAIL_PROVEDOR=smtp mas SMTP_HOST esta vazio. "
                        + "Para o Gmail: smtp.gmail.com");
            }
            if (smtpUsuario.isBlank()) {
                erros.add("EMAIL_PROVEDOR=smtp mas SMTP_USUARIO esta vazio.");
            }
            if (smtpSenha.isBlank()) {
                erros.add("EMAIL_PROVEDOR=smtp mas SMTP_SENHA esta vazia. "
                        + "No Gmail e uma senha de app, nao a senha da conta.");
            }
            if (remetente.isBlank()) {
                erros.add("EMAIL_PROVEDOR=smtp mas EMAIL_REMETENTE esta vazio.");
            } else if (ehGmail() && !remetente.toLowerCase(Locale.ROOT).contains(smtpUsuario.toLowerCase(Locale.ROOT))) {
                // O Gmail recusa ou reescreve um From que nao seja a conta
                // autenticada. Sem esta checagem o e-mail sai com um remetente
                // diferente do configurado, ou nao sai, e o motivo nao e obvio.
                erros.add("No Gmail, EMAIL_REMETENTE precisa usar o endereco de SMTP_USUARIO ("
                        + smtpUsuario + "). O Gmail recusa remetente que nao seja a conta autenticada.");
            }
        }

        return erros;
    }

    /** Cobre localhost, 127.0.0.1 e a forma sem host de quem esqueceu de trocar. */
    private boolean ehGmail() {
        return smtpHost.toLowerCase(Locale.ROOT).contains("gmail.com");
    }

    private boolean local(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        String v = valor.toLowerCase();
        return v.contains("localhost") || v.contains("127.0.0.1") || v.contains("0.0.0.0");
    }
}
