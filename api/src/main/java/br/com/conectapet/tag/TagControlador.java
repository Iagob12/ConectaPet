package br.com.conectapet.tag;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.seguranca.UsuarioAtual;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
public class TagControlador {

    private final TagRepositorio tags;
    private final br.com.conectapet.pet.PetRepositorio pets;
    private final ReivindicacaoServico reivindicacao;
    private final TransferenciaServico transferencia;
    private final br.com.conectapet.auditoria.AuditoriaServico auditoria;
    private final UsuarioAtual usuarioAtual;
    private final String urlPublica;
    private final String ipPimenta;
    private final java.time.Duration validadeTransferencia;

    public TagControlador(TagRepositorio tags, br.com.conectapet.pet.PetRepositorio pets,
                          ReivindicacaoServico reivindicacao,
                          TransferenciaServico transferencia,
                          br.com.conectapet.auditoria.AuditoriaServico auditoria, UsuarioAtual usuarioAtual,
                          @Value("${conectapet.tag.url-publica}") String urlPublica,
                          @Value("${conectapet.privacidade.ip-pimenta}") String ipPimenta,
                          @Value("${conectapet.transferencia.validade}") java.time.Duration validadeTransferencia) {
        this.tags = tags;
        this.pets = pets;
        this.reivindicacao = reivindicacao;
        this.transferencia = transferencia;
        this.auditoria = auditoria;
        this.usuarioAtual = usuarioAtual;
        this.urlPublica = urlPublica;
        this.ipPimenta = ipPimenta;
        this.validadeTransferencia = validadeTransferencia;
    }

    @GetMapping
    public List<TagResposta> minhasTags() {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return tags.findByUsuarioIdOrderByCriadoEmDesc(u.id()).stream()
                .map(this::montar)
                .toList();
    }

    @GetMapping("/{uuid}")
    public TagResposta detalhe(@PathVariable UUID uuid) {
        return montar(minhaTag(uuid));
    }

    /**
     * Resolve o pet ligado a tag.
     *
     * O painel lista tags e pets em chamadas separadas e precisa junta-las; sem
     * o uuid do pet aqui, a unica saida seria casar as duas listas pela ordem,
     * que quebra assim que alguem tem dois pets.
     */
    private TagResposta montar(Tag t) {
        UUID petUuid = t.getPetId() == null ? null
                : pets.findById(t.getPetId()).map(br.com.conectapet.pet.Pet::getUuid).orElse(null);
        return TagResposta.de(t, urlPublica, petUuid);
    }

    @PostMapping("/reivindicar")
    public TagResposta reivindicar(@Valid @RequestBody ReivindicarEntrada dto, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Tag tag = reivindicacao.reivindicar(dto.codigoPublico(), dto.codigoAtivacao(), u, ipHash(req));
        return montar(tag);
    }

    @PostMapping("/{uuid}/desativar")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable UUID uuid, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Tag tag = minhaTag(uuid);
        tag.transitarPara(StatusTag.DESATIVADA);
        tags.save(tag);
        auditoria.registrar(u.uuid(), br.com.conectapet.auditoria.AuditoriaServico.ACAO_TAG_DESATIVADA,
                "TAG", tag.getUuid(), null, ipHash(req));
    }

    // ---- Transferir titularidade: a tag muda de dono ----------------------

    /** O codigo e devolvido uma unica vez. Exige e-mail verificado. */
    @PostMapping("/{uuid}/titularidade")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CodigoTransferenciaResposta gerarTransferencia(@PathVariable UUID uuid, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.comEmailVerificado();
        String codigo = transferencia.gerar(uuid, u, ipHash(req));
        return new CodigoTransferenciaResposta(codigo,
                java.time.Instant.now().plus(validadeTransferencia));
    }

    @DeleteMapping("/{uuid}/titularidade")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void cancelarTransferencia(@PathVariable UUID uuid, HttpServletRequest req) {
        transferencia.cancelar(uuid, usuarioAtual.obrigatorio(), ipHash(req));
    }

    @PostMapping("/titularidade/aceitar")
    public TagResposta aceitarTransferencia(@Valid @RequestBody AceitarEntrada dto, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return montar(transferencia.aceitar(dto.codigo(), u, ipHash(req)));
    }

    // ---- Migrar perfil: mesmo tutor, tag nova -----------------------------

    @PostMapping("/{uuid}/migrar-perfil")
    public TagResposta migrarPerfil(@PathVariable UUID uuid, @Valid @RequestBody MigrarEntrada dto,
                                    HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Tag t = transferencia.migrarPerfil(uuid, dto.petUuid(), dto.desativarTagAnterior(), u, ipHash(req));
        return montar(t);
    }

    /**
     * Verificacao explicita de posse, nao apenas de autenticacao.
     *
     * Devolve 403 e nao 404 tambem quando a tag nao existe: distinguir permitiria
     * a um usuario logado enumerar UUIDs de tags alheias.
     */
    private Tag minhaTag(UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Tag tag = tags.findByUuid(uuid).orElseThrow(() -> new ProblemaException(TipoErro.NAO_E_DONO));
        if (!tag.pertenceA(u.id())) {
            throw new ProblemaException(TipoErro.NAO_E_DONO);
        }
        return tag;
    }

    /** O IP em claro nunca e persistido nem logado. */
    private String ipHash(HttpServletRequest req) {
        return Hashes.ipPseudonimo(req.getRemoteAddr(), ipPimenta);
    }

    // ---- DTOs --------------------------------------------------------------

    public record ReivindicarEntrada(
            @NotBlank
            @Pattern(regexp = "^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{10}$",
                     message = "O codigo da tag tem 10 caracteres e nao usa 0, O, I, 1, L nem U")
            String codigoPublico,

            @NotBlank
            @Pattern(regexp = "^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}$",
                     message = "O codigo de ativacao tem 8 caracteres e nao usa 0, O, I, 1, L nem U")
            String codigoAtivacao) {}

    public record AceitarEntrada(
            @NotBlank
            @Pattern(regexp = "^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}$",
                     message = "O codigo de transferencia tem 8 caracteres")
            String codigo) {}

    public record MigrarEntrada(@jakarta.validation.constraints.NotNull UUID petUuid,
                                boolean desativarTagAnterior) {}

    /** Devolvido uma unica vez; depois so existe como hash. */
    public record CodigoTransferenciaResposta(String codigo, java.time.Instant expiraEm) {}

    /** O codigo de ativacao nunca sai daqui, em hipotese nenhuma. */
    public record TagResposta(UUID uuid, String codigoPublico, String modelo, String status,
                              boolean modoPerdido, String urlPublica, UUID petUuid,
                              Instant reivindicadaEm, Instant enviadaEm, Instant desativadaEm) {

        static TagResposta de(Tag t, String base, UUID petUuid) {
            return new TagResposta(t.getUuid(), t.getCodigoPublico(), t.getModelo().name(),
                    t.getStatus().name(), t.isModoPerdido(), base + t.getCodigoPublico(), petUuid,
                    t.getReivindicadaEm(), t.getEnviadaEm(), t.getDesativadaEm());
        }
    }
}
