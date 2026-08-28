package br.com.conectapet.notificacao;

/**
 * Interface para desacoplar o envio. E-mail agora, WhatsApp depois, sem que o
 * codigo que enfileira precise saber qual e.
 */
public interface CanalEnvio {

    Notificacao.Canal canal();

    void enviar(Notificacao notificacao) throws Exception;
}
