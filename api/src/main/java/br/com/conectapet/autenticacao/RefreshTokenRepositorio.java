package br.com.conectapet.autenticacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositorio extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revogadoEm = :agora where r.familia = :familia and r.revogadoEm is null")
    int revogarFamilia(@Param("familia") UUID familia, @Param("agora") Instant agora);

    @Modifying
    @Query("update RefreshToken r set r.revogadoEm = :agora where r.usuarioId = :usuarioId and r.revogadoEm is null")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);
}
