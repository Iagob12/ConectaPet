package br.com.conectapet.leitura;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LeituraRepositorio extends JpaRepository<Leitura, Long> {

    Page<Leitura> findByPetIdOrderByOcorridaEmDesc(Long petId, Pageable pageable);

    /**
     * Deduplicacao da notificacao: mesma tag, mesmo IP, janela curta.
     * Sem isso, cada aproximacao do mesmo celular vira um push novo.
     */
    @Query("""
           select count(l) from Leitura l
           where l.tagId = :tagId and l.ipHash = :ipHash
             and l.notificadaEm is not null and l.ocorridaEm > :desde
           """)
    long notificacoesRecentes(@Param("tagId") Long tagId, @Param("ipHash") String ipHash,
                              @Param("desde") Instant desde);

    /**
     * Expurgo em duas etapas: coordenada, mensagem e telefone de quem encontrou
     * saem em 90 dias — sao dados de um terceiro que so quis ajudar. O restante
     * da leitura vive 12 meses.
     */
    @Modifying
    @Query("""
           update Leitura l
              set l.latitude = null, l.longitude = null, l.precisaoM = null,
                  l.telefoneDeQuemEncontrou = null, l.mensagemDeQuemEncontrou = null,
                  l.dadosTerceiroExpurgadosEm = :agora
            where l.ocorridaEm < :limite and l.dadosTerceiroExpurgadosEm is null
           """)
    int expurgarDadosDeTerceiro(@Param("limite") Instant limite, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from Leitura l where l.ocorridaEm < :limite")
    int expurgarAntigas(@Param("limite") Instant limite);

    /**
     * So origem CLIENTE. Contar ROBO encheria a metrica com preview de link
     * compartilhado, e SERVIDOR duplicaria a mesma visita ja contada pelo beacon.
     */
    @Query("""
           select count(l) from Leitura l
            where l.origem = br.com.conectapet.leitura.OrigemLeitura.CLIENTE
              and l.ocorridaEm between :de and :ate
           """)
    long contarPorClienteNoPeriodo(@Param("de") Instant de, @Param("ate") Instant ate);
}
