package br.com.conectapet.admin;

import br.com.conectapet.leitura.LeituraRepositorio;
import br.com.conectapet.tag.TagRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MetricasServico {

    private final TagRepositorio tags;
    private final LeituraRepositorio leituras;

    public MetricasServico(TagRepositorio tags, LeituraRepositorio leituras) {
        this.tags = tags;
        this.leituras = leituras;
    }

    /**
     * A taxa de ativacao usa como base as tags ENVIADAS, nao as produzidas:
     * tag que ainda esta na caixa nao teve chance de ser ativada, e contar
     * estoque como denominador faria a metrica parecer pior do que e.
     *
     * As leituras contam apenas origem CLIENTE. Incluir ROBO encheria o numero
     * com preview de link compartilhado no WhatsApp.
     */
    @Transactional(readOnly = true)
    public Metricas calcular(Instant de, Instant ate) {
        long produzidas = tags.count();
        long enviadas = tags.contarEnviadas();
        long ativadas = tags.contarAtivadas();
        double taxa = enviadas == 0 ? 0.0 : Math.round((ativadas * 10000.0) / enviadas) / 100.0;

        return new Metricas(produzidas, enviadas, ativadas, taxa,
                leituras.contarPorClienteNoPeriodo(de, ate),
                tags.contarEmModoPerdido());
    }

    public record Metricas(long tagsProduzidas, long tagsEnviadas, long tagsAtivadas,
                           double taxaAtivacao, long leiturasPeriodo, long petsEmModoPerdido) {}
}
