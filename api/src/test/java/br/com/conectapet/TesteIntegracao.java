package br.com.conectapet;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de integracao.
 *
 * MySQL real, nao H2: collation e indice unico se comportam diferente, e a
 * collation binaria de codigo_publico e justamente o que impede dois codigos
 * distintos de colidirem no unique durante a geracao de um lote. Em H2 esse
 * teste passaria e a producao quebraria.
 *
 * Marcados com @Tag("integracao") para o build rodar sem Docker:
 *   mvn -Psem-docker test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Tag("integracao")
public abstract class TesteIntegracao {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("conectapet.jwt.segredo", () -> "segredo-de-teste-com-mais-de-32-bytes-para-hmac-sha256");
        registro.add("conectapet.privacidade.ip-pimenta", () -> "pimenta-de-teste");
        registro.add("conectapet.cors.origens", () -> "http://localhost:4321");
        registro.add("conectapet.cookie.seguro", () -> "false");
    }
}
