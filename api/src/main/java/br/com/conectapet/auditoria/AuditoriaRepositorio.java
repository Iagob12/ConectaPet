package br.com.conectapet.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditoriaRepositorio extends JpaRepository<LogAuditoria, Long> {

    List<LogAuditoria> findByRecursoTipoAndRecursoUuidOrderByOcorridaEmDesc(String tipo, UUID uuid);

    List<LogAuditoria> findByAtorUuidOrderByOcorridaEmDesc(UUID atorUuid);
}
