package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdentidadeServicoTest {

    private Jwt token(boolean emailVerificado) {
        Instant agora = Instant.now();
        return Jwt.withTokenValue("credencial")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("google-123")
                .audience(List.of("cliente-web"))
                .issuedAt(agora.minusSeconds(10))
                .expiresAt(agora.plusSeconds(300))
                .claim("email", "Tutor@Exemplo.com")
                .claim("name", "Tutor Exemplo")
                .claim("email_verified", emailVerificado)
                .build();
    }

    @Test
    void aceitaSomentePerfilCompletoComEmailConfirmado() {
        GoogleIdentidadeServico servico = new GoogleIdentidadeServico("cliente-web", valor -> token(true));

        GoogleIdentidadeServico.PerfilGoogle perfil = servico.verificar("credencial");

        assertThat(perfil.email()).isEqualTo("Tutor@Exemplo.com");
        assertThat(perfil.nome()).isEqualTo("Tutor Exemplo");
        assertThat(perfil.subject()).isEqualTo("google-123");
    }

    @Test
    void recusaEmailQueGoogleNaoConfirmou() {
        GoogleIdentidadeServico servico = new GoogleIdentidadeServico("cliente-web", valor -> token(false));

        assertThatThrownBy(() -> servico.verificar("credencial"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.LOGIN_GOOGLE_INVALIDO));
    }

    @Test
    void transformaFalhaCriptograficaEmErroSeguro() {
        GoogleIdentidadeServico servico = new GoogleIdentidadeServico("cliente-web", valor -> {
            throw new JwtException("assinatura invalida");
        });

        assertThatThrownBy(() -> servico.verificar("credencial-forjada"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.LOGIN_GOOGLE_INVALIDO));
    }

    @Test
    void configuracaoAusenteNaoAceitaCredencial() {
        GoogleIdentidadeServico servico = new GoogleIdentidadeServico("", valor -> token(true));

        assertThatThrownBy(() -> servico.verificar("credencial"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.ERRO_INTERNO));
    }
}
