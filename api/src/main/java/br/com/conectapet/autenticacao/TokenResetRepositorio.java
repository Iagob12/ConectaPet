package br.com.conectapet.autenticacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TokenResetRepositorio extends JpaRepository<TokenResetSenha, Long> {

    Optional<TokenResetSenha> findByTokenHash(String tokenHash);

    /**
     * Invalida os pedidos anteriores ao criar um novo.
     *
     * Sem isto, pedir "esqueci minha senha" tres vezes deixaria tres links
     * validos circulando por e-mail ao mesmo tempo — e o mais antigo, que pode
     * ter vazado, continuaria abrindo a conta.
     */
    @Modifying
    @Query("update TokenResetSenha t set t.usadoEm = :agora "
         + "where t.usuarioId = :usuarioId and t.usadoEm is null")
    void invalidarPendentes(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);
}
