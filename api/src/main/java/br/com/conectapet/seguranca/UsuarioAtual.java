package br.com.conectapet.seguranca;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtual {

    public UsuarioAutenticado obrigatorio() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UsuarioAutenticado u)) {
            throw new ProblemaException(TipoErro.NAO_AUTENTICADO);
        }
        return u;
    }

    /** Para acao sensivel: transferir titularidade, trocar e-mail, excluir conta. */
    public UsuarioAutenticado comEmailVerificado() {
        UsuarioAutenticado u = obrigatorio();
        if (!u.emailVerificado()) {
            throw new ProblemaException(TipoErro.EMAIL_NAO_VERIFICADO);
        }
        return u;
    }
}
