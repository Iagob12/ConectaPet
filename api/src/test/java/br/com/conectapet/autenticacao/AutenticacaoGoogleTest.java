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
import static org.mockito.ArgumentMatchers.any;
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

        AutenticacaoServico.ResultadoGoogle resultado =
                servico.autenticarGoogle(" TUTOR@EXEMPLO.COM ", "Tutor Exemplo", "google-123");
        Usuario autenticado = resultado.usuario();

        assertThat(resultado.criado()).isFalse();
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

        assertThatThrownBy(() -> servico.autenticarGoogle(
                "tutor@exemplo.com", "Tutor Exemplo", "google-outro"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.LOGIN_GOOGLE_INVALIDO));
    }

    @Test
    void primeiroAcessoCriaContaGoogleComEmailConfirmado() {
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmailAndExcluidoEmIsNull("novo@exemplo.com")).thenReturn(Optional.empty());
        when(usuarios.existsByEmail("novo@exemplo.com")).thenReturn(false);
        when(usuarios.existsByGoogleSubject("google-123")).thenReturn(false);
        when(usuarios.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        AutenticacaoServico.ResultadoGoogle resultado = servico.autenticarGoogle(
                " NOVO@EXEMPLO.COM ", "  Nova   Pessoa  ", "google-123");

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.usuario().getEmail()).isEqualTo("novo@exemplo.com");
        assertThat(resultado.usuario().getNome()).isEqualTo("Nova Pessoa");
        assertThat(resultado.usuario().getGoogleSubject()).isEqualTo("google-123");
        assertThat(resultado.usuario().getSenhaHash()).isNull();
        assertThat(resultado.usuario().getTelefonePrincipal()).isNull();
        assertThat(resultado.usuario().emailVerificado()).isTrue();
    }

    @Test
    void naoReaproveitaIdentidadeDeContaExcluida() {
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-123")).thenReturn(Optional.empty());
        when(usuarios.findByEmailAndExcluidoEmIsNull("antigo@exemplo.com")).thenReturn(Optional.empty());
        when(usuarios.existsByEmail("antigo@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> servico.autenticarGoogle(
                "antigo@exemplo.com", "Antigo Tutor", "google-123"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.LOGIN_GOOGLE_INVALIDO));
    }

    @Test
    void subjectJaVinculadoContinuaValendoSeEmailGoogleMudar() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        usuario.setEmail("email-antigo@exemplo.com");
        usuario.setGoogleSubject("google-123");
        when(usuarios.findByGoogleSubjectAndExcluidoEmIsNull("google-123")).thenReturn(Optional.of(usuario));

        AutenticacaoServico.ResultadoGoogle resultado = servico.autenticarGoogle(
                "email-novo@exemplo.com", "Tutor Exemplo", "google-123");

        assertThat(resultado.usuario()).isSameAs(usuario);
        assertThat(resultado.criado()).isFalse();
    }

    @Test
    void contaSomenteGoogleNaoAceitaQualquerSenhaLocal() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        usuario.setEmail("google@exemplo.com");
        usuario.setSenhaHash(null);
        when(usuarios.findByEmailAndExcluidoEmIsNull("google@exemplo.com"))
                .thenReturn(Optional.of(usuario));
        when(encoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar("google@exemplo.com", "qualquer-senha"))
                .isInstanceOfSatisfying(ProblemaException.class,
                        e -> assertThat(e.tipo()).isEqualTo(TipoErro.CREDENCIAIS_INVALIDAS));
    }
}
