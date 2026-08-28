package br.com.conectapet.assinatura;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "assinaturas")
@Getter
@Setter
@NoArgsConstructor
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plano plano = Plano.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAssinatura status = StatusAssinatura.ATIVA;

    @Column(name = "iniciada_em", nullable = false)
    private Instant iniciadaEm;

    @Column(name = "expira_em")
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (iniciadaEm == null) iniciadaEm = agora;
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    /**
     * Plano vencido NAO esconde contato nem saude do publico — a promessa do
     * site e que a tag funciona para sempre. O vencimento governa apenas alerta
     * imediato, localizacao aproximada e teto de contatos de emergencia.
     */
    public boolean plusVigente() {
        return plano == Plano.PLUS
                && status == StatusAssinatura.ATIVA
                && (expiraEm == null || expiraEm.isAfter(Instant.now()));
    }
}
