package br.com.conectapet.leitura;

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
    private final UsuarioAtual usuarioAtual;

    public HistoricoControlador(LeituraRepositorio leituras, PetServico petServico,
                                UsuarioAtual usuarioAtual) {
        this.leituras = leituras;
        this.petServico = petServico;
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

        Page<Leitura> page = leituras.findByPetIdOrderByOcorridaEmDesc(
                pet.getId(), PageRequest.of(Math.max(pagina, 0), limite));

        List<LeituraDto> conteudo = page.getContent().stream().map(this::montar).toList();
        return new PaginaDto(conteudo, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * Tudo o que foi registrado, para todo mundo.
     *
     * Cidade, regiao e coordenadas ficavam nulas fora do plano Plus. Numa
     * primeira versao isso e o pior corte possivel: onde a tag foi lida e
     * justamente a informacao que ajuda a encontrar o pet, e cobrar por ela e
     * cobrar pelo momento em que a pessoa mais precisa. Enquanto nao houver
     * plano pago, nada aqui e escondido.
     */
    private LeituraDto montar(Leitura l) {
        return new LeituraDto(
                l.getUuid(),
                l.getOcorridaEm(),
                l.getOrigem().name(),
                l.getCidade(),
                l.getRegiao(),
                l.getPais(),
                l.isLocalizacaoCompartilhada(),
                l.getLatitude(),
                l.getLongitude(),
                l.getPrecisaoM(),
                l.getMensagemDeQuemEncontrou(),
                l.getTelefoneDeQuemEncontrou());
    }

    public record LeituraDto(UUID uuid, Instant ocorridaEm, String origem,
                             String cidade, String regiao, String pais,
                             boolean localizacaoCompartilhada,
                             BigDecimal latitude, BigDecimal longitude, Integer precisaoM,
                             String mensagemDeQuemEncontrou, String telefoneDeQuemEncontrou) {}

    public record PaginaDto(List<LeituraDto> conteudo, int pagina, int tamanho, long total) {}
}
