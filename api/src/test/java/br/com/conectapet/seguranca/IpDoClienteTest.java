package br.com.conectapet.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    private static final String REAL = "203.0.113.5";

    private MockHttpServletRequest pedido(String remoto, String encaminhado) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoto);
        if (encaminhado != null) {
            r.addHeader("X-Forwarded-For", encaminhado);
        }
        return r;
    }

    @Nested
    @DisplayName("varredura automatica (o padrao)")
    class Automatico {

        private final IpDoCliente ip = new IpDoCliente(IpDoCliente.AUTOMATICO);

        @Test
        @DisplayName("acha o cliente mesmo sem saber quantos proxies existem")
        void independeDaTopologia() {
            // Um salto interno...
            assertThat(ip.de(pedido("10.0.0.1", "9.9.9.9, " + REAL + ", 10.0.0.7"))).isEqualTo(REAL);
            // ...tres saltos internos, sem mudar nada na configuracao.
            assertThat(ip.de(pedido("10.0.0.1", "9.9.9.9, " + REAL + ", 10.0.0.7, 172.17.0.2, 192.168.1.9")))
                    .isEqualTo(REAL);
            // ...e nenhum.
            assertThat(ip.de(pedido("10.0.0.1", "9.9.9.9, " + REAL))).isEqualTo(REAL);
        }

        @Test
        @DisplayName("forjar o cabecalho nao muda a chave do limite")
        void forjaNaoMudaNada() {
            String a = ip.de(pedido("10.0.0.1", "1.1.1.1, " + REAL + ", 10.0.0.7"));
            String b = ip.de(pedido("10.0.0.1", "2.2.2.2, " + REAL + ", 10.0.0.7"));
            String c = ip.de(pedido("10.0.0.1", "lixo, mais lixo, " + REAL + ", 10.0.0.7"));
            assertThat(a).isEqualTo(b).isEqualTo(c).isEqualTo(REAL);
        }

        @Test
        @DisplayName("forjar um endereco PUBLICO tambem nao adianta")
        void forjaPublicaNaoAdianta() {
            // O que o cliente escreve fica sempre a esquerda do endereco real,
            // e a varredura vem da direita: encontra o verdadeiro antes.
            assertThat(ip.de(pedido("10.0.0.1", "8.8.8.8, " + REAL + ", 10.0.0.7"))).isEqualTo(REAL);
        }

        @Test
        @DisplayName("cadeia so com enderecos internos cai no endereco da conexao")
        void tudoInterno() {
            assertThat(ip.de(pedido("198.51.100.4", "10.0.0.1, 172.16.5.4"))).isEqualTo("198.51.100.4");
        }

        @Test
        @DisplayName("sem cabecalho, usa o endereco da conexao")
        void semCabecalho() {
            assertThat(ip.de(pedido("203.0.113.9", null))).isEqualTo("203.0.113.9");
            assertThat(ip.de(pedido("203.0.113.9", ""))).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("entradas vazias e espacos nao viram chave")
        void entradasVazias() {
            assertThat(ip.de(pedido("198.51.100.4", "9.9.9.9,   ,  " + REAL + " ,  "))).isEqualTo(REAL);
        }
    }

    @Nested
    @DisplayName("contagem explicita, para quem sabe a topologia")
    class Explicito {

        @Test
        @DisplayName("um proxy pega a ultima entrada")
        void umProxy() {
            assertThat(new IpDoCliente(1).de(pedido("10.0.0.1", "9.9.9.9, " + REAL))).isEqualTo(REAL);
        }

        @Test
        @DisplayName("dois proxies andam uma casa para a esquerda")
        void doisProxies() {
            assertThat(new IpDoCliente(2).de(pedido("10.0.0.1", "9.9.9.9, " + REAL + ", 10.0.0.7")))
                    .isEqualTo(REAL);
        }

        @Test
        @DisplayName("cabecalho mais curto que o esperado cai no endereco da conexao")
        void curtoDemais() {
            assertThat(new IpDoCliente(2).de(pedido("198.51.100.4", "9.9.9.9"))).isEqualTo("198.51.100.4");
        }

        @Test
        @DisplayName("zero proxies ignora o cabecalho por completo")
        void semProxyNenhum() {
            assertThat(new IpDoCliente(0).de(pedido("198.51.100.4", "9.9.9.9, " + REAL)))
                    .isEqualTo("198.51.100.4");
        }
    }

    @Test
    @DisplayName("classifica as faixas privadas que a hospedagem usa")
    void faixasInternas() {
        for (String s : new String[]{"10.0.0.1", "127.0.0.1", "192.168.1.1", "169.254.1.1",
                                     "172.16.0.1", "172.31.255.255", "::1", "fd00::1", "fe80::1"}) {
            assertThat(IpDoCliente.interno(s)).as(s).isTrue();
        }
        for (String s : new String[]{"203.0.113.5", "8.8.8.8", "172.15.0.1", "172.32.0.1", "2001:db8::1"}) {
            assertThat(IpDoCliente.interno(s)).as(s).isFalse();
        }
    }
}
