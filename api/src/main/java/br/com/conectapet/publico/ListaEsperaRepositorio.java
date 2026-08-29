package br.com.conectapet.publico;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio em arquivo proprio, nao aninhado numa classe.
 *
 * O Spring Data so detecta interfaces de repositorio aninhadas com
 * considerNestedRepositories=true — sem isso a aplicacao sobe e quebra na
 * injecao, com uma mensagem que nao aponta para a causa.
 */
public interface ListaEsperaRepositorio extends JpaRepository<ListaEsperaServico.Inscricao, Long> {

    boolean existsByEmail(String email);
}
