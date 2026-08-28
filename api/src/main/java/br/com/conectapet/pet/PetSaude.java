package br.com.conectapet.pet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "pet_saude")
@Getter
@Setter
@NoArgsConstructor
public class PetSaude {

    @Id
    @Column(name = "pet_id")
    private Long petId;

    @Column(length = 300)
    private String alergias;

    @Column(name = "medicacao_continua", length = 300)
    private String medicacaoContinua;

    @Column(length = 300)
    private String condicoes;

    @Column(name = "cuidados_especiais", length = 300)
    private String cuidadosEspeciais;

    @Column(name = "veterinario_nome")
    private String veterinarioNome;

    @Column(name = "veterinario_telefone")
    private String veterinarioTelefone;

    private String clinica;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public PetSaude(Long petId) {
        this.petId = petId;
    }

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public boolean vazio() {
        return alergias == null && medicacaoContinua == null && condicoes == null
                && cuidadosEspeciais == null && veterinarioNome == null
                && veterinarioTelefone == null && clinica == null;
    }
}
