package br.com.conectapet.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContatoRepositorio extends JpaRepository<ContatoEmergencia, Long> {

    List<ContatoEmergencia> findByPetIdOrderByOrdemAscIdAsc(Long petId);

    Optional<ContatoEmergencia> findByUuid(UUID uuid);

    long countByPetId(Long petId);
}
