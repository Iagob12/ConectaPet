package br.com.conectapet.seguranca;

import br.com.conectapet.usuario.Papel;

import java.util.UUID;

/** Principal enxuto: so o que a autorizacao precisa, nada de entidade JPA. */
public record UsuarioAutenticado(Long id, UUID uuid, String email, Papel papel, boolean emailVerificado) {

    public boolean admin() {
        return papel == Papel.ADMIN;
    }
}
