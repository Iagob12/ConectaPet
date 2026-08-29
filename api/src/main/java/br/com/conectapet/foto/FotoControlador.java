package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetServico;
import br.com.conectapet.seguranca.UsuarioAtual;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
public class FotoControlador {

    private final FotoServico fotos;
    private final PetServico petServico;
    private final UsuarioAtual usuarioAtual;
    private final long maxBytes;

    public FotoControlador(FotoServico fotos, PetServico petServico, UsuarioAtual usuarioAtual,
                           @Value("${conectapet.foto.max-bytes:5242880}") long maxBytes) {
        this.fotos = fotos;
        this.petServico = petServico;
        this.usuarioAtual = usuarioAtual;
        this.maxBytes = maxBytes;
    }

    // ---- Envio pelo dono ----------------------------------------------------

    @PostMapping("/api/pets/{uuid}/foto")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public FotoResposta enviar(@PathVariable UUID uuid, @RequestParam("arquivo") MultipartFile arquivo) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Pet pet = petServico.meuPet(uuid, u);

        FotoServico.exigirTamanhoAceitavel(arquivo.getSize(), maxBytes);

        byte[] bruto;
        try {
            bruto = arquivo.getBytes();
        } catch (IOException e) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS, "Nao consegui ler o arquivo enviado.");
        }

        String chave = fotos.enviar(pet, bruto);
        return FotoResposta.de(chave);
    }

    @DeleteMapping("/api/pets/{uuid}/foto")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        fotos.remover(petServico.meuPet(uuid, u));
    }

    /** A original so existe para o dono, no painel. */
    @GetMapping("/api/pets/{uuid}/foto")
    public ResponseEntity<byte[]> original(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Pet pet = petServico.meuPet(uuid, u);

        return fotos.lerDoDono(pet, ArmazenamentoFotos.Variante.ORIGINAL)
                .map(b -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.noStore())
                        .body(b))
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));
    }

    // ---- Leitura publica ----------------------------------------------------

    /**
     * Servida pela API, nunca por bucket publico, e sob a mesma regra de
     * visibilidade do perfil: tag desativada ou perfil oculto derrubam a foto
     * junto.
     *
     * `no-store` pelo mesmo motivo do perfil: a foto de um pet perdido com o
     * telefone do tutor ao lado nao pode ficar em cache de CDN.
     */
    @GetMapping("/api/public/fotos/{chave}/{variante}")
    public ResponseEntity<byte[]> publica(@PathVariable String chave, @PathVariable String variante) {
        var v = ArmazenamentoFotos.Variante.porSufixo(variante)
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));

        return fotos.lerPublica(chave, v)
                .map(b -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.noStore())
                        .header("X-Robots-Tag", "noindex, nofollow")
                        .body(b))
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));
    }

    public record FotoResposta(String pequena, String media) {
        static FotoResposta de(String chave) {
            return new FotoResposta("/api/public/fotos/" + chave + "/p",
                                    "/api/public/fotos/" + chave + "/m");
        }
    }
}
