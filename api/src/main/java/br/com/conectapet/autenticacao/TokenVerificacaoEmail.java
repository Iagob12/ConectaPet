package br.com.conectapet.autenticacao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Confirmacao de que o e-mail cadastrado e mesmo da pessoa.
 *
 * `emailAlvo` guarda para qual endereco o link foi emitido, e nao so o usuario:
 * quando a troca de e-mail existir, um link antigo nao pode confirmar um
 * endereco que ja mudou desde entao.
 */
@Entity
@Table(name = "tokens_verificacao_email")
@Getter
@Setter
@NoArgsConstructor
public class TokenVerificacaoEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "email_alvo", nullable = false)
    private String emailAlvo;

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
