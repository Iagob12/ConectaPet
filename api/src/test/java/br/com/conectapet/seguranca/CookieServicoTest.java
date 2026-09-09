package br.com.conectapet.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieServicoTest {

    private final CookieServico cookies = new CookieServico("", true, "/");

    @Test
    @DisplayName("sessao temporaria nao recebe Max-Age")
    void sessaoTemporaria() {
        var sessao = cookies.sessao("token", Duration.ofMinutes(15), false);
        var refresh = cookies.refresh("refresh", Duration.ofDays(30), false);
        var escolha = cookies.persistencia(false, Duration.ofDays(30));

        assertThat(sessao.toString()).doesNotContain("Max-Age").doesNotContain("Expires");
        assertThat(refresh.toString()).doesNotContain("Max-Age").doesNotContain("Expires");
        assertThat(escolha.toString()).contains("cp_permanecer=0").doesNotContain("Max-Age");
    }

    @Test
    @DisplayName("manter conectado persiste os tres cookies pelo prazo configurado")
    void sessaoPersistente() {
        var sessao = cookies.sessao("token", Duration.ofMinutes(15), true);
        var refresh = cookies.refresh("refresh", Duration.ofDays(30), true);
        var escolha = cookies.persistencia(true, Duration.ofDays(30));

        assertThat(sessao.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
        assertThat(refresh.getMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(escolha.getMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(escolha.toString()).contains("cp_permanecer=1", "HttpOnly", "Secure", "SameSite=Lax");
    }
}
