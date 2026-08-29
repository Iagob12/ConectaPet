package br.com.conectapet.seguranca;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtual {

    private final br.com.conectapet.usuario.UsuarioRepositorio usuarios;

    public UsuarioAtual(br.com.conectapet.usuario.UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    public UsuarioAutenticado obrigatorio() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UsuarioAutenticado u)) {
            throw new ProblemaException(TipoErro.NAO_AUTENTICADO);
        }
        return u;
    }

    /**
     * Para acao sensivel: transferir titularidade, trocar e-mail, excluir conta.
     *
     * Le do banco, e nao da claim do token. A claim e uma foto do momento do
     * login: quem confirmava o e-mail continuava barrado por ate quinze
     * minutos, vendo "confirme seu e-mail" numa tela que ja mostrava o endereco
     * como confirmado. Uma consulta a mais numa acao rara vale mais do que uma
     * porta que abre com atraso.
     */
    public UsuarioAutenticado comEmailVerificado() {
        UsuarioAutenticado u = obrigatorio();
        boolean verificado = usuarios.findById(u.id())
                .map(br.com.conectapet.usuario.Usuario::emailVerificado)
                .orElse(false);
        if (!verificado) {
            throw new ProblemaException(TipoErro.EMAIL_NAO_VERIFICADO);
        }
        return u;
    }
}
