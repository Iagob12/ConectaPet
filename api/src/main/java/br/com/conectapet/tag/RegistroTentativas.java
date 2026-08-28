package br.com.conectapet.tag;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean separado de proposito.
 *
 * REQUIRES_NEW so funciona atravessando o proxy do Spring: se estes metodos
 * vivessem dentro de ReivindicacaoServico, a auto-invocacao ignoraria a anotacao,
 * a tentativa seria desfeita junto com a excecao de codigo invalido, o contador
 * nunca subiria e o limite de 5 por hora nao existiria na pratica.
 */
@Component
public class RegistroTentativas {

    private final TentativaRepositorio tentativas;

    public RegistroTentativas(TentativaRepositorio tentativas) {
        this.tentativas = tentativas;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falha(String codigoPublico, String ipHash) {
        tentativas.save(new TentativaReivindicacao(codigoPublico, ipHash, false));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sucesso(String codigoPublico, String ipHash) {
        tentativas.save(new TentativaReivindicacao(codigoPublico, ipHash, true));
    }
}
