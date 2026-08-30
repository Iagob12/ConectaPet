package br.com.conectapet.publico;

import br.com.conectapet.comum.util.GeradorCodigo;
import br.com.conectapet.comum.util.Hashes;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite das rotas publicas.
 *
 * A chave do balde da leitura e **IP + codigo**, nao so IP: operadoras moveis
 * brasileiras compartilham um mesmo endereco de saida entre muitos assinantes,
 * e 30/min por IP puro derrubaria usuarios legitimos num shopping ou evento.
 *
 * Em memoria de proposito nesta fase: para limites de minuto, perder o contador
 * num restart e aceitavel. O limite que NAO pode viver em memoria e o de
 * reivindicacao, de 5 por hora, e esse ja mora no banco.
 *
 * O mapa tem TETO. Sem ele, a defesa vira o ataque: a chave inclui o codigo da
 * URL, que quem chama escolhe, entao bastava pedir /api/public/tags/<algo novo>
 * em laco para criar uma entrada permanente por requisicao ate a memoria acabar.
 * Numa instancia de 512 MB isso derruba a API em minutos, e o alvo e justamente
 * a rota que precisa responder quando alguem acha um cachorro na rua.
 *
 * Ao encher, o mapa e esvaziado por inteiro em vez de despejar entrada por
 * entrada. E grosseiro de proposito: LRU exigiria acesso sincronizado no
 * caminho de toda requisicao, e a consequencia de esvaziar — alguns baldes
 * voltam ao zero — e pequena perto de manter o teto barato. Precisa de
 * mais que isso, o lugar e um contador compartilhado, nao um mapa local.
 */
@Component
@Order(1)
public class FiltroLimiteRequisicoes extends OncePerRequestFilter {

    private final Map<String, Bucket> baldes = new ConcurrentHashMap<>();
    private final int tetoBaldes;
    private final String ipPimenta;
    private final br.com.conectapet.seguranca.IpDoCliente ipDoCliente;
    private final int limiteLeitura;
    private final int limiteRegistro;
    private final int limiteListaEspera;
    private final int limiteResetSenha;

    public FiltroLimiteRequisicoes(
            @Value("${conectapet.privacidade.ip-pimenta}") String ipPimenta,
                              br.com.conectapet.seguranca.IpDoCliente ipDoCliente,
            @Value("${conectapet.limites.leitura-publica-por-minuto:30}") int limiteLeitura,
            @Value("${conectapet.limites.registro-leitura-por-minuto:10}") int limiteRegistro,
            @Value("${conectapet.limites.lista-espera-por-hora:5}") int limiteListaEspera,
            @Value("${conectapet.limites.reset-senha-por-hora:5}") int limiteResetSenha,
            @Value("${conectapet.limites.teto-baldes:50000}") int tetoBaldes) {
        this.ipPimenta = ipPimenta;
        this.ipDoCliente = ipDoCliente;
        this.limiteLeitura = limiteLeitura;
        this.limiteRegistro = limiteRegistro;
        this.limiteListaEspera = limiteListaEspera;
        this.limiteResetSenha = limiteResetSenha;
        this.tetoBaldes = tetoBaldes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        // O esqueci-senha entra aqui mesmo nao sendo /api/public/: ele dispara
        // e-mail para terceiro sem exigir sessao, que e exatamente a forma de
        // usar o servidor para incomodar quem nem pediu nada.
        return !uri.startsWith("/api/public/") && !uri.equals("/api/auth/esqueci-senha");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String uri = req.getRequestURI();
        String ip = Hashes.ipPseudonimo(ipDoCliente.de(req), ipPimenta);
        Bucket balde;

        if (uri.equals("/api/auth/esqueci-senha")) {
            balde = obter("reset:" + ip, limiteResetSenha, Duration.ofHours(1));
        } else if (uri.startsWith("/api/public/lista-espera")) {
            balde = obter("espera:" + ip, limiteListaEspera, Duration.ofHours(1));
        } else if (uri.endsWith("/leituras")) {
            balde = obter("registro:" + ip + ":" + codigoDaUri(uri), limiteRegistro, Duration.ofMinutes(1));
        } else {
            balde = obter("leitura:" + ip + ":" + codigoDaUri(uri), limiteLeitura, Duration.ofMinutes(1));
        }

        if (balde.tryConsume(1)) {
            chain.doFilter(req, res);
            return;
        }

        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/problem+json");
        res.getWriter().write("""
                {"type":"https://api.conectapet.com.br/erros/limite-excedido",\
                "title":"Muitas requisicoes","status":429,\
                "detail":"Aguarde um instante e tente de novo."}""");
    }

    /**
     * So um codigo de forma valida vira chave propria. Todo o resto — lixo,
     * tentativa de enumeracao, variacao de maiuscula — cai no mesmo balde "-".
     *
     * E o que impede a chave de ter cardinalidade infinita: sem isto, pedir
     * um codigo inventado diferente a cada vez dava a quem chama um balde novo
     * e vazio toda vez, ou seja, nenhum limite, e ainda enchia o mapa.
     * Colapsados num balde so, os 30 pedidos por minuto passam a valer para a
     * enumeracao inteira, que e o comportamento que se quer limitar.
     */
    private String codigoDaUri(String uri) {
        String[] partes = uri.split("/");
        for (int i = 0; i < partes.length; i++) {
            if ("tags".equals(partes[i]) && i + 1 < partes.length) {
                String c = GeradorCodigo.normalizar(partes[i + 1]);
                return GeradorCodigo.formaValida(c, GeradorCodigo.TAMANHO_PUBLICO) ? c : "-";
            }
        }
        return "-";
    }

    private Bucket obter(String chave, int capacidade, Duration janela) {
        Bucket existente = baldes.get(chave);
        if (existente != null) {
            return existente;
        }
        // A verificacao fica ANTES de inserir, e nao depois: depois, o mapa
        // passa do teto por um instante a cada requisicao, que e exatamente a
        // janela que um laco explora.
        if (baldes.size() >= tetoBaldes) {
            baldes.clear();
        }
        return baldes.computeIfAbsent(chave, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacidade, Refill.intervally(capacidade, janela)))
                .build());
    }
}
