package br.com.conectapet.autenticacao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Pedido de redefinicao de senha.
 *
 * Guarda apenas o hash do token, pelo mesmo motivo do refresh: um vazamento do
 * banco nao pode entregar um passe de entrada em cada conta. O token em claro
 * existe uma vez so, no link que sai por e-mail.
 */
@Entity
@Table(name = "tokens_reset_senha")
@Getter
@Setter
@NoArgsConstructor
public class TokenResetSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean utilizavel() {
        return usadoEm == null && expiraEm.isAfter(Instant.now());
    }
}
