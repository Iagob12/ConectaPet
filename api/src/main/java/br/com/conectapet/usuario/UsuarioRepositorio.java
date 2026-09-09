package br.com.conectapet.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    /** O e-mail e normalizado em minusculas na aplicacao, nao no banco. */
    Optional<Usuario> findByEmailAndExcluidoEmIsNull(String email);

    Optional<Usuario> findByGoogleSubjectAndExcluidoEmIsNull(String googleSubject);

    Optional<Usuario> findByUuidAndExcluidoEmIsNull(UUID uuid);

    boolean existsByEmail(String email);

    /**
     * Visao administrativa das contas ainda existentes.
     *
     * locate(), em vez de LIKE, faz com que '%' e '_' digitados na busca sejam
     * tratados como texto comum. Contas excluidas ou anonimizadas nunca voltam
     * para a interface, mesmo para um administrador.
     */
    @Query("""
            select u from Usuario u
             where u.excluidoEm is null
               and u.anonimizadoEm is null
               and (:busca = ''
                    or locate(lower(:busca), lower(u.nome)) > 0
                    or locate(lower(:busca), lower(u.email)) > 0
                    or locate(:busca, coalesce(u.telefonePrincipal, '')) > 0)
            """)
    Page<Usuario> buscarContas(@Param("busca") String busca, Pageable pageable);
}
