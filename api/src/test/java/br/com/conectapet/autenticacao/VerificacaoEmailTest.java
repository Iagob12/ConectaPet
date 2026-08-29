package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.notificacao.Notificacao;
import br.com.conectapet.notificacao.NotificacaoServico;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificacaoEmailTest {

    private UsuarioRepositorio usuarios;
    private TokenVerificacaoRepositorio tokens;
    private NotificacaoServico notificacoes;
    private VerificacaoEmailServico servico;

    private final Map<String, TokenVerificacaoEmail> salvos = new HashMap<>();
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        tokens = mock(TokenVerificacaoRepositorio.class);
        notificacoes = mock(NotificacaoServico.class);

        usuario = new Usuario();
        usuario.setId(3L);
        usuario.setUuid(UUID.randomUUID());
        usuario.setEmail("tutor@exemplo.com");
        usuario.setNome("Tutor");

        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(tokens.save(any(TokenVerificacaoEmail.class))).thenAnswer(i -> {
            TokenVerificacaoEmail t = i.getArgument(0);
            salvos.put(t.getTokenHash(), t);
            return t;
        });
        when(tokens.findByTokenHash(anyString()))
                .thenAnswer(i -> Optional.ofNullable(salvos.get(i.<String>getArgument(0))));

        servico = new VerificacaoEmailServico(usuarios, tokens, notificacoes,
                Duration.ofDays(2), "https://conectapet.com.br");
    }

    private String tokenEnviado() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(notificacoes, atLeastOnce())
                .enfileirar(eq(Notificacao.Tipo.VERIFICACAO_EMAIL), anyString(), captor.capture());
        String link = String.valueOf(captor.getValue().get("link"));
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @Test
    @DisplayName("confirma o endereco e marca a data")
    void confirma() {
        servico.enviar(usuario);
        assertThat(usuario.emailVerificado()).isFalse();

        servico.confirmar(tokenEnviado());

        assertThat(usuario.emailVerificado()).isTrue();
        assertThat(usuario.getEmailVerificadoEm()).isNotNull();
    }

    @Test
    @DisplayName("nao reenvia para quem ja confirmou")
    void jaVerificado() {
        usuario.setEmailVerificadoEm(Instant.now());
        servico.enviar(usuario);

        verifyNoInteractions(notificacoes);
        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("link emitido para o e-mail antigo nao confirma o novo")
    void enderecoMudouDepois() {
        servico.enviar(usuario);
        String claro = tokenEnviado();

        // A pessoa trocou o e-mail entre o envio e o clique. O link prova
        // acesso ao endereco ANTIGO; aceita-lo carimbaria o novo com uma prova
        // que nao e dele.
        usuario.setEmail("outro@exemplo.com");

        assertThatThrownBy(() -> servico.confirmar(claro))
                .isInstanceOf(ProblemaException.class);
        assertThat(usuario.emailVerificado()).isFalse();
    }

    @Test
    @DisplayName("o mesmo link nao serve duas vezes")
    void usoUnico() {
        servico.enviar(usuario);
        String claro = tokenEnviado();
        servico.confirmar(claro);

        assertThatThrownBy(() -> servico.confirmar(claro))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("token expirado e recusado")
    void expirado() {
        servico.enviar(usuario);
        String claro = tokenEnviado();
        salvos.values().iterator().next().setExpiraEm(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> servico.confirmar(claro))
                .isInstanceOf(ProblemaException.class);
        assertThat(usuario.emailVerificado()).isFalse();
    }

    @Test
    @DisplayName("pedir outro link invalida o anterior")
    void invalidaPendentes() {
        servico.enviar(usuario);
        verify(tokens).invalidarPendentes(eq(3L), any(Instant.class));
    }
}
