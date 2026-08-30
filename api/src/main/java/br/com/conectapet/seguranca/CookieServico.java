package br.com.conectapet.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Sessao em cookie HttpOnly, nunca em localStorage: qualquer XSS levaria junto
 * os dados de contato de todos os pets do tutor.
 *
 * SameSite=Lax, e SameSite=None esta proibido — inclusive so em preview, que e
 * o tipo de configuracao que vaza para producao.
 *
 * Lax significa que o navegador NAO manda estes cookies para outro site. Hoje,
 * em producao, o site (vercel.app) e a API (onrender.com) sao sites diferentes,
 * e isso funciona apenas porque quem fala com a API autenticada e o servidor do
 * site — nao o navegador. As duas unicas chamadas que o navegador faz direto a
 * API sao publicas: a foto e a confirmacao de leitura.
 *
 * A consequencia pratica, para quem for mexer nisto: uma chamada autenticada
 * feita DO NAVEGADOR direto para a API vai falhar em silencio enquanto os dois
 * estiverem em dominios-raiz distintos. O cookie simplesmente nao e enviado, e
 * a resposta e um 401 sem causa aparente. O caminho certo e passar pelo BFF,
 * como todo o resto do painel ja faz — nao afrouxar para SameSite=None.
 */
@Service
public class CookieServico {

    public static final String COOKIE_SESSAO = "cp_sessao";
    public static final String COOKIE_REFRESH = "cp_refresh";

    private final String dominio;
    private final boolean seguro;
    private final String caminhoRefresh;

    public CookieServico(@Value("${conectapet.cookie.dominio:}") String dominio,
                         @Value("${conectapet.cookie.seguro:true}") boolean seguro,
                         @Value("${conectapet.cookie.caminho-refresh:/}") String caminhoRefresh) {
        this.dominio = dominio;
        this.seguro = seguro;
        this.caminhoRefresh = caminhoRefresh;
    }

    public ResponseCookie sessao(String token, Duration duracao) {
        return montar(COOKIE_SESSAO, token, duracao, "/");
    }

    /**
     * Escopo do refresh.
     *
     * Era "/api/auth", para o token longo so trafegar na rota que o consome.
     * Com o site servido por um BFF isso quebra a sessao sem proteger nada:
     * quem renova e o servidor do site, durante o render de uma pagina em /app,
     * e o navegador nunca manda um cookie escopado em /api/auth para la. O
     * efeito era a sessao morrer junto com o access token, em 15 minutos, sem
     * chance de renovar.
     *
     * O que de fato protege o token segue de pe nos dois casos: HttpOnly,
     * Secure e SameSite=Lax. Fica configuravel para um deploy sem BFF poder
     * voltar ao escopo estreito.
     */
    public ResponseCookie refresh(String token, Duration duracao) {
        return montar(COOKIE_REFRESH, token, duracao, caminhoRefresh);
    }

    /** O logout precisa apagar o cookie no mesmo caminho em que ele foi posto. */
    public ResponseCookie limparRefresh() {
        return montar(COOKIE_REFRESH, "", Duration.ZERO, caminhoRefresh);
    }

    public ResponseCookie limpar(String nome, String caminho) {
        return montar(nome, "", Duration.ZERO, caminho);
    }

    private ResponseCookie montar(String nome, String valor, Duration duracao, String caminho) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite("Lax")
                .path(caminho)
                .maxAge(duracao);
        if (dominio != null && !dominio.isBlank()) {
            b.domain(dominio);
        }
        return b.build();
    }
}
