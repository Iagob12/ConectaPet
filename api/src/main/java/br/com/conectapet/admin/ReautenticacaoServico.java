package br.com.conectapet.admin;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elevacao de privilegio para operacao sensivel.
 *
 * Ter a sessao de admin aberta nao basta para baixar os codigos de ativacao de
 * um lote: e preciso digitar a senha de novo. Sessao aberta num computador
 * destravado nao pode virar acesso ao segredo que protege todas as tags de um
 * lote inteiro.
 *
 * Vive em memoria de proposito. Perder os tokens num restart apenas obriga a
 * reautenticar — e a alternativa, persistir, seria guardar mais um portador no
 * banco sem ganho nenhum.
 */
@Service
public class ReautenticacaoServico {

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;
    private final Duration validade;
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Elevacao> ativos = new ConcurrentHashMap<>();

    public ReautenticacaoServico(UsuarioRepositorio usuarios, PasswordEncoder encoder,
                                 @Value("${conectapet.admin.validade-reautenticacao}") Duration validade) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.validade = validade;
    }

    public Elevacao elevar(UsuarioAutenticado admin, String senha) {
        var u = usuarios.findById(admin.id())
                .orElseThrow(() -> new ProblemaException(TipoErro.SEM_PERMISSAO));

        if (!encoder.matches(senha, u.getSenhaHash())) {
            throw new ProblemaException(TipoErro.SEM_PERMISSAO, "Senha incorreta.");
        }

        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Elevacao e = new Elevacao(token, admin.id(), Instant.now().plus(validade));
        ativos.put(token, e);
        return e;
    }

    /** Lanca se o token nao existe, expirou ou pertence a outro usuario. */
    public void exigir(String token, UsuarioAutenticado admin) {
        if (token == null || token.isBlank()) {
            throw new ProblemaException(TipoErro.SEM_PERMISSAO,
                    "Esta operacao exige que voce digite sua senha de novo.");
        }
        Elevacao e = ativos.get(token);
        if (e == null || e.expiraEm().isBefore(Instant.now()) || !e.usuarioId().equals(admin.id())) {
            throw new ProblemaException(TipoErro.SEM_PERMISSAO,
                    "Sua confirmacao de senha expirou. Digite a senha de novo.");
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void limpar() {
        Instant agora = Instant.now();
        ativos.values().removeIf(e -> e.expiraEm().isBefore(agora));
    }

    public record Elevacao(String token, Long usuarioId, Instant expiraEm) {}
}
