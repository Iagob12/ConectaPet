package br.com.conectapet.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Descobre o IP de quem realmente fez a requisicao, atras de proxy.
 *
 * Existe porque confiar no `X-Forwarded-For` inteiro e o mesmo que nao ter
 * limite por IP nenhum. O cabecalho e escrito pelo CLIENTE e apenas
 * COMPLEMENTADO pelo proxy: o que chega aqui e
 *
 *     X-Forwarded-For: <o que o cliente inventou>, <IP real, escrito pelo proxy>
 *
 * Quem le a primeira entrada — que e o que o ForwardedHeaderFilter do Spring
 * faz — le exatamente o valor que o atacante escolheu. Foi verificado contra a
 * producao: dez pedidos de "esqueci a senha" com um IP forjado diferente a cada
 * chamada passaram todos, enquanto dez com o mesmo cabecalho foram cortados no
 * sexto. O limite existia e nao valia nada.
 *
 * A entrada confiavel e a ULTIMA — a que o proxio imediatamente a nossa frente
 * escreveu, e que o cliente nao alcanca. Com mais de um proxy encadeado, anda
 * para a esquerda um por hop; dai `proxies-confiaveis` ser configuravel, e nao
 * uma constante: o numero certo depende de onde a aplicacao esta hospedada, e
 * errar para MAIS confia em entrada de atacante de novo.
 *
 * Sem o cabecalho, cai no endereco da conexao — que e o certo em execucao local
 * e em qualquer deploy sem proxy.
 */
@Component
public class IpDoCliente {

    private final int proxiesConfiaveis;

    public IpDoCliente(@Value("${conectapet.privacidade.proxies-confiaveis:1}") int proxiesConfiaveis) {
        this.proxiesConfiaveis = Math.max(0, proxiesConfiaveis);
    }

    public String de(HttpServletRequest req) {
        if (proxiesConfiaveis == 0) {
            return req.getRemoteAddr();
        }
        String cabecalho = req.getHeader("X-Forwarded-For");
        if (cabecalho == null || cabecalho.isBlank()) {
            return req.getRemoteAddr();
        }
        String[] partes = cabecalho.split(",");
        int indice = partes.length - proxiesConfiaveis;
        if (indice < 0 || indice >= partes.length) {
            // Menos entradas do que proxies declarados: ou a configuracao esta
            // errada, ou alguem alcancou a aplicacao por fora do proxy. Nos dois
            // casos o endereco da conexao e a informacao menos manipulavel que
            // existe aqui.
            return req.getRemoteAddr();
        }
        String ip = partes[indice].trim();
        return ip.isEmpty() ? req.getRemoteAddr() : ip;
    }
}
