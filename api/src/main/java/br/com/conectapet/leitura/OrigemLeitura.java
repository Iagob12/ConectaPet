package br.com.conectapet.leitura;

/**
 * Registrar leitura e notificar tutor sao coisas diferentes.
 *
 * SERVIDOR — navegador humano, registrada ao servir o perfil. Nao notifica.
 * ROBO     — user-agent de robo de preview. Registra para o historico ficar
 *            honesto, nao notifica.
 * CLIENTE  — confirmada por sendBeacon depois que a pagina renderizou. A unica
 *            que notifica.
 *
 * Sem essa separacao, todo compartilhamento de link no WhatsApp dispararia
 * "seu pet foi encontrado" — e o proprio tutor receberia um push a cada
 * aproximacao enquanto testa a tag no cadastro.
 */
public enum OrigemLeitura { SERVIDOR, CLIENTE, ROBO }
