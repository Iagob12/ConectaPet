package br.com.conectapet.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Defesa em profundidade para toda resposta da API.
 *
 * A API nao entrega documentos executaveis. Por isso sua CSP pode ser muito
 * mais fechada que a do site: nenhum script, estilo, frame, formulario ou
 * recurso externo e necessario. Os demais cabecalhos reduzem interpretacao de
 * MIME, embedding e cache acidental de dados pessoais por navegador ou proxy.
 */
@Component
@Order(2)
public class CabecalhosSegurancaApi extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()");
        res.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        res.setHeader("Cache-Control", "no-store");
        chain.doFilter(req, res);
    }
}
