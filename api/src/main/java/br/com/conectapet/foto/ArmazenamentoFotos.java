package br.com.conectapet.foto;

import java.util.Optional;

/**
 * Onde os bytes da foto ficam.
 *
 * Interface separada porque a decisao G escolheu object storage (S3 ou R2) para
 * producao, mas as credenciais ainda nao existem. Trocar a implementacao nao
 * toca em mais nenhum arquivo.
 *
 * Uma regra vale para qualquer implementacao: o bucket NUNCA e publico. A
 * imagem e sempre servida pela API, sob a mesma regra de visibilidade do perfil
 * — senao a foto continuaria acessivel com o perfil oculto ou a tag desativada.
 */
public interface ArmazenamentoFotos {

    enum Variante {
        PEQUENA("p"), MEDIA("m"), ORIGINAL("o");

        private final String sufixo;

        Variante(String sufixo) { this.sufixo = sufixo; }

        public String sufixo() { return sufixo; }

        public static Optional<Variante> porSufixo(String s) {
            for (Variante v : values()) {
                if (v.sufixo.equals(s)) {
                    return Optional.of(v);
                }
            }
            return Optional.empty();
        }
    }

    void guardar(String chave, Variante variante, byte[] bytes);

    Optional<byte[]> ler(String chave, Variante variante);

    void apagar(String chave);
}
