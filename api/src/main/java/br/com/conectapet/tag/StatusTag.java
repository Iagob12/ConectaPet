package br.com.conectapet.tag;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maquina de estados da tag.
 *
 *   CRIADA -> ENVIADA -> REIVINDICADA -> ATIVA <-> MODO_PERDIDO
 *                             |             |
 *                    EM_TRANSFERENCIA   DESATIVADA
 *                             |
 *                      REIVINDICADA (novo dono)
 *
 * REIVINDICADA: tem dono, sem pet vinculado.
 * ATIVA:        pet vinculado, com nome preenchido E ao menos um canal de
 *               contato visivel. A transicao e automatica ao salvar o perfil.
 *               Enquanto REIVINDICADA, o endpoint publico responde como nao ativada.
 * DESATIVADA:   terminal. Nao volta a ficar ativa sem intervencao administrativa.
 */
public enum StatusTag {

    CRIADA,
    ENVIADA,
    REIVINDICADA,
    ATIVA,
    MODO_PERDIDO,
    EM_TRANSFERENCIA,
    DESATIVADA;

    private static final Map<StatusTag, Set<StatusTag>> PERMITIDAS = Map.of(
            CRIADA,           EnumSet.of(ENVIADA, REIVINDICADA, DESATIVADA),
            ENVIADA,          EnumSet.of(REIVINDICADA, DESATIVADA),
            REIVINDICADA,     EnumSet.of(ATIVA, EM_TRANSFERENCIA, DESATIVADA),
            ATIVA,            EnumSet.of(MODO_PERDIDO, REIVINDICADA, EM_TRANSFERENCIA, DESATIVADA),
            MODO_PERDIDO,     EnumSet.of(ATIVA, EM_TRANSFERENCIA, DESATIVADA),
            EM_TRANSFERENCIA, EnumSet.of(REIVINDICADA, ATIVA, DESATIVADA),
            DESATIVADA,       EnumSet.noneOf(StatusTag.class));

    public boolean podeIrPara(StatusTag destino) {
        return PERMITIDAS.get(this).contains(destino);
    }

    /** Estados em que a tag ainda pode ser reivindicada por alguem. */
    public boolean reivindicavel() {
        return this == CRIADA || this == ENVIADA;
    }

    /** Estados em que o endpoint publico mostra dados do pet. */
    public boolean exibePerfil() {
        return this == ATIVA || this == MODO_PERDIDO;
    }
}
