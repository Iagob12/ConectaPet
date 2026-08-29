package br.com.conectapet.admin;

import br.com.conectapet.usuario.Papel;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Promove a ADMIN uma conta que ja existe, indicada por variavel de ambiente.
 *
 * O primeiro administrador tinha que sair de algum lugar, e as duas saidas
 * obvias sao piores: criar a conta com senha no arquivo de configuracao deixa
 * a senha em texto claro no deploy, e o seed de desenvolvimento e proibido
 * fora do perfil dev justamente porque imprime segredo no console.
 *
 * Aqui nao ha senha envolvida. A pessoa se cadastra pelo site como qualquer
 * tutor, escolhendo a propria senha, e a variavel apenas eleva o papel dessa
 * conta na proxima subida. Se o e-mail nao existir, o servico registra e
 * segue: promover um cadastro que ainda nao foi feito seria criar uma conta
 * fantasma com poder de administrador.
 */
@Configuration
public class PromocaoAdmin {

    private static final Logger log = LoggerFactory.getLogger(PromocaoAdmin.class);

    @Bean
    ApplicationRunner promoverAdminInicial(UsuarioRepositorio usuarios,
                                           @Value("${conectapet.admin.email-inicial:}") String email) {
        return args -> {
            if (email == null || email.isBlank()) {
                return;
            }
            String alvo = email.trim().toLowerCase(Locale.ROOT);
            var achado = usuarios.findByEmailAndExcluidoEmIsNull(alvo);

            if (achado.isEmpty()) {
                log.warn("conectapet.admin.email-inicial aponta para {}, que ainda nao tem cadastro. "
                        + "Crie a conta pelo site e suba de novo.", alvo);
                return;
            }
            Usuario u = achado.get();
            if (u.getPapel() == Papel.ADMIN) {
                return;
            }
            u.setPapel(Papel.ADMIN);
            usuarios.save(u);
            log.info("Conta {} promovida a ADMIN.", alvo);
        };
    }
}
