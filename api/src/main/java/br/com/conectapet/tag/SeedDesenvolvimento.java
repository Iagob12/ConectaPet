package br.com.conectapet.tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

/**
 * Lote de teste com os codigos de ativacao impressos no console.
 *
 * Restrito ao perfil dev E a uma propriedade explicita. Em qualquer outro
 * ambiente isso seria vazamento: o codigo de ativacao e justamente o segredo
 * que impede quem manuseia a encomenda de se cadastrar como dono.
 */
@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "conectapet.seed.habilitado", havingValue = "true")
public class SeedDesenvolvimento {

    private static final Logger log = LoggerFactory.getLogger(SeedDesenvolvimento.class);

    @Bean
    ApplicationRunner semearTags(LoteServico loteServico, LoteRepositorio lotes, TagRepositorio tags,
                                 @Value("${conectapet.seed.quantidade:10}") int quantidade,
                                 @Value("${conectapet.tag.url-publica}") String urlBase) {
        return args -> {
            if (lotes.count() > 0) {
                log.info("Seed ignorado: ja existe lote no banco.");
                return;
            }
            Lote lote = loteServico.gerar("Lote de desenvolvimento", quantidade, ModeloTag.CLASSICA,
                    "Gerado automaticamente pelo perfil dev");

            log.info("");
            log.info("=== {} tags de teste geradas (lote {}) ===", quantidade, lote.getId());
            log.info("{}", loteServico.csv(lote.getId(), urlBase));
            log.info("=== Fim do seed. Estes codigos so aparecem no perfil dev. ===");
            log.info("");
        };
    }
}
