package br.com.conectapet.tag;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID uuid;

    @Column(name = "codigo_publico", nullable = false, unique = true, updatable = false, length = 10)
    private String codigoPublico;

    @Column(name = "codigo_ativacao_hash", nullable = false)
    private String codigoAtivacaoHash;

    /** Em claro apenas enquanto o lote esta NAO_CONFIRMADO. Apagado na confirmacao. */
    @Column(name = "codigo_ativacao_claro", length = 8)
    private String codigoAtivacaoClaro;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModeloTag modelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusTag status = StatusTag.CRIADA;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "pet_id")
    private Long petId;

    /** Modo perdido e estado da tag: e ela que a pessoa na rua le. */
    @Column(name = "modo_perdido", nullable = false)
    private boolean modoPerdido = false;

    @Column(name = "reivindicada_em")
    private Instant reivindicadaEm;

    @Column(name = "enviada_em")
    private Instant enviadaEm;

    @Column(name = "desativada_em")
    private Instant desativadaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (uuid == null) uuid = UUID.randomUUID();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    /** Unica porta de troca de status. Transicao invalida vira 409. */
    public void transitarPara(StatusTag destino) {
        if (status == destino) {
            return;
        }
        if (!status.podeIrPara(destino)) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Nao e possivel ir de " + status + " para " + destino + ".");
        }
        status = destino;
        if (destino == StatusTag.DESATIVADA) {
            desativadaEm = Instant.now();
        }
    }

    public boolean pertenceA(Long outroUsuarioId) {
        return usuarioId != null && usuarioId.equals(outroUsuarioId);
    }
}
