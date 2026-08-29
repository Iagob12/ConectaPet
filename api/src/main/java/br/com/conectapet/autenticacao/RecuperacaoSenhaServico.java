package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.notificacao.Notificacao;
import br.com.conectapet.notificacao.NotificacaoServico;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Redefinicao de senha por link enviado ao e-mail.
 *
 * Sem isto, esquecer a senha era perder a conta para sempre — e junto com ela a
 * capacidade de corrigir o telefone que aparece na tag do pet. Era a falha mais
 * cara do produto: a tag continua na coleira, apontando para um numero errado
 * que o dono nao consegue mais trocar.
 */
@Service
public class RecuperacaoSenhaServico {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaServico.class);

    private final UsuarioRepositorio usuarios;
    private final TokenResetRepositorio tokens;
    private final RefreshTokenRepositorio refreshTokens;
    private final NotificacaoServico notificacoes;
    private final PasswordEncoder encoder;
    private final Duration validade;
    private final String urlSite;
    private final SecureRandom aleatorio = new SecureRandom();

    public RecuperacaoSenhaServico(UsuarioRepositorio usuarios, TokenResetRepositorio tokens,
                                   RefreshTokenRepositorio refreshTokens,
                                   NotificacaoServico notificacoes, PasswordEncoder encoder,
                                   @Value("${conectapet.reset-senha.validade:PT1H}") Duration validade,
                                   @Value("${conectapet.site.url}") String urlSite) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.notificacoes = notificacoes;
        this.encoder = encoder;
        this.validade = validade;
        this.urlSite = urlSite;
    }

    /**
     * Sempre termina em silencio, exista o e-mail ou nao.
     *
     * Responder diferente para endereco desconhecido transformaria a tela de
     * "esqueci minha senha" num verificador de quem e cliente — e a lista de
     * clientes daqui e uma lista de pessoas com pet e endereco descoberto.
     */
    @Transactional
    public void solicitar(String email) {
        Optional<Usuario> achado = usuarios.findByEmailAndExcluidoEmIsNull(normalizar(email));
        if (achado.isEmpty() || !achado.get().isAtivo()) {
            return;
        }
        Usuario u = achado.get();
        tokens.invalidarPendentes(u.getId(), Instant.now());

        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String claro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenResetSenha t = new TokenResetSenha();
        t.setUsuarioId(u.getId());
        t.setTokenHash(Hashes.sha256(claro));
        t.setExpiraEm(Instant.now().plus(validade));
        tokens.save(t);

        notificacoes.enfileirar(Notificacao.Tipo.RESET_SENHA, u.getEmail(), Map.of(
                "nome", u.getNome(),
                "link", urlSite + "/redefinir-senha?token=" + claro,
                "validadeMinutos", validade.toMinutes()));
    }

    /**
     * Troca a senha e derruba todas as sessoes abertas.
     *
     * Quem redefine a senha costuma estar fazendo isso porque desconfia que
     * alguem entrou. Manter as sessoes vivas deixaria o invasor logado
     * exatamente depois da acao que deveria expulsa-lo.
     */
    @Transactional
    public void redefinir(String tokenClaro, String novaSenha) {
        TokenResetSenha t = tokens.findByTokenHash(Hashes.sha256(tokenClaro))
                .filter(TokenResetSenha::utilizavel)
                .orElseThrow(() -> new ProblemaException(TipoErro.DADOS_INVALIDOS,
                        "Este link expirou ou ja foi usado. Peca um novo."));

        Usuario u = usuarios.findById(t.getUsuarioId())
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_AUTENTICADO));

        u.setSenhaHash(encoder.encode(novaSenha));
        usuarios.save(u);

        t.setUsadoEm(Instant.now());
        tokens.save(t);

        refreshTokens.revogarTodosDoUsuario(u.getId(), Instant.now());
        log.info("Senha redefinida para o usuario {}", u.getUuid());
    }

    private String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
