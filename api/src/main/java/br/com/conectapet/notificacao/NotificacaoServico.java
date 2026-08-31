package br.com.conectapet.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Padrao outbox.
 *
 * A notificacao e gravada na MESMA transacao do fato que a originou, e enviada
 * depois por job. Assim a resposta a quem encontrou o pet nao espera o servidor
 * de e-mail — e um provedor fora do ar nao desfaz o registro da leitura.
 */
@Service
public class NotificacaoServico {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoServico.class);
    private static final int MAX_TENTATIVAS = 5;
    private static final int LOTE = 50;

    private final NotificacaoRepositorio repo;
    private final Map<Notificacao.Canal, CanalEnvio> canais;
    private final ObjectMapper json;

    public NotificacaoServico(NotificacaoRepositorio repo, List<CanalEnvio> canais, ObjectMapper json) {
        this.repo = repo;
        this.canais = canais.stream().collect(Collectors.toMap(CanalEnvio::canal, c -> c));
        this.json = json;
    }

    /** Enfileira. Participa da transacao de quem chamou, de proposito. */
    @Transactional
    public void enfileirar(Notificacao.Tipo tipo, String destinatario, Map<String, Object> conteudo) {
        try {
            Notificacao n = new Notificacao();
            n.setTipo(tipo);
            n.setDestinatario(destinatario);
            n.setConteudo(json.writeValueAsString(conteudo));
            repo.save(n);
        } catch (Exception e) {
            // Falhar ao enfileirar nao pode derrubar a operacao principal: e pior
            // perder a leitura da tag do que perder o aviso sobre ela.
            log.error("Nao foi possivel enfileirar notificacao do tipo {}", tipo, e);
        }
    }

    @Scheduled(fixedDelayString = "${conectapet.outbox.intervalo-ms:15000}")
    @Transactional
    public void processarPendentes() {
        List<Notificacao> pendentes = repo.pendentes(Instant.now(), PageRequest.of(0, LOTE));
        for (Notificacao n : pendentes) {
            processar(n);
        }
    }

    private void processar(Notificacao n) {
        CanalEnvio canal = canais.get(n.getCanal());
        if (canal == null) {
            n.setStatus(Notificacao.Status.FALHOU);
            n.setUltimoErro("Canal sem implementacao: " + n.getCanal());
            repo.save(n);
            return;
        }
        try {
            canal.enviar(n);
            n.setStatus(Notificacao.Status.ENVIADA);
            n.setProcessadaEm(Instant.now());
        } catch (Exception e) {
            n.setTentativas(n.getTentativas() + 1);
            n.setUltimoErro(resumir(e));
            if (n.getTentativas() >= MAX_TENTATIVAS) {
                n.setStatus(Notificacao.Status.FALHOU);
                // O motivo vai junto. Antes ele so era gravado na coluna
                // ultimo_erro, e a linha de log dizia que algo quebrou sem
                // dizer o que — para descobrir era preciso abrir o banco de
                // producao. Quando as notificacoes param, o log e o primeiro
                // lugar onde se olha, e precisa bastar.
                log.error("Notificacao {} ({}) desistiu apos {} tentativas: {}",
                        n.getId(), n.getTipo(), n.getTentativas(), n.getUltimoErro());
            } else {
                // A primeira falha ja aparece. Com espera de 1, 2, 4 e 8
                // minutos, esperar a desistencia final para saber que ha
                // problema custa um quarto de hora de silencio.
                log.warn("Notificacao {} ({}) falhou na tentativa {}: {}",
                        n.getId(), n.getTipo(), n.getTentativas(), n.getUltimoErro());
                // Espera crescente: 1, 2, 4, 8 minutos.
                long minutos = (long) Math.pow(2, n.getTentativas() - 1);
                n.setProcessarApos(Instant.now().plus(Duration.ofMinutes(minutos)));
            }
        }
        repo.save(n);
    }

    /**
     * Resumo da falha, sem o destinatario.
     *
     * O JavaMail costuma por o endereco que falhou dentro da mensagem da
     * excecao. Como isto agora vai para o log — e nao so para uma coluna do
     * banco — o endereco e mascarado: uma falha de envio nao pode virar uma
     * lista de clientes em texto claro no log da hospedagem.
     */
    private String resumir(Exception e) {
        String m = e.getClass().getSimpleName() + ": " + e.getMessage();
        m = EMAIL.matcher(m).replaceAll("$1***@$2");
        return m.length() > 500 ? m.substring(0, 500) : m;
    }

    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+)");
}
