package br.com.conectapet.pet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * O que o publico ve quando encosta o celular na tag.
 *
 * Defaults do design system: telefone e WhatsApp visiveis, saude visivel,
 * microchip oculto.
 */
@Entity
@Table(name = "visibilidade_perfil")
@Getter
@Setter
@NoArgsConstructor
public class VisibilidadePerfil {

    @Id
    @Column(name = "pet_id")
    private Long petId;

    @Column(name = "mostrar_telefone", nullable = false)
    private boolean mostrarTelefone = true;

    @Column(name = "mostrar_whatsapp", nullable = false)
    private boolean mostrarWhatsapp = true;

    @Column(name = "mostrar_contatos_emergencia", nullable = false)
    private boolean mostrarContatosEmergencia = true;

    @Column(name = "mostrar_saude", nullable = false)
    private boolean mostrarSaude = true;

    @Column(name = "mostrar_cidade", nullable = false)
    private boolean mostrarCidade = true;

    /** Oculto por padrao: o microchip identifica o animal em cadastro oficial. */
    @Column(name = "mostrar_microchip", nullable = false)
    private boolean mostrarMicrochip = false;

    @Column(name = "mensagem_personalizada", length = 200)
    private String mensagemPersonalizada;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public VisibilidadePerfil(Long petId) {
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

    /**
     * Sem telefone e sem WhatsApp, a pagina de resgate nao tem como acionar
     * ninguem — vira uma tela de emergencia inutil.
     */
    public boolean temAlgumCanalDeContato() {
        return mostrarTelefone || mostrarWhatsapp;
    }
}
