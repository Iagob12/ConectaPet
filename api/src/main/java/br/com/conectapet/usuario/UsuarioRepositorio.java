package br.com.conectapet.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    /** O e-mail e normalizado em minusculas na aplicacao, nao no banco. */
    Optional<Usuario> findByEmailAndExcluidoEmIsNull(String email);

    Optional<Usuario> findByUuidAndExcluidoEmIsNull(UUID uuid);

    boolean existsByEmail(String email);
}
