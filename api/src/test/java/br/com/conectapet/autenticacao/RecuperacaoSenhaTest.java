package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.notificacao.Notificacao;
import br.com.conectapet.notificacao.NotificacaoServico;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * O que este teste protege sao as promessas que a tela faz ao usuario e as que
 * o servico faz ao proximo desenvolvedor. Nada aqui precisa de banco: o
 * repositorio e dublado, e o que importa e a decisao, nao a persistencia.
 */
class RecuperacaoSenhaTest {

    private UsuarioRepositorio usuarios;
    private TokenResetRepositorio tokens;
    private RefreshTokenRepositorio refreshTokens;
    private NotificacaoServico notificacoes;
    private PasswordEncoder encoder;
    private RecuperacaoSenhaServico servico;

    /** Guarda o que foi salvo, para o teste achar o token pelo hash. */
    private final Map<String, TokenResetSenha> salvos = new HashMap<>();
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        tokens = mock(TokenResetRepositorio.class);
        refreshTokens = mock(RefreshTokenRepositorio.class);
        notificacoes = mock(NotificacaoServico.class);
        encoder = new BCryptPasswordEncoder(4);

        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setUuid(UUID.randomUUID());
        usuario.setEmail("tutora@exemplo.com");
        usuario.setNome("Tutora");
        usuario.setSenhaHash(encoder.encode("senha-antiga-123"));

        when(usuarios.findByEmailAndExcluidoEmIsNull("tutora@exemplo.com"))
                .thenReturn(Optional.of(usuario));
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        when(tokens.save(any(TokenResetSenha.class))).thenAnswer(i -> {
            TokenResetSenha t = i.getArgument(0);
            salvos.put(t.getTokenHash(), t);
            return t;
        });
        when(tokens.findByTokenHash(anyString()))
                .thenAnswer(i -> Optional.ofNullable(salvos.get(i.<String>getArgument(0))));

        servico = new RecuperacaoSenhaServico(usuarios, tokens, refreshTokens, notificacoes,
                encoder, Duration.ofHours(1), "https://conectapet.com.br");
    }

    /** Extrai o token em claro do link que foi para a fila de e-mail. */
    private String tokenEnviado() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(notificacoes, atLeastOnce())
                .enfileirar(eq(Notificacao.Tipo.RESET_SENHA), eq("tutora@exemplo.com"), captor.capture());
        String link = String.valueOf(captor.getValue().get("link"));
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @Test
    @DisplayName("e-mail desconhecido nao gera token nem notificacao")
    void emailDesconhecido() {
        when(usuarios.findByEmailAndExcluidoEmIsNull("ninguem@exemplo.com"))
                .thenReturn(Optional.empty());

        servico.solicitar("ninguem@exemplo.com");

        // Silencio total: a diferenca entre existir e nao existir nao pode
        // aparecer nem como e-mail enviado nem como linha no banco.
        verifyNoInteractions(notificacoes);
        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("normaliza o e-mail antes de procurar")
    void normalizaEmail() {
        servico.solicitar("  TUTORA@Exemplo.COM  ");
        verify(usuarios).findByEmailAndExcluidoEmIsNull("tutora@exemplo.com");
    }

    @Test
    @DisplayName("guarda o hash, nunca o token em claro")
    void guardaSomenteHash() {
        servico.solicitar("tutora@exemplo.com");
        String claro = tokenEnviado();

        assertThat(salvos).hasSize(1);
        TokenResetSenha t = salvos.values().iterator().next();
        assertThat(t.getTokenHash()).isEqualTo(Hashes.sha256(claro)).isNotEqualTo(claro);
    }

    @Test
    @DisplayName("pedir de novo invalida o link anterior")
    void invalidaPendentes() {
        servico.solicitar("tutora@exemplo.com");
        verify(tokens).invalidarPendentes(eq(7L), any(Instant.class));
    }

    @Test
    @DisplayName("redefinir troca a senha e derruba todas as sessoes")
    void redefine() {
        servico.solicitar("tutora@exemplo.com");
        servico.redefinir(tokenEnviado(), "senha-nova-98765");

        assertThat(encoder.matches("senha-nova-98765", usuario.getSenhaHash())).isTrue();
        assertThat(encoder.matches("senha-antiga-123", usuario.getSenhaHash())).isFalse();
        // Quem redefine costuma suspeitar que alguem entrou: manter a sessao
        // viva deixaria o invasor logado depois da acao que deveria expulsa-lo.
        verify(refreshTokens).revogarTodosDoUsuario(eq(7L), any(Instant.class));
    }

    @Test
    @DisplayName("o mesmo link nao serve duas vezes")
    void tokenDeUsoUnico() {
        servico.solicitar("tutora@exemplo.com");
        String claro = tokenEnviado();
        servico.redefinir(claro, "senha-nova-98765");

        assertThatThrownBy(() -> servico.redefinir(claro, "outra-senha-4321"))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("token expirado e recusado")
    void tokenExpirado() {
        servico.solicitar("tutora@exemplo.com");
        String claro = tokenEnviado();
        salvos.values().iterator().next().setExpiraEm(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> servico.redefinir(claro, "senha-nova-98765"))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("token inventado e recusado sem tocar no usuario")
    void tokenInexistente() {
        assertThatThrownBy(() -> servico.redefinir("token-que-nunca-existiu", "senha-nova-98765"))
                .isInstanceOf(ProblemaException.class);
        verify(usuarios, never()).save(any());
    }
}
