package br.com.conectapet.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetRepositorio extends JpaRepository<Pet, Long> {

    Optional<Pet> findByUuidAndExcluidoEmIsNull(UUID uuid);

    List<Pet> findByUsuarioIdAndExcluidoEmIsNullOrderByCriadoEmDesc(Long usuarioId);
}
