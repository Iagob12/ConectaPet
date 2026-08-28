package br.com.conectapet.leitura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Retencao em duas etapas.
 *
 * Coordenada, mensagem e telefone de quem encontrou saem em 90 dias: sao dados
 * pessoais de um terceiro que nao e cliente e so quis ajudar, e guardar
 * coordenada exata por um ano e dificil de justificar.
 *
 * O restante da leitura vive 12 meses, para o tutor ter historico.
 */
@Component
public class ExpurgoLeituras {

    private static final Logger log = LoggerFactory.getLogger(ExpurgoLeituras.class);

    private final LeituraRepositorio leituras;
    private final Duration retencaoTerceiro;
    private final Duration retencaoLeitura;

    public ExpurgoLeituras(LeituraRepositorio leituras,
                           @Value("${conectapet.retencao.dados-de-terceiro}") Duration retencaoTerceiro,
                           @Value("${conectapet.retencao.leitura}") Duration retencaoLeitura) {
        this.leituras = leituras;
        this.retencaoTerceiro = retencaoTerceiro;
        this.retencaoLeitura = retencaoLeitura;
    }

    @Scheduled(cron = "${conectapet.retencao.cron:0 30 3 * * *}")
    @Transactional
    public void executar() {
        Instant agora = Instant.now();

        int anonimizadas = leituras.expurgarDadosDeTerceiro(agora.minus(retencaoTerceiro), agora);
        int removidas = leituras.expurgarAntigas(agora.minus(retencaoLeitura));

        if (anonimizadas > 0 || removidas > 0) {
            log.info("Expurgo de leituras: {} anonimizadas, {} removidas", anonimizadas, removidas);
        }
    }
}
