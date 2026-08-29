package br.com.conectapet.conta;

import br.com.conectapet.assinatura.Assinatura;
import br.com.conectapet.assinatura.AssinaturaRepositorio;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.Telefone;
import br.com.conectapet.seguranca.UsuarioAtual;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Dados da conta.
 *
 * Por enquanto so leitura e atualizacao de nome e telefones. Trocar e-mail,
 * exportar e excluir conta continuam pendentes: a exclusao depende da decisao
 * juridica sobre o equilibrio entre direito a eliminacao e retencao da
 * auditoria, que nao e escolha tecnica.
 */
@RestController
@RequestMapping("/api/me")
public class ContaControlador {

    private final UsuarioRepositorio usuarios;
    private final AssinaturaRepositorio assinaturas;
    private final UsuarioAtual usuarioAtual;

    public ContaControlador(UsuarioRepositorio usuarios, AssinaturaRepositorio assinaturas,
                            UsuarioAtual usuarioAtual) {
        this.usuarios = usuarios;
        this.assinaturas = assinaturas;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public ContaResposta eu() {
        return montar(carregar());
    }

    /**
     * Nao aceita troca de e-mail: mudar o e-mail muda a chave de recuperacao da
     * conta e exige verificacao do endereco novo antes de valer. Fica para
     * quando o envio de e-mail existir de verdade.
     */
    @PutMapping
    @Transactional
    public ContaResposta atualizar(@Valid @RequestBody ContaEntrada dto) {
        Usuario u = carregar();
        if (dto.nome() != null && !dto.nome().isBlank()) {
            u.setNome(dto.nome().trim());
        }
        u.setTelefonePrincipal(Telefone.paraGravar(dto.telefonePrincipal()));
        u.setTelefoneSecundario(Telefone.paraGravar(dto.telefoneSecundario()));
        u.setWhatsapp(Telefone.paraGravar(dto.whatsapp()));
        return montar(usuarios.save(u));
    }

    private Usuario carregar() {
        UsuarioAutenticado a = usuarioAtual.obrigatorio();
        return usuarios.findById(a.id()).orElseThrow(() -> new ProblemaException(TipoErro.NAO_AUTENTICADO));
    }

    private ContaResposta montar(Usuario u) {
        String plano = assinaturas.findFirstByUsuarioIdOrderByIdDesc(u.getId())
                .filter(Assinatura::plusVigente).map(a -> "PLUS").orElse("FREE");

        return new ContaResposta(u.getUuid(), u.getEmail(), u.getNome(),
                Telefone.paraExibicao(u.getTelefonePrincipal()), u.getTelefonePrincipal(),
                Telefone.paraExibicao(u.getTelefoneSecundario()), u.getTelefoneSecundario(),
                Telefone.paraExibicao(u.getWhatsapp()), u.getWhatsapp(),
                u.emailVerificado(), u.getPapel().name(), plano);
    }

    public record ContaEntrada(@Size(min = 2, max = 120) String nome,
                               String telefonePrincipal, String telefoneSecundario, String whatsapp) {}

    public record ContaResposta(UUID uuid, String email, String nome,
                                String telefonePrincipalExibicao, String telefonePrincipalE164,
                                String telefoneSecundarioExibicao, String telefoneSecundarioE164,
                                String whatsappExibicao, String whatsappE164,
                                boolean emailVerificado, String papel, String plano) {}
}
