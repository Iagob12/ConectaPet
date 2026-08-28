package br.com.conectapet.comum.erro;

public class ProblemaException extends RuntimeException {

    private final TipoErro tipo;
    private final String detalhe;

    public ProblemaException(TipoErro tipo) {
        this(tipo, null);
    }

    public ProblemaException(TipoErro tipo, String detalhe) {
        super(tipo.titulo());
        this.tipo = tipo;
        this.detalhe = detalhe;
    }

    public TipoErro tipo() { return tipo; }
    public String detalhe() { return detalhe; }
}
