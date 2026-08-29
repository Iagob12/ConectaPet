package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.Optional;

/**
 * Fotos em object storage.
 *
 * Serve S3 e qualquer compativel — R2, MinIO, Backblaze — porque o que muda
 * entre eles e o endpoint. E a implementacao de producao: o disco local nao
 * sobrevive a um redeploy, e uma foto perdida e a diferenca entre quem achou o
 * pet reconhecer o bicho ou nao.
 *
 * <b>O bucket nunca e publico.</b> Nada aqui devolve URL: os bytes voltam para a
 * API, que os serve sob a mesma regra de visibilidade do perfil. Com bucket
 * publico, a foto continuaria acessivel depois de o tutor ocultar o perfil ou
 * desativar a tag — e o endereco dela ja teria vazado para quem abriu a pagina.
 */
@Component
@ConditionalOnProperty(name = "conectapet.foto.armazenamento", havingValue = "s3")
public class ArmazenamentoS3 implements ArmazenamentoFotos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoS3.class);

    private final S3Client cliente;
    private final String bucket;

    public ArmazenamentoS3(
            @Value("${conectapet.foto.s3.bucket}") String bucket,
            @Value("${conectapet.foto.s3.regiao:auto}") String regiao,
            @Value("${conectapet.foto.s3.endpoint:}") String endpoint,
            @Value("${conectapet.foto.s3.chave}") String chaveAcesso,
            @Value("${conectapet.foto.s3.segredo}") String segredo) {

        this.bucket = bucket;
        this.cliente = criarCliente(regiao, endpoint, chaveAcesso, segredo);
        log.info("Fotos em object storage: bucket={} endpoint={}", bucket,
                endpoint.isBlank() ? "AWS" : endpoint);
    }

    /**
     * Monta o cliente. Visivel para o teste exercitar esta mesma configuracao,
     * e nao uma montada a parte que poderia divergir dela sem ninguem notar.
     */
    static S3Client criarCliente(String regiao, String endpoint, String chaveAcesso, String segredo) {
        var construtor = S3Client.builder()
                .region(Region.of(regiao))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(chaveAcesso, segredo)));

        if (!endpoint.isBlank()) {
            construtor.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            // Sem subdominio por bucket: R2, MinIO e afins nao
                            // o usam, e sem o caminho no path o cliente monta um
                            // host inexistente. A falha aparece como erro de DNS,
                            // sem relacao aparente com armazenamento.
                            .pathStyleAccessEnabled(true)
                            // Sem assinatura em blocos. A AWS aceita, mas nem todo
                            // servico compativel aceita — o Backblaze B2 recusa —
                            // e o corpo chega com os cabecalhos de bloco no meio
                            // dos bytes. O resultado seria uma foto gravada
                            // corrompida, que so aparece quando alguem tenta ver.
                            .chunkedEncodingEnabled(false)
                            .build());
        }
        return construtor.build();
    }

    /** Só para teste: injeta um cliente já configurado. */
    ArmazenamentoS3(S3Client cliente, String bucket) {
        this.cliente = cliente;
        this.bucket = bucket;
    }

    @Override
    public void guardar(String chave, Variante variante, byte[] bytes) {
        try {
            cliente.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objeto(chave, variante))
                    .contentType("image/jpeg")
                    // Nao ha cache publico: o objeto e privado e a API decide,
                    // a cada leitura, se aquela pessoa pode ve-lo.
                    .cacheControl("no-store")
                    .build(), RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            // Falha de escrita nao pode virar sucesso silencioso: o pet ficaria
            // com um perfil que promete foto e devolve nada.
            log.error("Falha ao guardar a foto {} ({})", chave, variante, e);
            throw new ProblemaException(TipoErro.ERRO_INTERNO, "Falha ao guardar a foto.");
        }
    }

    /**
     * Ausente devolve vazio; qualquer outra falha estoura.
     *
     * A distincao importa: tratar erro de rede como "nao existe" faria a foto
     * sumir da tela durante uma instabilidade, e o tutor concluiria que ela foi
     * apagada.
     */
    @Override
    public Optional<byte[]> ler(String chave, Variante variante) {
        try {
            return Optional.of(cliente.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objeto(chave, variante))
                    .build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Apaga todas as variantes.
     *
     * Uma a uma, e nao por prefixo: `deleteObjects` exige listar antes, e a
     * permissao de listagem no bucket e justamente a que nao queremos dar a
     * esta credencial. Sao tres chaves conhecidas.
     */
    @Override
    public void apagar(String chave) {
        for (Variante v : Variante.values()) {
            try {
                cliente.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(objeto(chave, v))
                        .build());
            } catch (SdkException e) {
                // Segue apagando as outras: parar na primeira falha deixaria
                // para tras justamente as variantes publicas.
                log.warn("Nao consegui apagar a foto {} ({})", chave, v, e);
            }
        }
    }

    /** Mesmo layout do armazenamento em disco: uma pasta por chave. */
    private String objeto(String chave, Variante variante) {
        return chave + "/" + variante.sufixo() + ".jpg";
    }
}
