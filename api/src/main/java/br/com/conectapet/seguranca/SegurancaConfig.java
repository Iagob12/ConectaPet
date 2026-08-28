package br.com.conectapet.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    private final JwtServico jwt;
    private final List<String> origens;

    public SegurancaConfig(JwtServico jwt, @Value("${conectapet.cors.origens}") String origens) {
        this.jwt = jwt;
        this.origens = Arrays.stream(origens.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    /** Custo 12: caro de proposito, para tornar forca bruta offline inviavel. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // sem sessao de servidor; cookie e SameSite=Lax
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg
                // Unica superficie aberta. Nenhuma outra rota fica publica.
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/registrar", "/api/auth/login", "/api/auth/refresh",
                        "/api/auth/esqueci-senha", "/api/auth/redefinir-senha",
                        "/api/auth/verificar-email").permitAll()
                .requestMatchers("/actuator/health", "/docs/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
                .accessDeniedHandler((req, res, ex) -> res.setStatus(403)))
            .addFilterBefore(new FiltroJwt(jwt), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** CORS so para as origens conhecidas, com credenciais. Nunca "*". */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origens);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Reautenticacao"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", cfg);
        return fonte;
    }

    /** Le a sessao do cookie HttpOnly. Ausencia de token nao e erro aqui: quem decide e a autorizacao. */
    static class FiltroJwt extends OncePerRequestFilter {

        private final JwtServico jwt;

        FiltroJwt(JwtServico jwt) {
            this.jwt = jwt;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws jakarta.servlet.ServletException, IOException {

            String token = extrair(req);
            if (token != null) {
                UsuarioAutenticado u = jwt.ler(token);
                if (u != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.papel().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            chain.doFilter(req, res);
        }

        private String extrair(HttpServletRequest req) {
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (CookieServico.COOKIE_SESSAO.equals(c.getName())) {
                        return c.getValue();
                    }
                }
            }
            return null;
        }
    }
}
