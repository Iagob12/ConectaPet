package br.com.conectapet.notificacao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "outbox_notificacoes")
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

    public enum Tipo { LEITURA_TAG, TAG_REIVINDICADA, TRANSFERENCIA_SOLICITADA,
                       VERIFICACAO_EMAIL, RESET_SENHA, RESUMO_DIARIO }

    public enum Canal { EMAIL, WHATSAPP }

    public enum Status { PENDENTE, ENVIADA, FALHOU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Canal canal = Canal.EMAIL;

    @Column(nullable = false)
    private String destinatario;

    /** Nunca senha, token, codigo de ativacao ou telefone aqui dentro. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDENTE;

    @Column(nullable = false)
    private int tentativas = 0;

    @Column(name = "processar_apos", nullable = false)
    private Instant processarApos;

    @Column(name = "processada_em")
    private Instant processadaEm;

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (processarApos == null) processarApos = agora;
        criadoEm = agora;
    }
}
