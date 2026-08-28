package br.com.conectapet.notificacao;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificacaoRepositorio extends JpaRepository<Notificacao, Long> {

    @Query("""
           select n from Notificacao n
            where n.status = br.com.conectapet.notificacao.Notificacao$Status.PENDENTE
              and n.processarApos <= :agora
            order by n.processarApos asc
           """)
    List<Notificacao> pendentes(@Param("agora") Instant agora, Pageable limite);
}
