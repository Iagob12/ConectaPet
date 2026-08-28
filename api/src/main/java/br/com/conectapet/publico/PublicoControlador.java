package br.com.conectapet.publico;

import br.com.conectapet.comum.util.GeradorCodigo;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.comum.util.PisoDeTempo;
import br.com.conectapet.comum.util.Telefone;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.tag.TagRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * A unica superficie aberta da API.
 *
 * Duas garantias valem para todas as rotas daqui:
 *
 *  1. `Cache-Control: no-store` e `X-Robots-Tag: noindex, nofollow`. O perfil de
 *     um pet com o telefone do tutor nao pode ser indexado nem ficar em cache de
 *     CDN. A meta tag equivalente e responsabilidade da pagina.
 *  2. Piso de tempo de resposta. Tempo constante de verdade e inatingivel em JVM
 *     com JIT e pool de conexoes; o piso elimina o vazamento observavel entre
 *     "codigo existe" e "codigo nao existe".
 */
@RestController
@RequestMapping("/api/public")
public class PublicoControlador {

    private final TagRepositorio tags;
    private final PerfilPublicoServico perfis;
    private final LeituraServico leituras;
    private final ListaEsperaServico listaEspera;
    private final String ipPimenta;
    private final Duration piso;

    public PublicoControlador(TagRepositorio tags, PerfilPublicoServico perfis, LeituraServico leituras,
                              ListaEsperaServico listaEspera,
                              @Value("${conectapet.privacidade.ip-pimenta}") String ipPimenta,
                              @Value("${conectapet.limites.piso-resposta-publica}") Duration piso) {
        this.tags = tags;
        this.perfis = perfis;
        this.leituras = leituras;
        this.listaEspera = listaEspera;
        this.ipPimenta = ipPimenta;
        this.piso = piso;
    }

    /**
     * O endpoint mais critico do sistema.
     *
     * Devolve 200 tanto para tag ativa quanto para nao ativada — e tambem para
     * codigo inexistente, com corpo identico. A especificacao original pedia 404
     * para inexistente "com o mesmo corpo de uma nao ativada", o que e
     * autocontraditorio: o status code sozinho ja permitiria enumerar os codigos.
     *
     * Efeito colateral: registra a leitura com a origem detectada. Nunca notifica.
     */
    @GetMapping("/tags/{codigoPublico}")
    public ResponseEntity<PerfilPublicoDto> perfil(@PathVariable String codigoPublico,
                                                   HttpServletRequest req) {
        PerfilPublicoDto corpo = PisoDeTempo.aoMenos(piso, () -> {
            Optional<Tag> tag = buscar(codigoPublico);
            tag.ifPresent(t -> leituras.registrarAcesso(t, ipHash(req), req.getHeader("User-Agent")));
            return perfis.montar(tag);
        });
        return ResponseEntity.ok().headers(cabecalhosPublicos()).body(corpo);
    }

    /** Mesma regra de indistinguibilidade e mesmo piso do endpoint principal. */
    @GetMapping("/tags/{codigoPublico}/status")
    public ResponseEntity<PerfilPublicoServico.StatusPublicoDto> status(@PathVariable String codigoPublico) {
        var corpo = PisoDeTempo.aoMenos(piso, () -> perfis.status(buscar(codigoPublico)));
        return ResponseEntity.ok().headers(cabecalhosPublicos()).body(corpo);
    }

    /**
     * Confirmacao de leitura humana, enviada pelo cliente via sendBeacon.
     * E a unica entrada que notifica o tutor.
     *
     * Devolve o mesmo 202 para codigo inexistente.
     */
    @PostMapping("/tags/{codigoPublico}/leituras")
    public ResponseEntity<LeituraAceitaDto> confirmarLeitura(@PathVariable String codigoPublico,
                                                             @Valid @RequestBody(required = false) LeituraEntrada dto,
                                                             HttpServletRequest req) {
        LeituraAceitaDto corpo = PisoDeTempo.aoMenos(piso, () -> {
            buscar(codigoPublico).ifPresent(tag -> leituras.confirmarLeituraHumana(
                    tag, ipHash(req), req.getHeader("User-Agent"),
                    dto == null ? null : dto.paraDados()));
            // Nao revela se o tutor recebeu, leu ou esta online: isso e
            // informacao do tutor, nao de quem encontrou o pet.
            return new LeituraAceitaDto("Avisamos o tutor. Obrigado por ajudar.");
        });
        return ResponseEntity.accepted().headers(cabecalhosPublicos()).body(corpo);
    }

    /**
     * Enquanto nao existe checkout, e o destino honesto do CTA principal.
     * Responde 202 mesmo para e-mail ja cadastrado, para nao revelar quem esta na lista.
     */
    @PostMapping("/lista-espera")
    public ResponseEntity<Void> entrarNaLista(@Valid @RequestBody ListaEsperaEntrada dto,
                                              HttpServletRequest req) {
        listaEspera.registrar(dto.email(), dto.tipoPet(), ipHash(req));
        return ResponseEntity.accepted().headers(cabecalhosPublicos()).build();
    }

    // ---- Apoio -------------------------------------------------------------

    /** Forma invalida nao chega ao banco: economiza consulta e nao muda a resposta. */
    private Optional<Tag> buscar(String codigoPublico) {
        String c = GeradorCodigo.normalizar(codigoPublico);
        if (!GeradorCodigo.formaValida(c, GeradorCodigo.TAMANHO_PUBLICO)) {
            return Optional.empty();
        }
        return tags.findByCodigoPublico(c);
    }

    private HttpHeaders cabecalhosPublicos() {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.CACHE_CONTROL, "no-store");
        h.add("X-Robots-Tag", "noindex, nofollow");
        return h;
    }

    private String ipHash(HttpServletRequest req) {
        return Hashes.ipPseudonimo(req.getRemoteAddr(), ipPimenta);
    }

    // ---- DTOs --------------------------------------------------------------

    public record LeituraEntrada(
            boolean localizacaoCompartilhada,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Min(0) @Max(100000) Integer precisaoM,
            @Size(max = 500) String mensagem,
            String telefone) {

        LeituraServico.DadosDeQuemEncontrou paraDados() {
            return new LeituraServico.DadosDeQuemEncontrou(
                    localizacaoCompartilhada, latitude, longitude, precisaoM,
                    mensagem, Telefone.paraE164(telefone));
        }
    }

    public record LeituraAceitaDto(String mensagem) {}

    public record ListaEsperaEntrada(
            @NotBlank @Email String email,
            @Size(max = 20) String tipoPet,
            @AssertTrue(message = "E preciso aceitar a politica de privacidade") boolean consentimento) {}
}
