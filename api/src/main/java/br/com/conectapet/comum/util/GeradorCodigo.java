package br.com.conectapet.comum.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera os dois codigos do sistema.
 *
 * Alfabeto: digitos e letras sem 0 O I 1 L U — 30 simbolos.
 * Nao e Base32 de biblioteca: o Crockford ja tira I L O U, e aqui tiramos
 * tambem 0 e 1, sobrando 30 e nao 32. Geracao e validacao sao manuais de
 * proposito; qualquer tentativa de "corrigir" isso usando um decoder de Base32
 * quebra os codigos ja gravados nas tags.
 *
 * Entropia: ~49 bits no codigo publico (10 caracteres), ~39 bits no de ativacao
 * (8 caracteres). Suficiente dado o limite de 5 tentativas por hora.
 *
 * SecureRandom, nunca sequencial: codigo sequencial permitiria varrer o site
 * inteiro e coletar o telefone de todos os tutores.
 */
@Component
public class GeradorCodigo {

    public static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int TAMANHO_PUBLICO = 10;
    public static final int TAMANHO_ATIVACAO = 8;

    private final SecureRandom aleatorio = new SecureRandom();

    public String codigoPublico() {
        return gerar(TAMANHO_PUBLICO);
    }

    public String codigoAtivacao() {
        return gerar(TAMANHO_ATIVACAO);
    }

    private String gerar(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Valida forma, antes de gastar uma tentativa do limite. */
    public static boolean formaValida(String codigo, int tamanho) {
        if (codigo == null || codigo.length() != tamanho) {
            return false;
        }
        for (int i = 0; i < codigo.length(); i++) {
            if (ALFABETO.indexOf(codigo.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normaliza o que a pessoa digitou: caixa alta e sem espaco.
     *
     * Nao corrige caractere ambiguo por adivinhacao. Trocar 0 por O
     * automaticamente esconderia um erro de leitura do cartao e faria a pessoa
     * gastar tentativa sem entender por que. A interface orienta; nao advinha.
     */
    public static String normalizar(String bruto) {
        return bruto == null ? null : bruto.trim().toUpperCase().replace(" ", "").replace("-", "");
    }
}
