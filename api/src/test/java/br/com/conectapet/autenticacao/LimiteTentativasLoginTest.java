package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class LimiteTentativasLoginTest {

    private LimiteTentativasLogin limite(int porConta, int porIp) {
        return new LimiteTentativasLogin(porConta, porIp, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("deixa passar enquanto o teto nao foi atingido")
    void abaixoDoTeto() {
        var l = limite(3, 10);
        l.registrarFalha("ana@teste.com", "ip-1");
        l.registrarFalha("ana@teste.com", "ip-1");

        assertThatNoException().isThrownBy(() -> l.verificar("ana@teste.com", "ip-1"));
    }

    @Test
    @DisplayName("tranca a conta depois do teto de falhas")
    void trancaPorConta() {
        var l = limite(3, 100);
        for (int i = 0; i < 3; i++) {
            l.registrarFalha("ana@teste.com", "ip-" + i);
        }

        // Trocar de IP nao ajuda: o balde da conta e global, como o da tag.
        assertThatThrownBy(() -> l.verificar("ana@teste.com", "ip-completamente-novo"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.BLOQUEADO);
    }

    @Test
    @DisplayName("trancar uma conta nao tranca as outras")
    void naoAfetaOutrasContas() {
        var l = limite(3, 100);
        for (int i = 0; i < 3; i++) {
            l.registrarFalha("ana@teste.com", "ip-1");
        }

        assertThatNoException().isThrownBy(() -> l.verificar("bruno@teste.com", "ip-1"));
    }

    @Test
    @DisplayName("o balde por IP conta espalhamento por varias contas")
    void trancaPorIp() {
        var l = limite(100, 3);
        // Uma senha comum tentada em muitas contas diferentes: o balde da conta
        // nunca enche, e sem o balde de IP isso passaria batido.
        l.registrarFalha("a@teste.com", "ip-atacante");
        l.registrarFalha("b@teste.com", "ip-atacante");
        l.registrarFalha("c@teste.com", "ip-atacante");

        assertThatThrownBy(() -> l.verificar("d@teste.com", "ip-atacante"))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("acertar a senha limpa o balde da conta")
    void sucessoLimpa() {
        var l = limite(3, 100);
        l.registrarFalha("ana@teste.com", "ip-1");
        l.registrarFalha("ana@teste.com", "ip-1");
        l.registrarSucesso("ana@teste.com");
        l.registrarFalha("ana@teste.com", "ip-1");

        // Quem erra duas vezes, lembra, entra e erra de novo depois nao pode
        // estar a uma tentativa do bloqueio.
        assertThatNoException().isThrownBy(() -> l.verificar("ana@teste.com", "ip-1"));
    }

    @Test
    @DisplayName("o e-mail e normalizado: maiuscula e espaco nao driblam o limite")
    void normalizaEmail() {
        var l = limite(3, 100);
        l.registrarFalha("ana@teste.com", "ip-1");
        l.registrarFalha("  ANA@Teste.COM  ", "ip-1");
        l.registrarFalha("Ana@TESTE.com", "ip-1");

        assertThatThrownBy(() -> l.verificar("ana@teste.com", "ip-1"))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("a janela nao e estendida a cada tentativa nova")
    void janelaNaoRenova() {
        // Renovar a cada tentativa deixaria a conta trancada enquanto o
        // atacante insistisse — o bloqueio viraria o ataque.
        var l = new LimiteTentativasLogin(2, 100, Duration.ofMillis(120));
        l.registrarFalha("ana@teste.com", "ip-1");
        l.registrarFalha("ana@teste.com", "ip-1");
        assertThatThrownBy(() -> l.verificar("ana@teste.com", "ip-1"))
                .isInstanceOf(ProblemaException.class);

        await(200);
        assertThatNoException().isThrownBy(() -> l.verificar("ana@teste.com", "ip-1"));
    }

    private void await(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
