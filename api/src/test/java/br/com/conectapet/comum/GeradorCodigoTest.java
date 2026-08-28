package br.com.conectapet.comum;

import br.com.conectapet.comum.util.GeradorCodigo;
import br.com.conectapet.comum.util.Hashes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class GeradorCodigoTest {

    private final GeradorCodigo gerador = new GeradorCodigo();

    @Test
    @DisplayName("nao usa 0 O I 1 L U — os caracteres que se confundem ao ler o cartao")
    void alfabetoSemAmbiguos() {
        assertThat(GeradorCodigo.ALFABETO).doesNotContain("0", "O", "I", "1", "L", "U");
        assertThat(GeradorCodigo.ALFABETO).hasSize(30);

        for (int i = 0; i < 500; i++) {
            assertThat(gerador.codigoPublico()).matches("^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{10}$");
            assertThat(gerador.codigoAtivacao()).matches("^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}$");
        }
    }

    @Test
    @DisplayName("nao e sequencial: codigo previsivel permitiria varrer o site e coletar todos os telefones")
    void naoSequencial() {
        Set<String> vistos = new HashSet<>();
        String anterior = null;
        for (int i = 0; i < 2000; i++) {
            String c = gerador.codigoPublico();
            assertThat(vistos.add(c)).as("codigo repetido em 2000 geracoes").isTrue();
            if (anterior != null) {
                assertThat(c).isNotEqualTo(proximoSequencial(anterior));
            }
            anterior = c;
        }
    }

    @Test
    @DisplayName("normaliza caixa e espacos, mas nao adivinha caractere ambiguo")
    void normalizacao() {
        assertThat(GeradorCodigo.normalizar("  k7m2 pq9xvb ")).isEqualTo("K7M2PQ9XVB");

        // 0 e O sao coisas diferentes: trocar automaticamente esconderia um erro
        // de leitura do cartao e faria a pessoa gastar tentativa sem entender.
        assertThat(GeradorCodigo.formaValida("K7M2PQ9XV0", 10)).isFalse();
        assertThat(GeradorCodigo.formaValida("K7M2PQ9XVL", 10)).isFalse();
    }

    @Test
    @DisplayName("forma invalida e detectada antes de gastar tentativa")
    void formaValida() {
        assertThat(GeradorCodigo.formaValida("K7M2PQ9XVB", 10)).isTrue();
        assertThat(GeradorCodigo.formaValida("K7M2PQ9XV", 10)).isFalse();
        assertThat(GeradorCodigo.formaValida(null, 10)).isFalse();
        assertThat(GeradorCodigo.formaValida("", 10)).isFalse();
    }

    @Test
    @DisplayName("pimentas diferentes produzem pseudonimos diferentes para o mesmo IP")
    void ipDependeDaPimenta() {
        String a = Hashes.ipPseudonimo("189.10.20.30", "pimenta-a");
        String b = Hashes.ipPseudonimo("189.10.20.30", "pimenta-b");

        assertThat(a).isNotEqualTo(b);
        assertThat(a).hasSize(32).doesNotContain("189");
        // estavel com a mesma pimenta, senao o limite por IP nao funcionaria
        assertThat(a).isEqualTo(Hashes.ipPseudonimo("189.10.20.30", "pimenta-a"));
        assertThat(Hashes.ipPseudonimo(null, "pimenta-a")).isNull();
    }

    private String proximoSequencial(String codigo) {
        char[] c = codigo.toCharArray();
        int idx = GeradorCodigo.ALFABETO.indexOf(c[c.length - 1]);
        c[c.length - 1] = GeradorCodigo.ALFABETO.charAt((idx + 1) % GeradorCodigo.ALFABETO.length());
        return new String(c);
    }
}
