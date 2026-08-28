package br.com.conectapet.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepositorio extends JpaRepository<Tag, Long> {

    Optional<Tag> findByCodigoPublico(String codigoPublico);

    Optional<Tag> findByUuid(UUID uuid);

    List<Tag> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    List<Tag> findByPetId(Long petId);

    List<Tag> findByLoteId(Long loteId);

    boolean existsByCodigoPublico(String codigoPublico);
}
