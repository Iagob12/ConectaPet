package br.com.conectapet.admin;

import br.com.conectapet.auditoria.AuditoriaServico;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.seguranca.UsuarioAtual;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.tag.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rotas administrativas. O acesso exige ROLE_ADMIN, garantido em SegurancaConfig.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminControlador {

    public static final String CABECALHO_ELEVACAO = "X-Reautenticacao";

    private final LoteServico loteServico;
    private final LoteRepositorio lotes;
    private final TagRepositorio tags;
    private final MetricasServico metricas;
    private final ReautenticacaoServico reautenticacao;
    private final AuditoriaServico auditoria;
    private final UsuarioAtual usuarioAtual;
    private final String urlPublica;
    private final String ipPimenta;
    private final br.com.conectapet.seguranca.IpDoCliente ipDoCliente;

    public AdminControlador(LoteServico loteServico, LoteRepositorio lotes, TagRepositorio tags,
                            MetricasServico metricas, ReautenticacaoServico reautenticacao,
                            AuditoriaServico auditoria, UsuarioAtual usuarioAtual,
                            @Value("${conectapet.tag.url-publica}") String urlPublica,
                            @Value("${conectapet.privacidade.ip-pimenta}") String ipPimenta,
                              br.com.conectapet.seguranca.IpDoCliente ipDoCliente) {
        this.loteServico = loteServico;
        this.lotes = lotes;
        this.tags = tags;
        this.metricas = metricas;
        this.reautenticacao = reautenticacao;
        this.auditoria = auditoria;
        this.usuarioAtual = usuarioAtual;
        this.urlPublica = urlPublica;
        this.ipPimenta = ipPimenta;
        this.ipDoCliente = ipDoCliente;
    }

    // ---- Reautenticacao ----------------------------------------------------

    @PostMapping("/reautenticar")
    public ElevacaoResposta reautenticar(@Valid @RequestBody SenhaEntrada dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        var e = reautenticacao.elevar(u, dto.senha());
        return new ElevacaoResposta(e.token(), e.expiraEm());
    }

    // ---- Lotes -------------------------------------------------------------

    @GetMapping("/lotes")
    public List<LoteResposta> listarLotes() {
        return lotes.findAll().stream().map(LoteResposta::de).toList();
    }

    /**
     * Gera N tags. O lote nasce NAO_CONFIRMADO e os codigos de ativacao ficam
     * recuperaveis ate a confirmacao explicita.
     */
    @PostMapping("/lotes")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public LoteResposta gerarLote(@Valid @RequestBody LoteEntrada dto, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Lote lote = loteServico.gerar(dto.nome(), dto.quantidade(), dto.modelo(), dto.observacoes());

        auditoria.registrar(u.uuid(), "LOTE_GERADO", "LOTE", null,
                Map.of("loteId", lote.getId(), "quantidade", dto.quantidade(),
                       "modelo", dto.modelo().name()), ipHash(req));

        return LoteResposta.de(lote);
    }

    /**
     * CSV para gravacao e impressao.
     *
     * Exige reautenticacao: uma sessao de admin aberta num computador
     * destravado nao pode virar acesso ao segredo que protege um lote inteiro.
     * Cada download vira registro de auditoria.
     */
    @GetMapping(value = "/lotes/{id}/codigos", produces = "text/csv")
    public ResponseEntity<String> baixarCodigos(@PathVariable Long id,
                                                @RequestHeader(value = CABECALHO_ELEVACAO, required = false) String elevacao,
                                                HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        reautenticacao.exigir(elevacao, u);

        String csv = loteServico.csv(id, urlPublica);

        auditoria.registrar(u.uuid(), "CODIGOS_BAIXADOS", "LOTE", null,
                Map.of("loteId", id), ipHash(req));

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        h.setContentDispositionFormData("attachment", "lote-" + id + ".csv");
        h.add(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.ok().headers(h).body(csv);
    }

    /**
     * Irreversivel: apaga os codigos de ativacao em claro, deixando so o hash.
     * Exige reautenticacao pelo mesmo motivo — e a acao que torna um erro de
     * download impossivel de corrigir.
     */
    @PostMapping("/lotes/{id}/confirmar")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void confirmarLote(@PathVariable Long id,
                              @RequestHeader(value = CABECALHO_ELEVACAO, required = false) String elevacao,
                              HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        reautenticacao.exigir(elevacao, u);

        loteServico.confirmar(id);

        auditoria.registrar(u.uuid(), "LOTE_CONFIRMADO", "LOTE", null,
                Map.of("loteId", id), ipHash(req));
    }

    // ---- Tags --------------------------------------------------------------

    @GetMapping("/tags")
    public PaginaTags buscarTags(@RequestParam(required = false) StatusTag status,
                                 @RequestParam(required = false) Long loteId,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 @RequestParam(defaultValue = "20") int tamanho) {
        int limite = Math.min(Math.max(tamanho, 1), 100);
        Page<Tag> page = tags.buscar(status, loteId, PageRequest.of(Math.max(pagina, 0), limite));
        return new PaginaTags(page.getContent().stream().map(TagAdminResposta::de).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * Marcar como enviada exige lote confirmado.
     *
     * Enviar tag cujo CSV ainda nao foi conferido significa mandar para o
     * cliente um chaveiro cujo codigo de ativacao talvez nao esteja impresso em
     * lugar nenhum — e ele so descobre com a caixa na mao.
     */
    @PostMapping("/tags/{uuid}/marcar-enviada")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void marcarEnviada(@PathVariable UUID uuid, HttpServletRequest req) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Tag tag = tags.findByUuid(uuid).orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));

        Lote lote = lotes.findById(tag.getLoteId())
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));
        if (lote.getStatus() != StatusLote.CONFIRMADO) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Confirme o recebimento do arquivo de codigos deste lote antes de enviar as tags.");
        }

        tag.transitarPara(StatusTag.ENVIADA);
        tag.setEnviadaEm(Instant.now());
        tags.save(tag);

        auditoria.registrar(u.uuid(), "TAG_ENVIADA", "TAG", tag.getUuid(), null, ipHash(req));
    }

    // ---- Metricas ----------------------------------------------------------

    @GetMapping("/metricas")
    public MetricasServico.Metricas metricas(@RequestParam(required = false) LocalDate de,
                                             @RequestParam(required = false) LocalDate ate) {
        Instant inicio = (de == null ? LocalDate.now().minusDays(30) : de)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant fim = (ate == null ? LocalDate.now() : ate)
                .plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return metricas.calcular(inicio, fim);
    }

    // ---- Apoio -------------------------------------------------------------

    private String ipHash(HttpServletRequest req) {
        return Hashes.ipPseudonimo(ipDoCliente.de(req), ipPimenta);
    }

    // ---- DTOs --------------------------------------------------------------

    public record SenhaEntrada(@NotBlank String senha) {}

    public record ElevacaoResposta(String token, Instant expiraEm) {}

    public record LoteEntrada(@NotBlank @Size(max = 80) String nome,
                              @NotNull @Min(1) @Max(10000) Integer quantidade,
                              @NotNull ModeloTag modelo,
                              @Size(max = 300) String observacoes) {}

    public record LoteResposta(Long id, String nome, Integer quantidade, String modelo,
                               String status, Instant produzidoEm, Instant confirmadoEm,
                               String observacoes) {

        static LoteResposta de(Lote l) {
            return new LoteResposta(l.getId(), l.getNome(), l.getQuantidade(), l.getModelo().name(),
                    l.getStatus().name(), l.getProduzidoEm(), l.getConfirmadoEm(), l.getObservacoes());
        }
    }

    /** Nem o codigo de ativacao em claro nem o hash saem por aqui. */
    public record TagAdminResposta(UUID uuid, String codigoPublico, String modelo, String status,
                                   Long loteId, boolean reivindicada, Instant enviadaEm,
                                   Instant reivindicadaEm, Instant desativadaEm) {

        static TagAdminResposta de(Tag t) {
            return new TagAdminResposta(t.getUuid(), t.getCodigoPublico(), t.getModelo().name(),
                    t.getStatus().name(), t.getLoteId(), t.getReivindicadaEm() != null,
                    t.getEnviadaEm(), t.getReivindicadaEm(), t.getDesativadaEm());
        }
    }

    public record PaginaTags(List<TagAdminResposta> conteudo, int pagina, int tamanho, long total) {}
}
