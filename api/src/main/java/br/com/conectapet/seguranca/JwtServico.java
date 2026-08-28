package br.com.conectapet.seguranca;

import br.com.conectapet.usuario.Papel;
import br.com.conectapet.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServico {

    private final PropriedadesJwt props;
    private final SecretKey chave;

    public JwtServico(PropriedadesJwt props) {
        this.props = props;
        byte[] bytes = props.segredo().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SEGREDO precisa de ao menos 32 bytes. Gere com: openssl rand -base64 48");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
    }

    /** Access token curto: 15 minutos. A renovacao vem do refresh rotativo. */
    public String gerarAcesso(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getUuid().toString())
                .claim("pap", usuario.getPapel().name())
                .claim("ver", usuario.emailVerificado())
                .claim("uid", usuario.getId())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(props.duracaoAcesso())))
                .signWith(chave)
                .compact();
    }

    /** Devolve null para token invalido, expirado ou adulterado — sem lancar. */
    public UsuarioAutenticado ler(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(chave).build()
                    .parseSignedClaims(token).getPayload();
            return new UsuarioAutenticado(
                    c.get("uid", Long.class),
                    UUID.fromString(c.getSubject()),
                    null,
                    Papel.valueOf(c.get("pap", String.class)),
                    Boolean.TRUE.equals(c.get("ver", Boolean.class)));
        } catch (Exception e) {
            return null;
        }
    }
}
