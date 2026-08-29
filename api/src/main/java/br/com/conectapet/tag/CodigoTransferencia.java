package br.com.conectapet.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Codigo de uso unico que transfere a titularidade de uma tag.
 *
 * Guardado como SHA-256: e um portador — quem tem o codigo vira dono. O valor
 * em claro existe apenas na resposta que o dono atual recebe, uma unica vez.
 */
@Entity
@Table(name = "codigos_transferencia")
@Getter
@Setter
@NoArgsConstructor
public class CodigoTransferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "codigo_hash", nullable = false, unique = true)
    private String codigoHash;

    @Column(name = "criado_por", nullable = false)
    private Long criadoPor;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    public boolean utilizavel() {
        return usadoEm == null && canceladoEm == null && expiraEm.isAfter(Instant.now());
    }
}
