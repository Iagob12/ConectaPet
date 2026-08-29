package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * Guarda em disco. Serve para desenvolvimento.
 *
 * NAO serve para producao: o disco de um container nao sobrevive a redeploy, e
 * a foto de todos os pets sumiria no proximo deploy. A implementacao para S3/R2
 * entra aqui do lado, com a mesma interface, quando houver credenciais.
 */
@Component
@ConditionalOnProperty(name = "conectapet.foto.armazenamento", havingValue = "local", matchIfMissing = true)
public class ArmazenamentoLocal implements ArmazenamentoFotos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoLocal.class);

    private final Path raiz;

    public ArmazenamentoLocal(@Value("${conectapet.foto.diretorio:./dados/fotos}") String diretorio) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
            log.info("Fotos em disco: {} (nao sobrevive a redeploy — trocar por S3/R2 em producao)", raiz);
        } catch (IOException e) {
            throw new IllegalStateException("Nao consegui criar o diretorio de fotos: " + raiz, e);
        }
    }

    @Override
    public void guardar(String chave, Variante variante, byte[] bytes) {
        try {
            Path destino = caminho(chave, variante);
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes);
        } catch (IOException e) {
            throw new ProblemaException(TipoErro.ERRO_INTERNO, "Falha ao guardar a foto.");
        }
    }

    @Override
    public Optional<byte[]> ler(String chave, Variante variante) {
        try {
            Path p = caminho(chave, variante);
            return Files.exists(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void apagar(String chave) {
        try {
            Path dir = raiz.resolve(chave).normalize();
            if (!dir.startsWith(raiz) || !Files.exists(dir)) {
                return;
            }
            try (var caminhos = Files.walk(dir)) {
                caminhos.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignorado) { }
                });
            }
        } catch (IOException e) {
            log.warn("Nao consegui apagar a pasta da foto {}", chave, e);
        }
    }

    /**
     * A chave vem do banco, mas normalizar e conferir o prefixo custa nada e
     * fecha a porta para travessia de diretorio caso algum dia ela passe a vir
     * de outro lugar.
     */
    private Path caminho(String chave, Variante variante) {
        Path p = raiz.resolve(chave).resolve(variante.sufixo() + ".jpg").normalize();
        if (!p.startsWith(raiz)) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS, "Caminho invalido.");
        }
        return p;
    }
}
