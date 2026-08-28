package br.com.conectapet.comum;

import br.com.conectapet.publico.DetectorRobo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorRoboTest {

    @Test
    @DisplayName("reconhece os robos de preview que compartilham link")
    void robos() {
        assertThat(DetectorRobo.ehRobo("WhatsApp/2.23.20.0")).isTrue();
        assertThat(DetectorRobo.ehRobo("facebookexternalhit/1.1")).isTrue();
        assertThat(DetectorRobo.ehRobo("TelegramBot (like TwitterBot)")).isTrue();
        assertThat(DetectorRobo.ehRobo("LinkedInBot/1.0")).isTrue();
        assertThat(DetectorRobo.ehRobo("curl/8.4.0")).isTrue();
    }

    @Test
    @DisplayName("user-agent ausente conta como robo: quase nunca e navegador de verdade")
    void semUserAgent() {
        assertThat(DetectorRobo.ehRobo(null)).isTrue();
        assertThat(DetectorRobo.ehRobo("")).isTrue();
    }

    @Test
    @DisplayName("celular de verdade nao e confundido com robo")
    void navegadores() {
        assertThat(DetectorRobo.ehRobo(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")).isFalse();
        assertThat(DetectorRobo.ehRobo(
                "Mozilla/5.0 (Linux; Android 13; SM-A536E) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")).isFalse();
    }
}
