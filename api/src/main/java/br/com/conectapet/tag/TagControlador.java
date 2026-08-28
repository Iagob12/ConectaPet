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
    private final ReivindicacaoServico reivindicacao;
    private final UsuarioAtual usuarioAtual;
    private final String urlPublica;
    private final String ipPimenta;

    public TagControlador(TagRepositorio tags, ReivindicacaoServico reivindicacao, UsuarioAtual usuarioAtual,
                          @Value("${conectapet.tag.url-publica}") String urlPublica,
                          @Value("${conectapet.privacidade.ip-pimenta}") String ipPimenta) {
        this.tags = tags;
        this.reivindicacao = reivindicacao;
        this.usuarioAtual = usuarioAtual;
        this.urlPublica = urlPublica;
        this.ipPimenta = ipPimenta;
    }

    @GetMapping
    public List<TagResposta> minhasTags() {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return tags.findByUsuarioIdOrderByCriadoEmDesc(u.id()).stream()
                .map(t -> TagResposta.de(t, urlPublica))
                .toList();
    }

    @GetMapping("/{uuid}")
    public TagResposta detalhe(@PathVariable UUID uuid) {
        return TagResposta.de(minhaTag(uuid), urlPublica);
    }

    @PostMapping("/reivindicar")
    public TagResposta reivindicar(@Valid @RequestBody ReivindicarEntrada dto, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Tag tag = reivindicacao.reivindicar(dto.codigoPublico(), dto.codigoAtivacao(), u, ipHash(req));
        return TagResposta.de(tag, urlPublica);
    }

    @PostMapping("/{uuid}/desativar")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable UUID uuid) {
        Tag tag = minhaTag(uuid);
        tag.transitarPara(StatusTag.DESATIVADA);
        tags.save(tag);
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

    /** O codigo de ativacao nunca sai daqui, em hipotese nenhuma. */
    public record TagResposta(UUID uuid, String codigoPublico, String modelo, String status,
                              boolean modoPerdido, String urlPublica,
                              Instant reivindicadaEm, Instant enviadaEm, Instant desativadaEm) {

        static TagResposta de(Tag t, String base) {
            return new TagResposta(t.getUuid(), t.getCodigoPublico(), t.getModelo().name(),
                    t.getStatus().name(), t.isModoPerdido(), base + t.getCodigoPublico(),
                    t.getReivindicadaEm(), t.getEnviadaEm(), t.getDesativadaEm());
        }
    }
}
