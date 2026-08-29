package br.com.conectapet.auditoria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "log_auditoria")
@Getter
@Setter
@NoArgsConstructor
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Pseudonimo estavel do ator. Guardamos o UUID, nunca e-mail nem telefone:
     * assim a auditoria sobrevive a anonimizacao da conta sem virar dado pessoal.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "ator_uuid", columnDefinition = "BINARY(16)")
    private UUID atorUuid;

    @Column(nullable = false, length = 60)
    private String acao;

    @Column(name = "recurso_tipo", nullable = false, length = 40)
    private String recursoTipo;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "recurso_uuid", columnDefinition = "BINARY(16)")
    private UUID recursoUuid;

    /** Nunca senha, token, codigo de ativacao, telefone ou e-mail. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private String detalhe;

    @Column(name = "ip_hash", columnDefinition = "CHAR(32)")
    private String ipHash;

    @Column(name = "ocorrida_em", nullable = false)
    private Instant ocorridaEm;

    @PrePersist
    void aoCriar() {
        if (ocorridaEm == null) ocorridaEm = Instant.now();
    }
}
