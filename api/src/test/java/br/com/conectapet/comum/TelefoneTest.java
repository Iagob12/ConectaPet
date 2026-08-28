package br.com.conectapet.comum;

import br.com.conectapet.comum.util.Telefone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneTest {

    @Test
    @DisplayName("normaliza as formas que a pessoa realmente digita")
    void formasComuns() {
        assertThat(Telefone.paraE164("(11) 99999-0000")).isEqualTo("+5511999990000");
        assertThat(Telefone.paraE164("11999990000")).isEqualTo("+5511999990000");
        assertThat(Telefone.paraE164("+55 11 99999-0000")).isEqualTo("+5511999990000");
        assertThat(Telefone.paraE164("5511999990000")).isEqualTo("+5511999990000");
        // zero de operadora com 12 digitos: leitura unica, aceito
        assertThat(Telefone.paraE164("011 99999-0000")).isEqualTo("+5511999990000");
        // fixo, 10 digitos
        assertThat(Telefone.paraE164("(11) 3333-0000")).isEqualTo("+551133330000");
    }

    @Test
    @DisplayName("recusa numero sem forma de telefone brasileiro")
    void recusaInvalido() {
        assertThat(Telefone.paraE164("99999-0000")).isNull();        // sem DDD
        assertThat(Telefone.paraE164("(11) 89999-0000")).isNull();   // celular sem o nono digito
        assertThat(Telefone.paraE164("(01) 99999-0000")).isNull();   // DDD inexistente
        // Ambiguo: seria zero + DDD 19 + fixo, ou DDD 01 + celular? Aceitar
        // gravaria um numero valido e DIFERENTE do que a pessoa quis digitar.
        assertThat(Telefone.paraE164("01999990000")).isNull();
        assertThat(Telefone.paraE164("abc")).isNull();
        assertThat(Telefone.paraE164(null)).isNull();
    }

    @Test
    @DisplayName("exibicao e wa.me saem do mesmo E.164")
    void derivados() {
        String e164 = Telefone.paraE164("11999990000");

        assertThat(Telefone.paraExibicao(e164)).isEqualTo("(11) 99999-0000");
        assertThat(Telefone.paraWhatsApp(e164)).isEqualTo("5511999990000");

        assertThat(Telefone.paraExibicao(Telefone.paraE164("1133330000"))).isEqualTo("(11) 3333-0000");
    }
}
