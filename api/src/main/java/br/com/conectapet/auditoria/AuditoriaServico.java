package br.com.conectapet.auditoria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Trilha de auditoria.
 *
 * Obrigatoria para: reivindicacao, transferencia de titularidade, migracao de
 * perfil, desativacao de tag e alteracao de visibilidade — as operacoes que
 * mudam quem responde por um pet ou o que o publico enxerga dele.
 *
 * Participa da transacao de quem chamou: se a operacao for desfeita, o registro
 * de auditoria tambem some. Auditoria de algo que nao aconteceu e pior que
 * nenhuma auditoria.
 */
@Service
public class AuditoriaServico {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServico.class);

    public static final String ACAO_REIVINDICACAO = "TAG_REIVINDICADA";
    public static final String ACAO_TRANSFERENCIA_GERADA = "TRANSFERENCIA_GERADA";
    public static final String ACAO_TRANSFERENCIA_CANCELADA = "TRANSFERENCIA_CANCELADA";
    public static final String ACAO_TRANSFERENCIA_ACEITA = "TRANSFERENCIA_ACEITA";
    public static final String ACAO_PERFIL_MIGRADO = "PERFIL_MIGRADO";
    public static final String ACAO_TAG_DESATIVADA = "TAG_DESATIVADA";
    public static final String ACAO_VISIBILIDADE_ALTERADA = "VISIBILIDADE_ALTERADA";

    private final AuditoriaRepositorio repo;
    private final ObjectMapper json;

    public AuditoriaServico(AuditoriaRepositorio repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @Transactional
    public void registrar(UUID atorUuid, String acao, String recursoTipo, UUID recursoUuid,
                          Map<String, Object> detalhe, String ipHash) {
        try {
            LogAuditoria l = new LogAuditoria();
            l.setAtorUuid(atorUuid);
            l.setAcao(acao);
            l.setRecursoTipo(recursoTipo);
            l.setRecursoUuid(recursoUuid);
            l.setDetalhe(detalhe == null ? null : json.writeValueAsString(detalhe));
            l.setIpHash(ipHash);
            repo.save(l);
        } catch (Exception e) {
            // Nao derruba a operacao principal, mas isso e grave o bastante para
            // aparecer como erro: e uma transferencia sem rastro.
            log.error("Falha ao registrar auditoria: acao={} recurso={}", acao, recursoTipo, e);
        }
    }
}
