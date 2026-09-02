package br.com.conectapet.tag;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetServico;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fecha a primeira configuracao da NFC em uma unica transacao.
 *
 * Ler ou digitar o codigo nao muda a tag. Somente este servico, chamado pelo
 * botao final do formulario, assume a NFC, cria o perfil e liga os dois. Se o
 * pet ou o contato do tutor estiver incompleto, a excecao desfaz tudo e o mesmo
 * link continua livre para uma nova tentativa.
 */
@Service
public class AtivacaoCadastroServico {

    private final ReivindicacaoServico reivindicacao;
    private final PetServico pets;
    private final TagRepositorio tags;

    public AtivacaoCadastroServico(ReivindicacaoServico reivindicacao,
                                    PetServico pets,
                                    TagRepositorio tags) {
        this.reivindicacao = reivindicacao;
        this.pets = pets;
        this.tags = tags;
    }

    @Transactional
    public Resultado confirmar(String codigoPublico, Pet dadosPet,
                               UsuarioAutenticado usuario, String ipHash) {
        Tag tag = reivindicacao.reivindicar(codigoPublico, usuario, ipHash);
        Pet pet = pets.criar(dadosPet, tag.getUuid(), usuario);

        // O formulario final so pode consumir a NFC quando a pagina publica ja
        // tem pet e ao menos um meio visivel de falar com o tutor.
        String falta = pets.motivoNaoPronto(pet, usuario.id());
        if (falta != null) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS,
                    "Nao foi possivel confirmar a tag. " + falta);
        }

        Tag ativa = tags.findByUuid(tag.getUuid())
                .orElseThrow(() -> new ProblemaException(TipoErro.ESTADO_INVALIDO));
        if (ativa.getStatus() != StatusTag.ATIVA || ativa.getPetId() == null) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "A tag nao ficou pronta. Revise os dados e tente novamente.");
        }

        return new Resultado(ativa, pet);
    }

    public record Resultado(Tag tag, Pet pet) {}
}
