package br.com.conectapet.comum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Apaga TODOS os dados do banco, uma vez, sob pedido explicito.
 *
 * ISTO E TEMPORARIO. Existe porque zerar a base de producao foi pedido e nao
 * ha outro caminho: as credenciais do banco vivem so na hospedagem, e nao ha
 * console de SQL disponivel. Depois de rodar, este arquivo deve ser removido —
 * codigo que apaga banco nao mora num repositorio por mais tempo que o
 * necessario.
 *
 * A trava e um token literal, e nao um booleano. "RESET_BANCO=true" esquecido
 * numa variavel de ambiente apagaria a base a cada publicacao, silenciosamente,
 * meses depois, quando ja houvesse cliente de verdade dentro. O token carrega a
 * data e some junto com o codigo: uma variavel esquecida deixa de casar com
 * qualquer coisa na versao seguinte.
 *
 * O historico do Flyway e preservado. Apaga-lo faria a proxima subida tentar
 * remigrar um esquema que ja existe, e a aplicacao nao subiria.
 */
@Configuration
public class ResetBancoUmaVez {

    private static final Logger log = LoggerFactory.getLogger(ResetBancoUmaVez.class);

    /** Precisa bater exatamente. Muda a cada uso; nunca reaproveitar. */
    private static final String TOKEN = "APAGAR-TUDO-2026-08-31";

    @Bean
    @Order(0)   // antes da promocao a admin, que procura um usuario que vai deixar de existir
    ApplicationRunner resetBanco(JdbcTemplate jdbc,
                                 @Value("${conectapet.reset-banco:}") String pedido) {
        return args -> {
            if (!TOKEN.equals(pedido)) {
                return;
            }
            List<String> tabelas = jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                  + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'",
                    String.class);

            log.warn("RESET DE BANCO PEDIDO. Apagando {} tabelas: {}", tabelas.size(), tabelas);

            jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String t : tabelas) {
                    jdbc.execute("TRUNCATE TABLE `" + t + "`");
                }
            } finally {
                jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            log.warn("RESET CONCLUIDO. O banco esta vazio. "
                   + "Cadastre-se pelo site e reinicie para a conta virar ADMIN. "
                   + "REMOVA a variavel RESET_BANCO e esta classe.");
        };
    }
}
