package br.com.conectapet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base dos testes de integracao.
 *
 * MySQL real, nao H2: collation e indice unico se comportam diferente, e a
 * collation binaria de codigo_publico e justamente o que impede dois codigos
 * distintos de colidirem no unique durante a geracao de um lote. Em H2 esse
 * teste passaria e a producao quebraria.
 *
 * <h2>De onde vem o banco</h2>
 *
 * Por padrao, Testcontainers sobe um MySQL 8.4 descartavel — o que exige Docker.
 * Quando Docker nao existe na maquina, um MySQL ja rodando serve igual: basta
 * apontar {@code teste.banco.url} (ou a variavel TESTE_BANCO_URL) para ele.
 *
 * <pre>
 * ./mvnw verify -Dteste.banco.url=jdbc:mysql://127.0.0.1:3306/conectapet_teste \
 *               -Dteste.banco.usuario=conectapet -Dteste.banco.senha=...
 * </pre>
 *
 * <b>Use um schema separado.</b> Os testes limpam tabelas entre casos; apontar
 * para o banco de desenvolvimento apagaria os dados dele. Por isso a checagem
 * abaixo recusa um nome de schema que nao termine em {@code _teste}: e o tipo
 * de engano que so se percebe depois de perdido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integracao")
public abstract class TesteIntegracao {

    private static final String URL = propriedade("teste.banco.url", "TESTE_BANCO_URL");
    private static final String USUARIO = propriedade("teste.banco.usuario", "TESTE_BANCO_USUARIO");
    private static final String SENHA = propriedade("teste.banco.senha", "TESTE_BANCO_SENHA");

    private static final boolean EXTERNO = !URL.isBlank();

    private static MySQLContainer<?> mysql;

    static {
        if (EXTERNO) {
            exigirSchemaDeTeste(URL);
        } else {
            mysql = new MySQLContainer<>("mysql:8.4")
                    .withCommand("--character-set-server=utf8mb4",
                                 "--collation-server=utf8mb4_0900_ai_ci");
            mysql.start();
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        if (EXTERNO) {
            registro.add("spring.datasource.url", () -> URL);
            registro.add("spring.datasource.username", () -> USUARIO);
            registro.add("spring.datasource.password", () -> SENHA);
        } else {
            registro.add("spring.datasource.url", mysql::getJdbcUrl);
            registro.add("spring.datasource.username", mysql::getUsername);
            registro.add("spring.datasource.password", mysql::getPassword);
        }
        registro.add("conectapet.jwt.segredo", () -> "segredo-de-teste-com-mais-de-32-bytes-para-hmac-sha256");
        registro.add("conectapet.privacidade.ip-pimenta", () -> "pimenta-de-teste");
        registro.add("conectapet.cors.origens", () -> "http://localhost:4321");
        registro.add("conectapet.cookie.seguro", () -> "false");
        registro.add("conectapet.site.url", () -> "http://localhost:4321");
    }

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Banco limpo antes de cada caso.
     *
     * Estava faltando, e so apareceu quando estes testes rodaram pela primeira
     * vez: cada classe limpava tags e lotes, mas ninguem limpava usuarios. O
     * primeiro caso passava e todos os seguintes morriam no unique do e-mail.
     * Escrito e nunca executado, o defeito ficou invisivel.
     *
     * Aqui, e nao em cada classe, porque isolamento entre casos e propriedade da
     * suite: uma classe nova nasceria com o mesmo problema.
     */
    @BeforeEach
    void limparBanco() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            // A ordem nao importa com a checagem desligada; o que importa e nao
            // apagar o historico do Flyway, senao a proxima subida remigra tudo.
            for (String tabela : jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                  + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'",
                    String.class)) {
                jdbc.execute("TRUNCATE TABLE `" + tabela + "`");
            }
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /** Trava contra apontar a suite para o banco de desenvolvimento. */
    private static void exigirSchemaDeTeste(String url) {
        String semQuery = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        String schema = semQuery.substring(semQuery.lastIndexOf('/') + 1);
        if (!schema.endsWith("_teste")) {
            throw new IllegalStateException(
                    "Os testes limpam tabelas. Aponte teste.banco.url para um schema terminado "
                    + "em _teste, nao para \"" + schema + "\".");
        }
    }

    private static String propriedade(String sistema, String ambiente) {
        String v = System.getProperty(sistema);
        if (v == null || v.isBlank()) {
            v = System.getenv(ambiente);
        }
        return v == null ? "" : v.trim();
    }
}
