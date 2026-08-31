package br.com.conectapet.foto;

import br.com.conectapet.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra o MySQL de verdade, porque o que se testa aqui e o comportamento do
 * banco: BLOB que volta byte a byte igual, REPLACE que substitui em vez de
 * quebrar, e apagar que leva as tres variantes juntas.
 */
@TestPropertySource(properties = "conectapet.foto.armazenamento=banco")
class ArmazenamentoBancoIT extends TesteIntegracao {

    @Autowired
    private JdbcTemplate jdbc;

    private ArmazenamentoBanco armazenamento() {
        return new ArmazenamentoBanco(jdbc);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("o que entra e exatamente o que sai")
    void guardaEle() {
        ArmazenamentoBanco a = armazenamento();
        // Bytes com zeros e valores altos: JPEG tem os dois, e uma coluna de
        // texto por engano truncaria no primeiro zero sem avisar.
        byte[] conteudo = new byte[]{0x00, (byte) 0xFF, 0x10, 0x00, (byte) 0x80, 0x7F};

        a.guardar("chave-a", ArmazenamentoFotos.Variante.MEDIA, conteudo);

        assertThat(a.ler("chave-a", ArmazenamentoFotos.Variante.MEDIA))
                .contains(conteudo);
    }

    @Test
    @DisplayName("trocar a foto substitui, em vez de quebrar na chave primaria")
    void trocarSubstitui() {
        ArmazenamentoBanco a = armazenamento();
        a.guardar("chave-b", ArmazenamentoFotos.Variante.PEQUENA, bytes("antiga"));
        a.guardar("chave-b", ArmazenamentoFotos.Variante.PEQUENA, bytes("nova"));

        assertThat(a.ler("chave-b", ArmazenamentoFotos.Variante.PEQUENA))
                .contains(bytes("nova"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fotos_arquivo WHERE chave = ?", Integer.class, "chave-b"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("as variantes nao se atrapalham")
    void variantesIndependentes() {
        ArmazenamentoBanco a = armazenamento();
        a.guardar("chave-c", ArmazenamentoFotos.Variante.PEQUENA, bytes("p"));
        a.guardar("chave-c", ArmazenamentoFotos.Variante.MEDIA, bytes("m"));
        a.guardar("chave-c", ArmazenamentoFotos.Variante.ORIGINAL, bytes("o"));

        assertThat(a.ler("chave-c", ArmazenamentoFotos.Variante.PEQUENA)).contains(bytes("p"));
        assertThat(a.ler("chave-c", ArmazenamentoFotos.Variante.MEDIA)).contains(bytes("m"));
        assertThat(a.ler("chave-c", ArmazenamentoFotos.Variante.ORIGINAL)).contains(bytes("o"));
    }

    @Test
    @DisplayName("apagar leva as tres variantes, e so as daquela chave")
    void apagarLevaTudo() {
        ArmazenamentoBanco a = armazenamento();
        for (ArmazenamentoFotos.Variante v : ArmazenamentoFotos.Variante.values()) {
            a.guardar("some", v, bytes("x"));
            a.guardar("fica", v, bytes("y"));
        }

        a.apagar("some");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fotos_arquivo WHERE chave = ?", Integer.class, "some"))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fotos_arquivo WHERE chave = ?", Integer.class, "fica"))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("foto que nao existe devolve vazio, e nao excecao")
    void inexistente() {
        // O pet pode simplesmente nao ter foto. Isso e um caso normal, nao erro.
        assertThat(armazenamento().ler("nao-existe", ArmazenamentoFotos.Variante.MEDIA))
                .isEmpty();
    }

    @Test
    @DisplayName("aguenta uma foto do tamanho real, com folga")
    void tamanhoReal() {
        // As tres variantes de um pet somam ~28 kB. 500 kB e vinte vezes a
        // maior delas — se MEDIUMBLOB estivesse errado, quebraria aqui.
        byte[] grande = new byte[500 * 1024];
        for (int i = 0; i < grande.length; i++) {
            grande[i] = (byte) (i % 251);
        }
        ArmazenamentoBanco a = armazenamento();
        a.guardar("grande", ArmazenamentoFotos.Variante.ORIGINAL, grande);

        assertThat(a.ler("grande", ArmazenamentoFotos.Variante.ORIGINAL)).contains(grande);
    }
}
