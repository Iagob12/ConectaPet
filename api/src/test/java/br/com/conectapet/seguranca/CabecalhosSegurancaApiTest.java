package br.com.conectapet.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CabecalhosSegurancaApiTest {

    @Test
    @DisplayName("toda resposta da API sai sem cache e sem contexto executável")
    void cabecalhosRestritivos() throws Exception {
        var filtro = new CabecalhosSegurancaApi();
        var req = new MockHttpServletRequest("GET", "/api/public/tags/ABC");
        var res = new MockHttpServletResponse();

        filtro.doFilter(req, res, (pedido, resposta) -> resposta.getWriter().write("{}"));

        assertThat(res.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.getHeader("Content-Security-Policy"))
                .contains("default-src 'none'")
                .contains("frame-ancestors 'none'");
        assertThat(res.getHeader("Permissions-Policy"))
                .contains("camera=()")
                .contains("geolocation=()");
    }
}
