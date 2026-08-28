package br.com.conectapet.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro de tentativa de reivindicacao.
 *
 * Vive no banco, e nao em memoria, porque o limite de 5 por hora precisa
 * sobreviver a restart: em memoria, bastaria esperar o container reiniciar
 * — ou disparar um deploy — para zerar o contador.
 */
@Entity
@Table(name = "tentativas_reivindicacao")
@Getter
@Setter
@NoArgsConstructor
public class TentativaReivindicacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_publico", nullable = false, length = 10)
    private String codigoPublico;

    @Column(name = "ip_hash", nullable = false, length = 32)
    private String ipHash;

    @Column(nullable = false)
    private boolean sucesso;

    @Column(name = "ocorrida_em", nullable = false)
    private Instant ocorridaEm;

    public TentativaReivindicacao(String codigoPublico, String ipHash, boolean sucesso) {
        this.codigoPublico = codigoPublico;
        this.ipHash = ipHash;
        this.sucesso = sucesso;
        this.ocorridaEm = Instant.now();
    }
}
