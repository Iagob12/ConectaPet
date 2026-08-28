package br.com.conectapet.publico;

import java.util.Locale;

/**
 * Distingue robo de gente para decidir a ORIGEM da leitura registrada.
 *
 * Isto nao e uma barreira de seguranca e nao decide se o tutor sera notificado
 * — quem decide isso e o beacon do cliente. Aqui e so para o historico ficar
 * honesto e a metrica de ativacao nao contar preview de link compartilhado.
 *
 * Por isso a lista incompleta e aceitavel: errar aqui rotula uma leitura, nao
 * dispara um alarme falso. Filtrar notificacao por user-agent seria uma corrida
 * que se perde, e foi justamente por isso que separamos registrar de notificar.
 */
public final class DetectorRobo {

    private DetectorRobo() {}

    private static final String[] MARCAS = {
            "bot", "crawler", "spider", "preview", "whatsapp", "telegram",
            "facebookexternalhit", "twitterbot", "linkedinbot", "slackbot",
            "discordbot", "embedly", "quora link preview", "pinterest",
            "vkshare", "skypeuripreview", "applebot", "googlebot", "bingbot",
            "curl", "wget", "python-requests", "headlesschrome"
    };

    public static boolean ehRobo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;   // sem user-agent quase nunca e um navegador de verdade
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String marca : MARCAS) {
            if (ua.contains(marca)) {
                return true;
            }
        }
        return false;
    }
}
