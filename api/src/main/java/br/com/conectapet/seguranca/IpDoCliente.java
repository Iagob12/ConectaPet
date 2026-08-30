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
 *     X-Forwarded-For: <o que o cliente inventou>, <IP real>, <internos>
 *
 * Quem le a primeira entrada — que e o que o ForwardedHeaderFilter do Spring
 * faz — le exatamente o valor que o atacante escolheu. Verificado contra a
 * producao: dez pedidos de "esqueci a senha" com um IP forjado diferente a
 * cada chamada passaram todos, enquanto dez com o mesmo cabecalho foram
 * cortados no sexto. O limite existia e nao valia nada.
 *
 * ---- Por que "a ultima entrada publica", e nao "a ultima" -----------------
 *
 * A primeira correcao pegava a ultima entrada, assumindo um proxy. Contra a
 * producao do Render o limite ficou ERRATICO: oito chamadas com o mesmo
 * cabecalho passaram todas, e chamadas com cabecalhos diferentes as vezes
 * colidiam. A explicacao e que a ultima entrada nao e o cliente — e um
 * endereco interno da hospedagem, que muda entre requisicoes.
 *
 * Contar saltos exige acertar um numero que depende do provedor, que nao esta
 * documentado, que ninguem revisa e que muda sem aviso quando a hospedagem
 * mexe na propria arquitetura. Errar para menos entrega uma chave que varia
 * sozinha — nenhum limite. Errar para mais entrega uma chave que o atacante
 * escolhe — nenhum limite tambem, e ainda por cima em silencio.
 *
 * A varredura da direita para a esquerda nao depende desse numero. Os
 * enderecos internos do fim sao privados (RFC 1918, loopback, link-local); o
 * do cliente e publico. Entao a primeira entrada publica vinda da direita e a
 * do cliente. O que o atacante escreve fica sempre a ESQUERDA disso — mesmo
 * que ele escreva um endereco publico, a varredura encontra o verdadeiro
 * antes de chegar nele.
 */
@Component
public class IpDoCliente {

    /** Varredura automatica: nao depende de saber a topologia do provedor. */
    public static final int AUTOMATICO = -1;

    private final int proxiesConfiaveis;

    public IpDoCliente(@Value("${conectapet.privacidade.proxies-confiaveis:-1}") int proxiesConfiaveis) {
        this.proxiesConfiaveis = proxiesConfiaveis;
    }

    public String de(HttpServletRequest req) {
        if (proxiesConfiaveis == 0) {
            return req.getRemoteAddr();   // sem proxy: o cabecalho e so ruido
        }
        String cabecalho = req.getHeader("X-Forwarded-For");
        if (cabecalho == null || cabecalho.isBlank()) {
            return req.getRemoteAddr();
        }
        String[] partes = cabecalho.split(",");

        if (proxiesConfiaveis > 0) {
            // Contagem explicita, para quem sabe a topologia e prefere fixa-la.
            int indice = partes.length - proxiesConfiaveis;
            return indice >= 0 && indice < partes.length
                    ? ouEndereco(partes[indice], req)
                    : req.getRemoteAddr();
        }

        for (int i = partes.length - 1; i >= 0; i--) {
            String ip = partes[i].trim();
            if (!ip.isEmpty() && !interno(ip)) {
                return ip;
            }
        }
        // So enderecos internos: ou a aplicacao foi alcancada por dentro da
        // rede, ou a cadeia veio malformada. O endereco da conexao e a
        // informacao menos manipulavel que sobra.
        return req.getRemoteAddr();
    }

    private String ouEndereco(String candidato, HttpServletRequest req) {
        String ip = candidato.trim();
        return ip.isEmpty() ? req.getRemoteAddr() : ip;
    }

    /** RFC 1918, loopback, link-local e o equivalente em IPv6. */
    static boolean interno(String ip) {
        String s = ip.startsWith("[") ? ip.substring(1) : ip;
        return s.startsWith("10.")
                || s.startsWith("127.")
                || s.startsWith("192.168.")
                || s.startsWith("169.254.")
                || s.startsWith("::1")
                || s.startsWith("fd") || s.startsWith("fc")   // ULA
                || s.startsWith("fe80")                       // link-local
                || s.matches("^172[.](1[6-9]|2[0-9]|3[01])[.].*");
    }
}
