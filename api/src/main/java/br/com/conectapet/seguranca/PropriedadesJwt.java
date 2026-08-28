package br.com.conectapet.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "conectapet.jwt")
public record PropriedadesJwt(
        String segredo,
        Duration duracaoAcesso,
        Duration duracaoRefresh,
        /**
         * Janela em que o refresh imediatamente anterior continua aceito sem ser
         * tratado como reuso. Sem ela, duas abas renovando ao mesmo tempo
         * derrubam a sessao de um usuario legitimo.
         */
        Duration toleranciaRotacao
) {}
