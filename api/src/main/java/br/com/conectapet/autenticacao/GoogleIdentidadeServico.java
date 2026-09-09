package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Valida o ID token emitido pelo Google antes de confiar no e-mail. */
@Service
public class GoogleIdentidadeServico {

    private static final Set<String> EMISSORES = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";

    private final String clientId;
    private final JwtDecoder decoder;

    public GoogleIdentidadeServico(@Value("${conectapet.google.client-id:}") String clientId) {
        this(clientId, criarDecoder(clientId));
    }

    GoogleIdentidadeServico(String clientId, JwtDecoder decoder) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.decoder = decoder;
    }

    public PerfilGoogle verificar(String credencial) {
        if (clientId.isBlank()) {
            throw new ProblemaException(TipoErro.ERRO_INTERNO);
        }
        try {
            Jwt jwt = decoder.decode(credencial);
            String email = jwt.getClaimAsString("email");
            String nome = jwt.getClaimAsString("name");
            String subject = jwt.getSubject();
            Boolean verificado = jwt.getClaim("email_verified");
            if (email == null || email.isBlank() || nome == null || nome.isBlank()
                    || subject == null || subject.isBlank() || !Boolean.TRUE.equals(verificado)) {
                throw new ProblemaException(TipoErro.LOGIN_GOOGLE_INVALIDO);
            }
            return new PerfilGoogle(email, nome, subject);
        } catch (JwtException e) {
            throw new ProblemaException(TipoErro.LOGIN_GOOGLE_INVALIDO);
        }
    }

    private static JwtDecoder criarDecoder(String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
        OAuth2TokenValidator<Jwt> padrao = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> emissor = jwt -> jwt.getIssuer() != null
                && EMISSORES.contains(jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Emissor do token nao e o Google", null));
        OAuth2TokenValidator<Jwt> audiencia = jwt -> jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Token emitido para outro aplicativo", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(padrao, emissor, audiencia));
        return decoder;
    }

    public record PerfilGoogle(String email, String nome, String subject) {}
}
