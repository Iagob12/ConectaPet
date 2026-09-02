package br.com.conectapet.tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepositorio extends JpaRepository<Tag, Long> {

    Optional<Tag> findByCodigoPublico(String codigoPublico);

    /**
     * A confirmacao final precisa serializar duas pessoas que tentem concluir a
     * mesma tag ao mesmo tempo. Sem o lock, ambas poderiam ler CRIADA antes de
     * uma delas gravar e a ultima transacao acabaria trocando o dono.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tag t where t.codigoPublico = :codigoPublico")
    Optional<Tag> findByCodigoPublicoParaAtualizar(@Param("codigoPublico") String codigoPublico);

    Optional<Tag> findByUuid(UUID uuid);

    List<Tag> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    List<Tag> findByPetId(Long petId);

    List<Tag> findByLoteId(Long loteId);

    boolean existsByCodigoPublico(String codigoPublico);

    // ---- Consultas administrativas ----------------------------------------

    /** Filtros opcionais: parametro nulo nao restringe. */
    @Query("""
           select t from Tag t
            where (:status is null or t.status = :status)
              and (:loteId is null or t.loteId = :loteId)
            order by t.id desc
           """)
    Page<Tag> buscar(@Param("status") StatusTag status, @Param("loteId") Long loteId, Pageable pagina);

    @Query("select count(t) from Tag t where t.enviadaEm is not null")
    long contarEnviadas();

    /** Ativada = ja teve dono. Reivindicada conta, mesmo sem perfil ainda. */
    @Query("select count(t) from Tag t where t.reivindicadaEm is not null")
    long contarAtivadas();

    @Query("select count(distinct t.petId) from Tag t where t.modoPerdido = true and t.petId is not null")
    long contarEmModoPerdido();
}
