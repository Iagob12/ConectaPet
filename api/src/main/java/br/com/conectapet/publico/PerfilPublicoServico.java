package br.com.conectapet.publico;

import br.com.conectapet.comum.util.Telefone;
import br.com.conectapet.pet.*;
import br.com.conectapet.tag.StatusTag;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Monta o que a pagina de resgate mostra.
 *
 * Regra que governa este arquivo inteiro: o DTO e construido campo a campo a
 * partir de visibilidade_perfil. A entidade JPA NUNCA e serializada. Campo
 * oculto nao vira null nem string vazia — ele simplesmente nao existe no JSON,
 * porque a pagina nao pode ter rotulo orfao nem espaco reservado.
 *
 * Nada aqui devolve e-mail do tutor, endereco completo, documento, id interno
 * ou codigo de ativacao, em nenhum caminho.
 */
@Service
public class PerfilPublicoServico {

    private final PetRepositorio pets;
    private final PetSaudeRepositorio saudes;
    private final VisibilidadeRepositorio visibilidades;
    private final ContatoRepositorio contatos;
    private final UsuarioRepositorio usuarios;

    public PerfilPublicoServico(PetRepositorio pets, PetSaudeRepositorio saudes,
                                VisibilidadeRepositorio visibilidades,
                                ContatoRepositorio contatos, UsuarioRepositorio usuarios) {
        this.pets = pets;
        this.saudes = saudes;
        this.visibilidades = visibilidades;
        this.contatos = contatos;
        this.usuarios = usuarios;
    }

    /**
     * Devolve o perfil de uma tag que exibe perfil, ou o estado NAO_ATIVADA.
     *
     * O chamador passa Optional vazio quando o codigo nao existe — e este metodo
     * produz exatamente a mesma resposta que produziria para uma tag existente
     * mas nao ativada. Nao ha ramo que diferencie os dois casos.
     */
    @Transactional(readOnly = true)
    public PerfilPublicoDto montar(Optional<Tag> talvezTag) {
        if (talvezTag.isEmpty()) {
            return PerfilPublicoDto.naoAtivada();
        }
        Tag tag = talvezTag.get();
        if (!tag.getStatus().exibePerfil() || tag.getPetId() == null) {
            return PerfilPublicoDto.naoAtivada();
        }
        Optional<Pet> talvezPet = pets.findById(tag.getPetId()).filter(p -> p.getExcluidoEm() == null);
        if (talvezPet.isEmpty()) {
            return PerfilPublicoDto.naoAtivada();
        }

        Pet pet = talvezPet.get();
        VisibilidadePerfil v = visibilidades.findById(pet.getId())
                .orElseGet(() -> new VisibilidadePerfil(pet.getId()));
        Optional<Usuario> tutor = usuarios.findById(pet.getUsuarioId());
        if (tutor.isEmpty()) {
            return PerfilPublicoDto.naoAtivada();
        }

        return new PerfilPublicoDto(
                "ATIVO",
                tag.isModoPerdido(),
                montarPet(pet, v),
                montarTutor(tutor.get(), v),
                v.isMostrarSaude() ? montarSaude(pet) : null,
                v.isMostrarContatosEmergencia() ? montarContatos(pet) : null,
                v.getMensagemPersonalizada());
    }

    private PerfilPublicoDto.PetDto montarPet(Pet p, VisibilidadePerfil v) {
        return new PerfilPublicoDto.PetDto(
                p.getNome(),
                p.getEspecie().name(),
                p.getRaca(),
                p.getSexo() == null ? null : p.getSexo().name(),
                p.getPesoKg(),
                p.getCor(),
                p.getCastrado(),
                // Oculto por padrao: o microchip identifica o animal em cadastro oficial.
                v.isMostrarMicrochip() ? p.getNumeroMicrochip() : null,
                v.isMostrarCidade() ? p.getCidade() : null,
                v.isMostrarCidade() ? p.getEstado() : null,
                p.getObservacoes(),
                p.getFotoChave() == null ? null : montarFoto(p));
    }

    /**
     * Variantes geradas na ingestao e servidas pela propria API, sob a mesma
     * regra de visibilidade. Nunca uma URL de bucket publico: a foto seria
     * acessivel com o perfil oculto ou a tag desativada.
     */
    private PerfilPublicoDto.FotoDto montarFoto(Pet p) {
        String base = "/api/public/fotos/" + p.getFotoChave();
        // A saida e JPEG: escrever WebP exigiria dependencia com codigo nativo.
        // "original" nao vai para o publico: e do dono, no painel.
        return new PerfilPublicoDto.FotoDto(base + "/p", base + "/m", null);
    }

    private PerfilPublicoDto.TutorDto montarTutor(Usuario u, VisibilidadePerfil v) {
        String telefone = v.isMostrarTelefone() ? u.getTelefonePrincipal() : null;
        String whats = v.isMostrarWhatsapp() ? u.getWhatsapp() : null;

        return new PerfilPublicoDto.TutorDto(
                primeiroNome(u.getNome()),
                Telefone.paraExibicao(telefone), telefone,
                Telefone.paraExibicao(whats), Telefone.paraWhatsApp(whats));
    }

    /** So o primeiro nome: sobrenome completo e mais dado do que a situacao exige. */
    private String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        int espaco = nome.trim().indexOf(' ');
        return espaco < 0 ? nome.trim() : nome.trim().substring(0, espaco);
    }

    private PerfilPublicoDto.SaudeDto montarSaude(Pet pet) {
        PetSaude s = saudes.findById(pet.getId()).orElse(null);
        if (s == null || s.vazio()) {
            return null;
        }
        return new PerfilPublicoDto.SaudeDto(
                s.getAlergias(), s.getMedicacaoContinua(), s.getCondicoes(), s.getCuidadosEspeciais(),
                s.getVeterinarioNome(),
                Telefone.paraExibicao(s.getVeterinarioTelefone()), s.getVeterinarioTelefone(),
                s.getClinica());
    }

    private List<PerfilPublicoDto.ContatoDto> montarContatos(Pet pet) {
        List<ContatoEmergencia> lista = contatos.findByPetIdOrderByOrdemAscIdAsc(pet.getId());
        if (lista.isEmpty()) {
            return null;
        }
        List<PerfilPublicoDto.ContatoDto> saida = new ArrayList<>(lista.size());
        for (ContatoEmergencia c : lista) {
            saida.add(new PerfilPublicoDto.ContatoDto(
                    c.getNome(), c.getParentesco(),
                    Telefone.paraExibicao(c.getTelefone()), c.getTelefone()));
        }
        return saida;
    }

    /** Versao leve, para a pagina decidir o que renderizar. Mesma regra. */
    @Transactional(readOnly = true)
    public StatusPublicoDto status(Optional<Tag> talvezTag) {
        boolean exibe = talvezTag
                .filter(t -> t.getStatus().exibePerfil() && t.getPetId() != null)
                .isPresent();
        boolean perdido = talvezTag.map(Tag::isModoPerdido).orElse(false) && exibe;
        return new StatusPublicoDto(exibe ? "ATIVO" : "NAO_ATIVADA", perdido);
    }

    public record StatusPublicoDto(String estado, boolean modoPerdido) {}
}
