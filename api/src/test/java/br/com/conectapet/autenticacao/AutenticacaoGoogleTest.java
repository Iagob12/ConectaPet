package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.seguranca.PropriedadesJwt;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoGoogleTest {

    @Mock UsuarioRepositorio usuarios;
    @Mock RefreshTokenRepositorio refreshTokens;
    @Mock PasswordEncoder encoder;
    @Mock PropriedadesJwt props;
    private AutenticacaoServico servico;

    @BeforeEach
    void preparar() {
        servico = new AutenticacaoServico(usuarios, refreshTokens, encoder, props);
    }

    @Test
    void primeiroAcessoVinculaSubjectEConfirmaEmail() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        usuario.setEmail("tutor@exemplo.com");
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmailAndExcluidoEmIsNull("tutor@exemplo.com")).thenReturn(Optional.of(usuario));

        Usuario autenticado = servico.autenticarGoogle(" TUTOR@EXEMPLO.COM ", "google-123");

        assertThat(autenticado.getGoogleSubject()).isEqualTo("google-123");
        assertThat(autenticado.emailVerificado()).isTrue();
        verify(usuarios).save(usuario);
    }

    @Test
    void naoAceitaOutroGoogleDepoisDoVinculo() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        usuario.setEmail("tutor@exemplo.com");
        usuario.setGoogleSubject("google-original");
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-outro")).thenReturn(Optional.empty());
        when(usuarios.findByEmailAndExcluidoEmIsNull("tutor@exemplo.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servico.autenticarGoogle("tutor@exemplo.com", "google-outro"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.LOGIN_GOOGLE_INVALIDO));
    }

    @Test
    void naoCriaCadastroIncompleto() {
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmailAndExcluidoEmIsNull("novo@exemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticarGoogle("novo@exemplo.com", "google-123"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.CONTA_NAO_ENCONTRADA));
    }

    @Test
    void subjectJaVinculadoContinuaValendoSeEmailGoogleMudar() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        usuario.setEmail("email-antigo@exemplo.com");
        usuario.setGoogleSubject("google-123");
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-123")).thenReturn(Optional.of(usuario));

        Usuario autenticado = servico.autenticarGoogle("email-novo@exemplo.com", "google-123");

        assertThat(autenticado).isSameAs(usuario);
    }
}
