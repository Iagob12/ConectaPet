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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Verificacao do e-mail cadastrado.
 *
 * A tabela existia desde a primeira migracao e o codigo nunca foi escrito, o
 * que travava a transferencia de titularidade: gerar um codigo de transferencia
 * exige e-mail verificado, e como ninguem conseguia verificar, ninguem
 * conseguia passar uma tag adiante. O recurso existia na API e era inalcancavel.
 *
 * A verificacao NAO bloqueia a ativacao da tag, de proposito: o codigo de
 * ativacao ja prova posse fisica da embalagem, que e evidencia mais forte que
 * um clique em link. Ela guarda apenas o que depende de o endereco ser
 * alcancavel de verdade — passar a tag para outra pessoa, e amanha recuperar a
 * conta.
 */
@Service
public class VerificacaoEmailServico {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoEmailServico.class);

    private final UsuarioRepositorio usuarios;
    private final TokenVerificacaoRepositorio tokens;
    private final NotificacaoServico notificacoes;
    private final Duration validade;
    private final String urlSite;
    private final SecureRandom aleatorio = new SecureRandom();

    public VerificacaoEmailServico(UsuarioRepositorio usuarios, TokenVerificacaoRepositorio tokens,
                                   NotificacaoServico notificacoes,
                                   @Value("${conectapet.verificacao-email.validade:P2D}") Duration validade,
                                   @Value("${conectapet.site.url}") String urlSite) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.notificacoes = notificacoes;
        this.validade = validade;
        this.urlSite = urlSite;
    }

    /** Silencioso quando o e-mail ja esta verificado: reenviar nao faria nada. */
    @Transactional
    public void enviar(Usuario u) {
        if (u.emailVerificado()) {
            return;
        }
        tokens.invalidarPendentes(u.getId(), Instant.now());

        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String claro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenVerificacaoEmail t = new TokenVerificacaoEmail();
        t.setUsuarioId(u.getId());
        t.setTokenHash(Hashes.sha256(claro));
        t.setEmailAlvo(u.getEmail());
        t.setExpiraEm(Instant.now().plus(validade));
        tokens.save(t);

        notificacoes.enfileirar(Notificacao.Tipo.VERIFICACAO_EMAIL, u.getEmail(), Map.of(
                "nome", u.getNome(),
                "link", urlSite + "/confirmar-email?token=" + claro));
    }

    @Transactional
    public void enviarPara(Long usuarioId) {
        usuarios.findById(usuarioId).ifPresent(this::enviar);
    }

    /**
     * Confirma o endereco.
     *
     * Se o e-mail da conta mudou depois que o link foi emitido, o link nao vale
     * mais: ele prova acesso ao endereco ANTIGO, e confirmar o novo com essa
     * prova seria aceitar um carimbo emitido para outra coisa.
     */
    @Transactional
    public void confirmar(String tokenClaro) {
        TokenVerificacaoEmail t = tokens.findByTokenHash(Hashes.sha256(tokenClaro))
                .filter(TokenVerificacaoEmail::utilizavel)
                .orElseThrow(() -> new ProblemaException(TipoErro.DADOS_INVALIDOS,
                        "Este link expirou ou ja foi usado. Peca outro pelo painel."));

        Usuario u = usuarios.findById(t.getUsuarioId())
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_AUTENTICADO));

        if (!t.getEmailAlvo().equalsIgnoreCase(u.getEmail())) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS,
                    "Este link foi enviado para outro endereco de e-mail. Peca um novo pelo painel.");
        }

        if (!u.emailVerificado()) {
            u.setEmailVerificadoEm(Instant.now());
            usuarios.save(u);
            log.info("E-mail verificado para o usuario {}", u.getUuid());
        }

        t.setUsadoEm(Instant.now());
        tokens.save(t);
    }
}
