package br.com.conectapet.foto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Os bytes da foto no proprio MySQL.
 *
 * Guardar imagem em banco costuma ser ma ideia, e aqui nao e — por dois numeros
 * medidos, nao por gosto. As tres variantes de um pet somam cerca de 28 kB (160,
 * 400 e 1200 px em JPEG), e o upload ja e recusado acima de 4 MB antes do
 * reprocessamento. Trinta mil pets cabem em menos de 1 GB.
 *
 * A alternativa era object storage, que custa uma conta a mais para administrar
 * e chaves a mais para vazar. A alternativa que estava no ar era o disco do
 * container, efemero: toda foto sumia a cada publicacao.
 *
 * O dia em que o volume mudar essa conta, {@link ArmazenamentoFotos} ja existe e
 * a troca para S3 nao toca em mais nenhum arquivo. E o motivo de a interface
 * existir.
 *
 * JdbcTemplate, e nao JPA, de proposito: o Hibernate manteria o array de bytes
 * no cache de primeiro nivel, e ler dez fotos numa listagem encheria a memoria
 * com dados que ninguem vai reutilizar na mesma transacao.
 */
@Component
@ConditionalOnProperty(name = "conectapet.foto.armazenamento", havingValue = "banco")
public class ArmazenamentoBanco implements ArmazenamentoFotos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoBanco.class);

    private final JdbcTemplate jdbc;

    public ArmazenamentoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        log.info("Fotos no banco de dados (tabela fotos_arquivo).");
    }

    /**
     * Substitui a variante se ela ja existir.
     *
     * Trocar a foto de um pet reusa a mesma chave, e sem o REPLACE o segundo
     * envio quebraria na chave primaria — com a foto antiga ainda no lugar.
     */
    @Override
    @Transactional
    public void guardar(String chave, Variante variante, byte[] bytes) {
        jdbc.update("""
                REPLACE INTO fotos_arquivo (chave, variante, conteudo, tamanho, criado_em)
                VALUES (?, ?, ?, ?, ?)""",
                chave, variante.sufixo(), bytes, bytes.length, Timestamp.from(Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> ler(String chave, Variante variante) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT conteudo FROM fotos_arquivo WHERE chave = ? AND variante = ?",
                    byte[].class, chave, variante.sufixo()));
        } catch (EmptyResultDataAccessException e) {
            // Foto inexistente nao e erro: o pet pode simplesmente nao ter uma.
            return Optional.empty();
        }
    }

    /** Apaga as tres variantes de uma vez: elas nascem e morrem juntas. */
    @Override
    @Transactional
    public void apagar(String chave) {
        jdbc.update("DELETE FROM fotos_arquivo WHERE chave = ?", chave);
    }
}
