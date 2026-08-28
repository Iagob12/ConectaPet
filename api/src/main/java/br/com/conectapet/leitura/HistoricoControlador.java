package br.com.conectapet.leitura;

import br.com.conectapet.assinatura.Assinatura;
import br.com.conectapet.assinatura.AssinaturaRepositorio;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetServico;
import br.com.conectapet.seguranca.UsuarioAtual;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pets/{uuid}/leituras")
public class HistoricoControlador {

    private final LeituraRepositorio leituras;
    private final PetServico petServico;
    private final AssinaturaRepositorio assinaturas;
    private final UsuarioAtual usuarioAtual;

    public HistoricoControlador(LeituraRepositorio leituras, PetServico petServico,
                                AssinaturaRepositorio assinaturas, UsuarioAtual usuarioAtual) {
        this.leituras = leituras;
        this.petServico = petServico;
        this.assinaturas = assinaturas;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public PaginaDto historico(@PathVariable UUID uuid,
                               @RequestParam(defaultValue = "0") int pagina,
                               @RequestParam(defaultValue = "20") int tamanho) {

        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Pet pet = petServico.meuPet(uuid, u);

        // Teto de 100 por pagina, mesmo que peçam mais.
        int limite = Math.min(Math.max(tamanho, 1), 100);
        boolean plus = assinaturas.findFirstByUsuarioIdOrderByIdDesc(u.id())
                .filter(Assinatura::plusVigente).isPresent();

        Page<Leitura> page = leituras.findByPetIdOrderByOcorridaEmDesc(
                pet.getId(), PageRequest.of(Math.max(pagina, 0), limite));

        List<LeituraDto> conteudo = page.getContent().stream().map(l -> montar(l, plus)).toList();
        return new PaginaDto(conteudo, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * No plano Free o histórico existe, mas localizacao e detalhe vem vazios com
     * `disponivelNoPlano: false` — a interface mostra o recurso bloqueado com o
     * que ele entrega, em vez de esconder que ele existe.
     */
    private LeituraDto montar(Leitura l, boolean plus) {
        return new LeituraDto(
                l.getUuid(),
                l.getOcorridaEm(),
                l.getOrigem().name(),
                plus ? l.getCidade() : null,
                plus ? l.getRegiao() : null,
                plus ? l.getPais() : null,
                l.isLocalizacaoCompartilhada(),
                plus ? l.getLatitude() : null,
                plus ? l.getLongitude() : null,
                plus ? l.getPrecisaoM() : null,
                l.getMensagemDeQuemEncontrou(),
                l.getTelefoneDeQuemEncontrou(),
                plus);
    }

    public record LeituraDto(UUID uuid, Instant ocorridaEm, String origem,
                             String cidade, String regiao, String pais,
                             boolean localizacaoCompartilhada,
                             BigDecimal latitude, BigDecimal longitude, Integer precisaoM,
                             String mensagemDeQuemEncontrou, String telefoneDeQuemEncontrou,
                             boolean disponivelNoPlano) {}

    public record PaginaDto(List<LeituraDto> conteudo, int pagina, int tamanho, long total) {}
}
