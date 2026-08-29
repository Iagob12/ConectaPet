package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.seguranca.*;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoControlador {

    private final AutenticacaoServico servico;
    private final JwtServico jwt;
    private final CookieServico cookies;
    private final PropriedadesJwt props;
    private final UsuarioRepositorio usuarios;
    private final UsuarioAtual usuarioAtual;

    public AutenticacaoControlador(AutenticacaoServico servico, JwtServico jwt, CookieServico cookies,
                                   PropriedadesJwt props, UsuarioRepositorio usuarios, UsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.jwt = jwt;
        this.cookies = cookies;
        this.props = props;
        this.usuarios = usuarios;
        this.usuarioAtual = usuarioAtual;
    }

    /**
     * Cadastro ja devolve sessao valida: a reivindicacao NAO exige e-mail
     * verificado. O codigo de ativacao prova posse fisica da embalagem, que e
     * evidencia mais forte que um clique em link — e mandar o cliente a caixa de
     * entrada com a tag na mao e perde-lo no melhor momento.
     */
    @PostMapping("/registrar")
    public ResponseEntity<UsuarioResposta> registrar(@Valid @RequestBody RegistroEntrada dto) {
        Usuario u = servico.registrar(dto.email(), dto.senha(), dto.nome(), dto.telefonePrincipal());
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(comSessao(u))
                .body(UsuarioResposta.de(u));
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResposta> login(@Valid @RequestBody LoginEntrada dto) {
        Usuario u = servico.autenticar(dto.email(), dto.senha());
        return ResponseEntity.ok().headers(comSessao(u)).body(UsuarioResposta.de(u));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue(name = CookieServico.COOKIE_REFRESH, required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new ProblemaException(TipoErro.NAO_AUTENTICADO);
        }
        AutenticacaoServico.Rotacao r = servico.rotacionar(token);
        Usuario u = usuarios.findById(r.usuarioId())
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_AUTENTICADO));

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.SET_COOKIE, cookies.sessao(jwt.gerarAcesso(u), props.duracaoAcesso()).toString());
        h.add(HttpHeaders.SET_COOKIE, cookies.refresh(r.tokenNovo(), props.duracaoRefresh()).toString());
        return ResponseEntity.ok().headers(h).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = CookieServico.COOKIE_REFRESH, required = false) String token) {
        if (token != null && !token.isBlank()) {
            servico.revogar(token);
        }
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.SET_COOKIE, cookies.limpar(CookieServico.COOKIE_SESSAO, "/").toString());
        h.add(HttpHeaders.SET_COOKIE, cookies.limparRefresh().toString());
        return ResponseEntity.noContent().headers(h).build();
    }

    /** Sempre 202, exista o e-mail ou nao — nao revela quem e cliente. */
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void esqueciSenha(@Valid @RequestBody EmailEntrada dto, HttpServletRequest req) {
        // Enfileiramento na outbox entra na fase de notificacoes.
    }

    private HttpHeaders comSessao(Usuario u) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.SET_COOKIE, cookies.sessao(jwt.gerarAcesso(u), props.duracaoAcesso()).toString());
        h.add(HttpHeaders.SET_COOKIE,
                cookies.refresh(servico.emitirRefresh(u.getId()), props.duracaoRefresh()).toString());
        return h;
    }

    // ---- DTOs. Nenhuma entidade JPA cruza a fronteira do controlador. -------

    public record RegistroEntrada(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 100, message = "A senha precisa de ao menos 10 caracteres") String senha,
            @NotBlank @Size(min = 2, max = 120) String nome,
            String telefonePrincipal) {}

    public record LoginEntrada(@NotBlank @Email String email, @NotBlank String senha) {}

    public record EmailEntrada(@NotBlank @Email String email) {}

    public record UsuarioResposta(UUID uuid, String email, String nome, boolean emailVerificado, String papel) {
        static UsuarioResposta de(Usuario u) {
            return new UsuarioResposta(u.getUuid(), u.getEmail(), u.getNome(),
                    u.emailVerificado(), u.getPapel().name());
        }
    }
}
