package br.com.conectapet.comum;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O reset apaga tudo — e por isso a trava importa mais que o apagar.
 *
 * O caso que este teste protege nao e "o reset funciona". E "o reset NAO roda
 * quando ninguem pediu": uma variavel de ambiente esquecida apagando a base de
 * producao meses depois, com cliente de verdade dentro, e o pior desfecho
 * possivel deste arquivo existir.
 */
class ResetBancoUmaVezIT extends TesteIntegracao {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UsuarioRepositorio usuarios;

    private static final ApplicationArguments SEM_ARGS =
            new org.springframework.boot.DefaultApplicationArguments();

    private void criarUsuario() {
        Usuario u = new Usuario();
        u.setEmail("alguem-" + System.nanoTime() + "@exemplo.invalid");
        u.setNome("Alguem");
        u.setSenhaHash("$2a$10$abcdefghijklmnopqrstuv");
        u.setTelefonePrincipal("+5511999990000");
        usuarios.save(u);
    }

    private ApplicationRunner runner(String pedido) {
        return new ResetBancoUmaVez().resetBanco(jdbc, pedido);
    }

    @Test
    @DisplayName("sem o token, nao apaga nada")
    void semToken() throws Exception {
        criarUsuario();
        long antes = usuarios.count();
        assertThat(antes).isPositive();

        for (String pedido : new String[]{ "", "true", "sim", "APAGAR", "apagar-tudo-2026-08-31" }) {
            runner(pedido).run(SEM_ARGS);
            assertThat(usuarios.count())
                    .as("valor \"%s\" nao pode disparar o reset", pedido)
                    .isEqualTo(antes);
        }
    }

    @Test
    @DisplayName("com o token exato, apaga os dados e preserva o historico do Flyway")
    void comToken() throws Exception {
        criarUsuario();
        assertThat(usuarios.count()).isPositive();

        runner("APAGAR-TUDO-2026-08-31").run(SEM_ARGS);

        assertThat(usuarios.count()).isZero();
        // Apagar o historico faria a proxima subida tentar remigrar um esquema
        // que ja existe, e a aplicacao nao subiria.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
                .as("as migracoes precisam continuar registradas")
                .isPositive();
    }
}
