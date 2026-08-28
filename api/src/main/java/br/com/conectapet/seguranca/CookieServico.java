package br.com.conectapet.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Sessao em cookie HttpOnly, nunca em localStorage: qualquer XSS levaria junto
 * os dados de contato de todos os pets do tutor.
 *
 * SameSite=Lax funciona porque site e API vivem sob o mesmo dominio-raiz.
 * SameSite=None esta proibido, inclusive so em preview — e o tipo de
 * configuracao que vaza para producao. Previews usam preview-*.conectapet.com.br.
 */
@Service
public class CookieServico {

    public static final String COOKIE_SESSAO = "cp_sessao";
    public static final String COOKIE_REFRESH = "cp_refresh";

    private final String dominio;
    private final boolean seguro;

    public CookieServico(@Value("${conectapet.cookie.dominio:}") String dominio,
                         @Value("${conectapet.cookie.seguro:true}") boolean seguro) {
        this.dominio = dominio;
        this.seguro = seguro;
    }

    public ResponseCookie sessao(String token, Duration duracao) {
        return montar(COOKIE_SESSAO, token, duracao, "/");
    }

    /** Escopo restrito: o refresh so e enviado para a rota que o consome. */
    public ResponseCookie refresh(String token, Duration duracao) {
        return montar(COOKIE_REFRESH, token, duracao, "/api/auth");
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
