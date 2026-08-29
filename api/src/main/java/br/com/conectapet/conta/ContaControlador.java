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
    private final br.com.conectapet.autenticacao.VerificacaoEmailServico verificacao;
    private final ContaServico contaServico;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    private final int tetoContatosFree;
    private final int tetoContatosPlus;

    public ContaControlador(UsuarioRepositorio usuarios, AssinaturaRepositorio assinaturas,
                            UsuarioAtual usuarioAtual,
                            br.com.conectapet.autenticacao.VerificacaoEmailServico verificacao,
                            ContaServico contaServico,
                            org.springframework.security.crypto.password.PasswordEncoder encoder,
                            @org.springframework.beans.factory.annotation.Value("${conectapet.planos.free.teto-contatos}") int tetoContatosFree,
                            @org.springframework.beans.factory.annotation.Value("${conectapet.planos.plus.teto-contatos}") int tetoContatosPlus) {
        this.usuarios = usuarios;
        this.assinaturas = assinaturas;
        this.usuarioAtual = usuarioAtual;
        this.verificacao = verificacao;
        this.contaServico = contaServico;
        this.encoder = encoder;
        this.tetoContatosFree = tetoContatosFree;
        this.tetoContatosPlus = tetoContatosPlus;
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

    /** Sempre 202: reenviar para um e-mail ja verificado nao faz nada, e dizer
     *  isso de forma diferente nao ajudaria ninguem. */
    @PostMapping("/verificar-email")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public void reenviarVerificacao() {
        verificacao.enviar(carregar());
    }

    /**
     * Portabilidade: tudo o que a conta guarda, num arquivo so.
     *
     * Vai como anexo, e nao como corpo de tela, porque o objetivo e a pessoa
     * levar o arquivo embora — inclusive para outro servico.
     */
    @GetMapping("/exportar")
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> exportar() {
        Usuario u = carregar();
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .attachment().filename("conectapet-meus-dados.json").build());
        headers.add(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store");
        return org.springframework.http.ResponseEntity.ok().headers(headers)
                .body(contaServico.exportar(u.getId()));
    }

    /**
     * Encerrar exige a senha, mesmo com a sessao aberta.
     *
     * E irreversivel e derruba todas as tags do tutor. Um computador
     * destravado nao pode virar isso com dois cliques — mesmo raciocinio da
     * reautenticacao do administrativo.
     */
    @DeleteMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void encerrar(@Valid @RequestBody SenhaEntrada dto) {
        Usuario u = carregar();
        if (!encoder.matches(dto.senha(), u.getSenhaHash())) {
            throw new ProblemaException(TipoErro.SEM_PERMISSAO, "Senha incorreta.");
        }
        contaServico.encerrar(u.getId());
    }

    private Usuario carregar() {
        UsuarioAutenticado a = usuarioAtual.obrigatorio();
        return usuarios.findById(a.id()).orElseThrow(() -> new ProblemaException(TipoErro.NAO_AUTENTICADO));
    }

    private ContaResposta montar(Usuario u) {
        boolean plus = assinaturas.findFirstByUsuarioIdOrderByIdDesc(u.getId())
                .filter(Assinatura::plusVigente).isPresent();
        String plano = plus ? "PLUS" : "FREE";
        // O teto sai daqui para o painel poder avisar ANTES do preenchimento.
        // Descobrir o limite so ao salvar e perder o que a pessoa digitou.
        int limiteContatos = plus ? tetoContatosPlus : tetoContatosFree;

        return new ContaResposta(u.getUuid(), u.getEmail(), u.getNome(),
                Telefone.paraExibicao(u.getTelefonePrincipal()), u.getTelefonePrincipal(),
                Telefone.paraExibicao(u.getTelefoneSecundario()), u.getTelefoneSecundario(),
                Telefone.paraExibicao(u.getWhatsapp()), u.getWhatsapp(),
                u.emailVerificado(), u.getPapel().name(), plano, limiteContatos);
    }

    public record SenhaEntrada(@jakarta.validation.constraints.NotBlank String senha) {}

    public record ContaEntrada(@Size(min = 2, max = 120) String nome,
                               String telefonePrincipal, String telefoneSecundario, String whatsapp) {}

    public record ContaResposta(UUID uuid, String email, String nome,
                                String telefonePrincipalExibicao, String telefonePrincipalE164,
                                String telefoneSecundarioExibicao, String telefoneSecundarioE164,
                                String whatsappExibicao, String whatsappE164,
                                boolean emailVerificado, String papel, String plano,
                                int limiteContatos) {}
}
