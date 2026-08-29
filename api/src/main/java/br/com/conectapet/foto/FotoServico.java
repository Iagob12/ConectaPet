package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetRepositorio;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.tag.TagRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class FotoServico {

    private final PetRepositorio pets;
    private final TagRepositorio tags;
    private final ArmazenamentoFotos armazenamento;
    private final ProcessadorImagem processador;
    private final SecureRandom aleatorio = new SecureRandom();

    public FotoServico(PetRepositorio pets, TagRepositorio tags,
                       ArmazenamentoFotos armazenamento, ProcessadorImagem processador) {
        this.pets = pets;
        this.tags = tags;
        this.armazenamento = armazenamento;
        this.processador = processador;
    }

    @Transactional
    public String enviar(Pet pet, byte[] bruto) {
        ProcessadorImagem.Variantes v = processador.processar(bruto);

        String chaveAnterior = pet.getFotoChave();
        String chave = novaChave();

        armazenamento.guardar(chave, ArmazenamentoFotos.Variante.PEQUENA, v.pequena());
        armazenamento.guardar(chave, ArmazenamentoFotos.Variante.MEDIA, v.media());
        armazenamento.guardar(chave, ArmazenamentoFotos.Variante.ORIGINAL, v.original());

        pet.setFotoChave(chave);
        pets.save(pet);

        // Chave nova a cada envio: o cache de quem ja viu a foto antiga nao
        // serve a nova, e a antiga deixa de existir de verdade.
        if (chaveAnterior != null) {
            armazenamento.apagar(chaveAnterior);
        }
        return chave;
    }

    @Transactional
    public void remover(Pet pet) {
        String chave = pet.getFotoChave();
        if (chave == null) {
            return;
        }
        pet.setFotoChave(null);
        pets.save(pet);
        armazenamento.apagar(chave);
    }

    /**
     * Leitura publica, sob a MESMA regra de visibilidade do perfil.
     *
     * Se nenhuma tag do pet estiver exibindo perfil — porque nunca foi ativada,
     * porque o dono desativou, ou porque a tag mudou de titularidade — a foto
     * deixa de ser acessivel junto. Um bucket publico nao daria isso: a URL
     * continuaria funcionando para sempre, para quem a tivesse copiado.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> lerPublica(String chave, ArmazenamentoFotos.Variante variante) {
        if (variante == ArmazenamentoFotos.Variante.ORIGINAL) {
            return Optional.empty();   // a original e do dono, no painel
        }
        Optional<Pet> pet = pets.findByFotoChaveAndExcluidoEmIsNull(chave);
        if (pet.isEmpty()) {
            return Optional.empty();
        }
        boolean algumaTagExibe = tags.findByPetId(pet.get().getId()).stream()
                .anyMatch(t -> t.getStatus().exibePerfil());
        if (!algumaTagExibe) {
            return Optional.empty();
        }
        return armazenamento.ler(chave, variante);
    }

    /** Leitura pelo dono, no painel. Inclui a original. */
    @Transactional(readOnly = true)
    public Optional<byte[]> lerDoDono(Pet pet, ArmazenamentoFotos.Variante variante) {
        if (pet.getFotoChave() == null) {
            return Optional.empty();
        }
        return armazenamento.ler(pet.getFotoChave(), variante);
    }

    /**
     * Chave aleatoria de 32 caracteres, nao derivada do pet.
     *
     * Se fosse o UUID do pet, quem descobrisse um UUID teria a URL da foto de
     * graca — e as URLs seriam enumeraveis a partir de qualquer vazamento de id.
     */
    private String novaChave() {
        byte[] bytes = new byte[24];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Deixa claro no tipo que o limite de 5 MB e verificado antes de tudo. */
    public static void exigirTamanhoAceitavel(long bytes, long maximo) {
        if (bytes > maximo) {
            throw new ProblemaException(TipoErro.ARQUIVO_GRANDE,
                    "A foto precisa ter no maximo 5 MB.");
        }
        if (bytes == 0) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS, "Arquivo vazio.");
        }
    }
}
