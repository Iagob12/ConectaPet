package br.com.conectapet.leitura;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leituras")
@Getter
@Setter
@NoArgsConstructor
public class Leitura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID uuid;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "pet_id")
    private Long petId;

    @Column(name = "ocorrida_em", nullable = false)
    private Instant ocorridaEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemLeitura origem;

    @Column(name = "ip_hash", columnDefinition = "CHAR(32)")
    private String ipHash;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    private String cidade;
    private String regiao;

    @Column(length = 2)
    private String pais;

    @Column(name = "localizacao_compartilhada", nullable = false)
    private boolean localizacaoCompartilhada = false;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "precisao_m")
    private Integer precisaoM;

    @Column(name = "mensagem_de_quem_encontrou", length = 500)
    private String mensagemDeQuemEncontrou;

    @Column(name = "telefone_de_quem_encontrou")
    private String telefoneDeQuemEncontrou;

    @Column(name = "dados_terceiro_expurgados_em")
    private Instant dadosTerceiroExpurgadosEm;

    @Column(name = "notificada_em")
    private Instant notificadaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (uuid == null) uuid = UUID.randomUUID();
        if (ocorridaEm == null) ocorridaEm = agora;
        criadoEm = agora;
    }
}
