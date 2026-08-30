package br.com.conectapet.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que faltava quando o limite por IP nao valia nada.
 *
 * O caso do "forja" e o unico que importa de verdade: foi ele que passou
 * contra a producao — dez pedidos de recuperacao de senha com um IP inventado
 * diferente a cada chamada, nenhum bloqueado.
 */
class IpDoClienteTest {

    private MockHttpServletRequest pedido(String remoto, String encaminhado) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoto);
        if (encaminhado != null) {
            r.addHeader("X-Forwarded-For", encaminhado);
        }
        return r;
    }

    @Test
    @DisplayName("um proxy: usa a entrada que o proxy escreveu, nao a que o cliente inventou")
    void ignoraOQueOClienteEscreveu() {
        IpDoCliente ip = new IpDoCliente(1);
        // O cliente mandou "9.9.9.9"; o proxy acrescentou o endereco real.
        assertThat(ip.de(pedido("10.0.0.1", "9.9.9.9, 203.0.113.5"))).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("forjar o cabecalho nao muda mais a chave do limite")
    void forjaNaoMudaNada() {
        IpDoCliente ip = new IpDoCliente(1);
        String a = ip.de(pedido("10.0.0.1", "1.1.1.1, 203.0.113.5"));
        String b = ip.de(pedido("10.0.0.1", "2.2.2.2, 203.0.113.5"));
        String c = ip.de(pedido("10.0.0.1", "qualquer lixo aqui, 203.0.113.5"));
        assertThat(a).isEqualTo(b).isEqualTo(c).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("sem cabecalho, usa o endereco da conexao")
    void semCabecalho() {
        assertThat(new IpDoCliente(1).de(pedido("203.0.113.9", null))).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("dois proxies encadeados andam uma casa para a esquerda")
    void doisProxies() {
        IpDoCliente ip = new IpDoCliente(2);
        assertThat(ip.de(pedido("10.0.0.1", "9.9.9.9, 203.0.113.5, 10.0.0.7")))
                .isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("menos entradas do que proxies declarados cai no endereco da conexao")
    void cabecalhoCurtoDemais() {
        // Alcancar a aplicacao por fora do proxy nao pode virar um IP a escolher.
        IpDoCliente ip = new IpDoCliente(2);
        assertThat(ip.de(pedido("198.51.100.4", "9.9.9.9"))).isEqualTo("198.51.100.4");
    }

    @Test
    @DisplayName("zero proxies: o cabecalho e ignorado por completo")
    void semProxyNenhum() {
        IpDoCliente ip = new IpDoCliente(0);
        assertThat(ip.de(pedido("198.51.100.4", "9.9.9.9, 203.0.113.5")))
                .isEqualTo("198.51.100.4");
    }

    @Test
    @DisplayName("entrada vazia ou so espaco nao vira chave")
    void entradaVazia() {
        IpDoCliente ip = new IpDoCliente(1);
        assertThat(ip.de(pedido("198.51.100.4", "9.9.9.9,   "))).isEqualTo("198.51.100.4");
        assertThat(ip.de(pedido("198.51.100.4", ""))).isEqualTo("198.51.100.4");
    }
}
