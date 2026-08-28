package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.seguranca.PropriedadesJwt;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacaoServico {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoServico.class);

    /**
     * Hash BCrypt descartavel, usado para gastar o mesmo tempo quando o e-mail
     * nao existe. Sem isso, a diferenca de latencia entre "conta existe, senha
     * errada" e "conta nao existe" revela quem e cliente.
     */
    private static final String HASH_FALSO =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.7dEA9GYAKRT5pW7XHZQZgVMFj9nNzXe";

    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final PasswordEncoder encoder;
    private final PropriedadesJwt props;
    private final SecureRandom aleatorio = new SecureRandom();

    public AutenticacaoServico(UsuarioRepositorio usuarios, RefreshTokenRepositorio refreshTokens,
                               PasswordEncoder encoder, PropriedadesJwt props) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.props = props;
    }

    @Transactional
    public Usuario registrar(String email, String senha, String nome, String telefone) {
        String normalizado = normalizarEmail(email);
        if (usuarios.existsByEmail(normalizado)) {
            throw new ProblemaException(TipoErro.EMAIL_EM_USO);
        }
        Usuario u = new Usuario();
        u.setEmail(normalizado);
        u.setSenhaHash(encoder.encode(senha));
        u.setNome(nome.trim());
        u.setTelefonePrincipal(telefone);
        return usuarios.save(u);
    }

    /**
     * Resposta identica para e-mail inexistente e senha errada. O BCrypt e
     * executado nos dois caminhos para igualar o tempo de resposta.
     */
    @Transactional(readOnly = true)
    public Usuario autenticar(String email, String senha) {
        Optional<Usuario> achado = usuarios.findByEmailAndExcluidoEmIsNull(normalizarEmail(email));
        String hash = achado.map(Usuario::getSenhaHash).orElse(HASH_FALSO);
        boolean confere = encoder.matches(senha, hash);

        if (achado.isEmpty() || !confere || !achado.get().isAtivo()) {
            throw new ProblemaException(TipoErro.CREDENCIAIS_INVALIDAS);
        }
        return achado.get();
    }

    /** Novo login abre uma familia nova de refresh. */
    @Transactional
    public String emitirRefresh(Long usuarioId) {
        return emitirNaFamilia(usuarioId, UUID.randomUUID(), null);
    }

    /**
     * Rotacao com deteccao de reuso.
     *
     * Fora da janela de tolerancia, apresentar um token ja usado significa que
     * ele vazou: revoga a familia inteira e derruba a sessao. Dentro da janela,
     * e apenas a segunda aba do mesmo usuario renovando ao mesmo tempo — devolve
     * o token sucessor em vez de punir.
     */
    @Transactional
    public Rotacao rotacionar(String tokenClaro) {
        RefreshToken atual = refreshTokens.findByTokenHash(Hashes.sha256(tokenClaro))
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_AUTENTICADO));

        if (atual.getRevogadoEm() != null || atual.getExpiraEm().isBefore(Instant.now())) {
            throw new ProblemaException(TipoErro.NAO_AUTENTICADO);
        }

        if (atual.getUsadoEm() != null) {
            boolean dentroDaJanela = atual.getUsadoEm()
                    .plus(props.toleranciaRotacao())
                    .isAfter(Instant.now());

            if (!dentroDaJanela) {
                log.warn("Reuso de refresh detectado. Revogando familia do usuario {}", atual.getUsuarioId());
                refreshTokens.revogarFamilia(atual.getFamilia(), Instant.now());
                throw new ProblemaException(TipoErro.NAO_AUTENTICADO);
            }
            // Corrida entre abas: emite um token novo na mesma familia em vez de
            // punir. Devolver o sucessor nao serviria — so o hash dele existe.
            String extra = emitirNaFamilia(atual.getUsuarioId(), atual.getFamilia(), null);
            return new Rotacao(atual.getUsuarioId(), extra);
        }

        String novo = emitirNaFamilia(atual.getUsuarioId(), atual.getFamilia(), atual);
        return new Rotacao(atual.getUsuarioId(), novo);
    }

    @Transactional
    public void revogar(String tokenClaro) {
        refreshTokens.findByTokenHash(Hashes.sha256(tokenClaro))
                .ifPresent(t -> {
                    t.setRevogadoEm(Instant.now());
                    refreshTokens.save(t);
                });
    }

    @Transactional
    public void revogarTudo(Long usuarioId) {
        refreshTokens.revogarTodosDoUsuario(usuarioId, Instant.now());
    }

    private String emitirNaFamilia(Long usuarioId, UUID familia, RefreshToken anterior) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String claro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken novo = new RefreshToken();
        novo.setUsuarioId(usuarioId);
        novo.setTokenHash(Hashes.sha256(claro));
        novo.setFamilia(familia);
        novo.setExpiraEm(Instant.now().plus(props.duracaoRefresh()));
        refreshTokens.saveAndFlush(novo);

        if (anterior != null) {
            anterior.setUsadoEm(Instant.now());
            anterior.setSubstituidoPor(novo.getId());
            refreshTokens.save(anterior);
        }
        return claro;
    }

    private String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public record Rotacao(Long usuarioId, String tokenNovo) {}
}
