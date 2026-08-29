package br.com.conectapet.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CodigoTransferenciaRepositorio extends JpaRepository<CodigoTransferencia, Long> {

    Optional<CodigoTransferencia> findByCodigoHash(String codigoHash);

    /**
     * Consome o codigo de forma atomica.
     *
     * Duas pessoas com o mesmo codigo chamando ao mesmo tempo: apenas uma
     * atualizacao afeta linha, e so ela prossegue. Ler-depois-gravar deixaria
     * uma janela em que as duas passariam e a tag trocaria de dono duas vezes.
     */
    @Modifying
    @Query("""
           update CodigoTransferencia c set c.usadoEm = :agora
            where c.id = :id and c.usadoEm is null and c.canceladoEm is null and c.expiraEm > :agora
           """)
    int consumir(@Param("id") Long id, @Param("agora") Instant agora);

    @Modifying
    @Query("""
           update CodigoTransferencia c set c.canceladoEm = :agora
            where c.tagId = :tagId and c.usadoEm is null and c.canceladoEm is null
           """)
    int cancelarPendentesDaTag(@Param("tagId") Long tagId, @Param("agora") Instant agora);

    @Query("""
           select count(c) from CodigoTransferencia c
            where c.tagId = :tagId and c.usadoEm is null and c.canceladoEm is null and c.expiraEm > :agora
           """)
    long pendentesDaTag(@Param("tagId") Long tagId, @Param("agora") Instant agora);
}
