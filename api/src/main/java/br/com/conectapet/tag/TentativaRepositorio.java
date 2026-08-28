package br.com.conectapet.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TentativaRepositorio extends JpaRepository<TentativaReivindicacao, Long> {

    /**
     * Balde GLOBAL por codigo — sem IP na chave de proposito.
     * Com IP na chave, um atacante com 200 enderecos faria 1.000 tentativas por
     * hora no mesmo codigo e o limite nao protegeria nada.
     */
    @Query("""
           select count(t) from TentativaReivindicacao t
           where t.codigoPublico = :codigo and t.sucesso = false and t.ocorridaEm > :desde
           """)
    long falhasPorCodigo(@Param("codigo") String codigo, @Param("desde") Instant desde);

    /** Balde por IP, independente do anterior. */
    @Query("""
           select count(t) from TentativaReivindicacao t
           where t.ipHash = :ipHash and t.sucesso = false and t.ocorridaEm > :desde
           """)
    long falhasPorIp(@Param("ipHash") String ipHash, @Param("desde") Instant desde);
}
