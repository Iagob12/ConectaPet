package br.com.conectapet.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador exposto na API. O id sequencial nunca sai daqui. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    /** Guardado em E.164; a forma de exibicao e derivada na aplicacao. */
    @Column(name = "telefone_principal")
    private String telefonePrincipal;

    @Column(name = "telefone_secundario")
    private String telefoneSecundario;

    /** Nem sempre e o telefone principal. Faltava no modelo original. */
    private String whatsapp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel = Papel.TUTOR;

    @Column(name = "email_verificado_em")
    private Instant emailVerificadoEm;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

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

    public boolean emailVerificado() {
        return emailVerificadoEm != null;
    }
}
