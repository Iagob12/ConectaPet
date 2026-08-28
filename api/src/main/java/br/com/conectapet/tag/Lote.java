package br.com.conectapet.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "lotes_tag")
@Getter
@Setter
@NoArgsConstructor
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private Integer quantidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModeloTag modelo;

    /**
     * Nasce NAO_CONFIRMADO. Os codigos de ativacao seguem recuperaveis mediante
     * reautenticacao ate o admin confirmar que recebeu o arquivo. Sem isso, uma
     * conexao que cai no meio do download perde os codigos de um lote de tags ja
     * gravadas fisicamente: prejuizo material, nao inconveniencia.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusLote status = StatusLote.NAO_CONFIRMADO;

    @Column(name = "produzido_em", nullable = false)
    private Instant produzidoEm;

    @Column(name = "confirmado_em")
    private Instant confirmadoEm;

    @Column(length = 300)
    private String observacoes;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (produzidoEm == null) produzidoEm = agora;
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }
}
