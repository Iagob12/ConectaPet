package br.com.conectapet.autenticacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TokenVerificacaoRepositorio extends JpaRepository<TokenVerificacaoEmail, Long> {

    Optional<TokenVerificacaoEmail> findByTokenHash(String tokenHash);

    /** Pedir outro link invalida o anterior, pelo mesmo motivo do reset de senha. */
    @Modifying
    @Query("update TokenVerificacaoEmail t set t.usadoEm = :agora "
         + "where t.usuarioId = :usuarioId and t.usadoEm is null")
    void invalidarPendentes(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);
}
