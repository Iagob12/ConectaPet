package br.com.conectapet.tag;

import br.com.conectapet.comum.erro.ProblemaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MaquinaEstadosTagTest {

    private Tag tagEm(StatusTag status) {
        Tag t = new Tag();
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("percorre o caminho feliz ate o modo perdido e de volta")
    void caminhoFeliz() {
        Tag t = tagEm(StatusTag.CRIADA);
        t.transitarPara(StatusTag.ENVIADA);
        t.transitarPara(StatusTag.REIVINDICADA);
        t.transitarPara(StatusTag.ATIVA);
        t.transitarPara(StatusTag.MODO_PERDIDO);
        t.transitarPara(StatusTag.ATIVA);
        assertThat(t.getStatus()).isEqualTo(StatusTag.ATIVA);
    }

    @Test
    @DisplayName("DESATIVADA e terminal: nao volta a ficar ativa sozinha")
    void desativadaEhTerminal() {
        Tag t = tagEm(StatusTag.ATIVA);
        t.transitarPara(StatusTag.DESATIVADA);

        assertThatThrownBy(() -> t.transitarPara(StatusTag.ATIVA))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> t.transitarPara(StatusTag.REIVINDICADA))
                .isInstanceOf(ProblemaException.class);
        assertThat(t.getDesativadaEm()).isNotNull();
    }

    @Test
    @DisplayName("nao pula de CRIADA direto para ATIVA")
    void naoPulaParaAtiva() {
        assertThatThrownBy(() -> tagEm(StatusTag.CRIADA).transitarPara(StatusTag.ATIVA))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("so CRIADA e ENVIADA sao reivindicaveis")
    void reivindicavel() {
        assertThat(StatusTag.CRIADA.reivindicavel()).isTrue();
        assertThat(StatusTag.ENVIADA.reivindicavel()).isTrue();
        assertThat(StatusTag.REIVINDICADA.reivindicavel()).isFalse();
        assertThat(StatusTag.ATIVA.reivindicavel()).isFalse();
        assertThat(StatusTag.DESATIVADA.reivindicavel()).isFalse();
    }

    @Test
    @DisplayName("REIVINDICADA nao exibe perfil publico: responde como nao ativada")
    void apenasAtivaExibePerfil() {
        assertThat(StatusTag.ATIVA.exibePerfil()).isTrue();
        assertThat(StatusTag.MODO_PERDIDO.exibePerfil()).isTrue();
        assertThat(StatusTag.REIVINDICADA.exibePerfil()).isFalse();
        assertThat(StatusTag.ENVIADA.exibePerfil()).isFalse();
        assertThat(StatusTag.DESATIVADA.exibePerfil()).isFalse();
    }

    @Test
    @DisplayName("transitar para o mesmo estado e inofensivo")
    void mesmoEstado() {
        Tag t = tagEm(StatusTag.ATIVA);
        t.transitarPara(StatusTag.ATIVA);
        assertThat(t.getStatus()).isEqualTo(StatusTag.ATIVA);
    }
}
