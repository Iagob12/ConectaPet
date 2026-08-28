package br.com.conectapet.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PetRepositorios {

    private PetRepositorios() {}

    public interface Pets extends JpaRepository<Pet, Long> {
        Optional<Pet> findByUuidAndExcluidoEmIsNull(UUID uuid);
        List<Pet> findByUsuarioIdAndExcluidoEmIsNullOrderByCriadoEmDesc(Long usuarioId);
    }

    public interface Saudes extends JpaRepository<PetSaude, Long> {}

    public interface Visibilidades extends JpaRepository<VisibilidadePerfil, Long> {}

    public interface Contatos extends JpaRepository<ContatoEmergencia, Long> {
        List<ContatoEmergencia> findByPetIdOrderByOrdemAscIdAsc(Long petId);
        Optional<ContatoEmergencia> findByUuid(UUID uuid);
        long countByPetId(Long petId);
    }
}
