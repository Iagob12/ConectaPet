package br.com.conectapet.pet;

import br.com.conectapet.assinatura.Assinatura;
import br.com.conectapet.assinatura.AssinaturaRepositorio;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.tag.StatusTag;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.tag.TagRepositorio;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PetServico {

    private final PetRepositorio pets;
    private final PetSaudeRepositorio saudes;
    private final VisibilidadeRepositorio visibilidades;
    private final ContatoRepositorio contatos;
    private final TagRepositorio tags;
    private final UsuarioRepositorio usuarios;
    private final AssinaturaRepositorio assinaturas;
    private final br.com.conectapet.auditoria.AuditoriaServico auditoria;
    private final int tetoContatosFree;
    private final int tetoContatosPlus;

    public PetServico(PetRepositorio pets, PetSaudeRepositorio saudes,
                      VisibilidadeRepositorio visibilidades, ContatoRepositorio contatos,
                      TagRepositorio tags, UsuarioRepositorio usuarios, AssinaturaRepositorio assinaturas,
                      br.com.conectapet.auditoria.AuditoriaServico auditoria,
                      @Value("${conectapet.planos.free.teto-contatos}") int tetoContatosFree,
                      @Value("${conectapet.planos.plus.teto-contatos}") int tetoContatosPlus) {
        this.pets = pets;
        this.saudes = saudes;
        this.visibilidades = visibilidades;
        this.contatos = contatos;
        this.tags = tags;
        this.usuarios = usuarios;
        this.assinaturas = assinaturas;
        this.auditoria = auditoria;
        this.tetoContatosFree = tetoContatosFree;
        this.tetoContatosPlus = tetoContatosPlus;
    }

    // ---- Posse -------------------------------------------------------------

    /**
     * Verificacao explicita de posse, nao apenas de autenticacao.
     *
     * Devolve 403 tambem quando o pet nao existe: distinguir permitiria a um
     * usuario logado enumerar UUIDs de pets alheios pela diferenca 403/404.
     */
    @Transactional(readOnly = true)
    public Pet meuPet(UUID uuid, UsuarioAutenticado u) {
        Pet pet = pets.findByUuidAndExcluidoEmIsNull(uuid)
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_E_DONO));
        if (!pet.pertenceA(u.id())) {
            throw new ProblemaException(TipoErro.NAO_E_DONO);
        }
        return pet;
    }

    @Transactional(readOnly = true)
    public List<Pet> meusPets(UsuarioAutenticado u) {
        return pets.findByUsuarioIdAndExcluidoEmIsNullOrderByCriadoEmDesc(u.id());
    }

    // ---- Ciclo de vida do pet ----------------------------------------------

    /**
     * Cria o pet e, junto, as linhas de saude e visibilidade — esta ja com os
     * defaults. Sem isso, o primeiro PUT de visibilidade teria que criar a linha
     * e o perfil publico ficaria sem regra definida no meio do caminho.
     */
    @Transactional
    public Pet criar(Pet novo, UUID tagUuid, UsuarioAutenticado u) {
        novo.setUsuarioId(u.id());
        pets.saveAndFlush(novo);

        saudes.save(new PetSaude(novo.getId()));
        visibilidades.save(new VisibilidadePerfil(novo.getId()));

        if (tagUuid != null) {
            vincularTag(tagUuid, novo, u);
        }
        return novo;
    }

    @Transactional
    public Pet atualizar(UUID uuid, Pet dados, UsuarioAutenticado u) {
        Pet pet = meuPet(uuid, u);
        pet.setNome(dados.getNome());
        pet.setEspecie(dados.getEspecie());
        pet.setRaca(dados.getRaca());
        pet.setSexo(dados.getSexo());
        pet.setDataNascimento(dados.getDataNascimento());
        pet.setPesoKg(dados.getPesoKg());
        pet.setCor(dados.getCor());
        pet.setCastrado(dados.getCastrado());
        pet.setNumeroMicrochip(dados.getNumeroMicrochip());
        pet.setCidade(dados.getCidade());
        pet.setEstado(dados.getEstado());
        pet.setObservacoes(dados.getObservacoes());
        pets.save(pet);

        reavaliarTags(pet, u);
        return pet;
    }

    /** Exclusao logica. As tags voltam para REIVINDICADA, sem perfil. */
    @Transactional
    public void excluir(UUID uuid, UsuarioAutenticado u) {
        Pet pet = meuPet(uuid, u);
        for (Tag tag : tags.findByPetId(pet.getId())) {
            tag.setPetId(null);
            tag.setModoPerdido(false);
            if (tag.getStatus() != StatusTag.DESATIVADA) {
                tag.transitarPara(StatusTag.REIVINDICADA);
            }
            tags.save(tag);
        }
        pet.setExcluidoEm(java.time.Instant.now());
        pets.save(pet);
    }

    // ---- Saude e visibilidade ----------------------------------------------

    @Transactional(readOnly = true)
    public PetSaude saude(Pet pet) {
        return saudes.findById(pet.getId()).orElseGet(() -> new PetSaude(pet.getId()));
    }

    @Transactional
    public PetSaude salvarSaude(UUID uuid, PetSaude dados, UsuarioAutenticado u) {
        Pet pet = meuPet(uuid, u);
        PetSaude s = saudes.findById(pet.getId()).orElseGet(() -> new PetSaude(pet.getId()));
        s.setAlergias(dados.getAlergias());
        s.setMedicacaoContinua(dados.getMedicacaoContinua());
        s.setCondicoes(dados.getCondicoes());
        s.setCuidadosEspeciais(dados.getCuidadosEspeciais());
        s.setVeterinarioNome(dados.getVeterinarioNome());
        s.setVeterinarioTelefone(dados.getVeterinarioTelefone());
        s.setClinica(dados.getClinica());
        return saudes.save(s);
    }

    @Transactional(readOnly = true)
    public VisibilidadePerfil visibilidade(Pet pet) {
        return visibilidades.findById(pet.getId())
                .orElseGet(() -> new VisibilidadePerfil(pet.getId()));
    }

    /**
     * Recusa a configuracao que deixaria o perfil sem nenhum canal de contato.
     *
     * Regra de negocio, nao de interface: o frontend tambem bloqueia, mas nao
     * pode ser a unica barreira — a API e chamada por mais de um cliente.
     */
    @Transactional
    public VisibilidadePerfil salvarVisibilidade(UUID uuid, VisibilidadePerfil dados, UsuarioAutenticado u) {
        Pet pet = meuPet(uuid, u);

        if (!dados.temAlgumCanalDeContato()) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS,
                    "Deixe ao menos o telefone ou o WhatsApp visivel. Sem nenhum dos dois, "
                    + "quem encontrar seu pet nao tem como falar com voce.");
        }

        VisibilidadePerfil v = visibilidades.findById(pet.getId())
                .orElseGet(() -> new VisibilidadePerfil(pet.getId()));
        v.setMostrarTelefone(dados.isMostrarTelefone());
        v.setMostrarWhatsapp(dados.isMostrarWhatsapp());
        v.setMostrarContatosEmergencia(dados.isMostrarContatosEmergencia());
        v.setMostrarSaude(dados.isMostrarSaude());
        v.setMostrarCidade(dados.isMostrarCidade());
        v.setMostrarMicrochip(dados.isMostrarMicrochip());
        v.setMensagemPersonalizada(dados.getMensagemPersonalizada());
        visibilidades.save(v);

        // Auditoria obrigatoria: alterar visibilidade muda o que um estranho na
        // rua consegue ver do pet e do tutor.
        auditoria.registrar(u.uuid(), br.com.conectapet.auditoria.AuditoriaServico.ACAO_VISIBILIDADE_ALTERADA,
                "PET", pet.getUuid(),
                java.util.Map.of(
                        "telefone", v.isMostrarTelefone(),
                        "whatsapp", v.isMostrarWhatsapp(),
                        "saude", v.isMostrarSaude(),
                        "contatos", v.isMostrarContatosEmergencia(),
                        "microchip", v.isMostrarMicrochip(),
                        "cidade", v.isMostrarCidade()),
                null);

        reavaliarTags(pet, u);
        return v;
    }

    // ---- Contatos de emergencia --------------------------------------------

    @Transactional(readOnly = true)
    public List<ContatoEmergencia> contatos(Pet pet) {
        return contatos.findByPetIdOrderByOrdemAscIdAsc(pet.getId());
    }

    @Transactional
    public ContatoEmergencia adicionarContato(UUID uuid, ContatoEmergencia novo, UsuarioAutenticado u) {
        Pet pet = meuPet(uuid, u);
        int teto = tetoContatos(u.id());
        if (contatos.countByPetId(pet.getId()) >= teto) {
            throw new ProblemaException(TipoErro.LIMITE_PLANO,
                    "Seu plano permite ate " + teto + " contato(s) de emergencia por pet.");
        }
        novo.setPetId(pet.getId());
        return contatos.save(novo);
    }

    @Transactional
    public ContatoEmergencia atualizarContato(UUID petUuid, UUID contatoUuid,
                                              ContatoEmergencia dados, UsuarioAutenticado u) {
        Pet pet = meuPet(petUuid, u);
        ContatoEmergencia c = contatoDoPet(contatoUuid, pet);
        c.setNome(dados.getNome());
        c.setTelefone(dados.getTelefone());
        c.setParentesco(dados.getParentesco());
        c.setOrdem(dados.getOrdem());
        return contatos.save(c);
    }

    @Transactional
    public void removerContato(UUID petUuid, UUID contatoUuid, UsuarioAutenticado u) {
        Pet pet = meuPet(petUuid, u);
        contatos.delete(contatoDoPet(contatoUuid, pet));
    }

    private ContatoEmergencia contatoDoPet(UUID contatoUuid, Pet pet) {
        ContatoEmergencia c = contatos.findByUuid(contatoUuid)
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_E_DONO));
        // O contato existe, mas pode ser de outro pet — inclusive de outro dono.
        if (!c.getPetId().equals(pet.getId())) {
            throw new ProblemaException(TipoErro.NAO_E_DONO);
        }
        return c;
    }

    /**
     * Plus vencido nao apaga nem esconde contatos ja cadastrados: eles seguem
     * visiveis ao publico. O que fica bloqueado e adicionar mais.
     */
    private int tetoContatos(Long usuarioId) {
        return assinaturas.findFirstByUsuarioIdOrderByIdDesc(usuarioId)
                .filter(Assinatura::plusVigente)
                .map(a -> tetoContatosPlus)
                .orElse(tetoContatosFree);
    }

    // ---- Modo perdido ------------------------------------------------------

    /**
     * O estado vive na TAG — e ela que a pessoa na rua le. O endpoint fica no pet
     * por conveniencia e propaga para todas as tags dele.
     */
    @Transactional
    public List<Tag> definirModoPerdido(UUID uuid, boolean ligado, UsuarioAutenticado u) {
        Pet pet = meuPet(uuid, u);
        List<Tag> doPet = tags.findByPetId(pet.getId()).stream()
                .filter(t -> t.getStatus() != StatusTag.DESATIVADA)
                .toList();

        if (doPet.isEmpty()) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Este pet ainda nao tem tag vinculada.");
        }
        for (Tag t : doPet) {
            t.setModoPerdido(ligado);
            if (t.getStatus() == StatusTag.ATIVA || t.getStatus() == StatusTag.MODO_PERDIDO) {
                t.transitarPara(ligado ? StatusTag.MODO_PERDIDO : StatusTag.ATIVA);
            }
            tags.save(t);
        }
        return doPet;
    }

    // ---- Vinculo e transicao para ATIVA ------------------------------------

    @Transactional
    public Tag vincularTag(UUID tagUuid, Pet pet, UsuarioAutenticado u) {
        Tag tag = tags.findByUuid(tagUuid).orElseThrow(() -> new ProblemaException(TipoErro.NAO_E_DONO));
        if (!tag.pertenceA(u.id())) {
            throw new ProblemaException(TipoErro.NAO_E_DONO);
        }
        if (tag.getStatus() == StatusTag.DESATIVADA) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Esta tag esta desativada e nao aceita perfil.");
        }
        tag.setPetId(pet.getId());
        tags.save(tag);
        reavaliarTags(pet, u);
        return tags.findByUuid(tagUuid).orElseThrow();
    }

    /**
     * Criterio de ATIVA: pet vinculado, com nome preenchido E ao menos um canal
     * de contato visivel. A transicao e automatica ao salvar o perfil.
     *
     * "Canal visivel" exige as duas coisas: a chave ligada na visibilidade E o
     * telefone existindo no cadastro do tutor. So a chave ligada, sem numero,
     * produziria uma tag ATIVA cuja pagina de resgate nao aciona ninguem.
     */
    @Transactional
    public void reavaliarTags(Pet pet, UsuarioAutenticado u) {
        boolean pronto = perfilPronto(pet, u.id());
        for (Tag tag : tags.findByPetId(pet.getId())) {
            if (tag.getStatus() == StatusTag.DESATIVADA || tag.getStatus() == StatusTag.EM_TRANSFERENCIA) {
                continue;
            }
            if (pronto && tag.getStatus() == StatusTag.REIVINDICADA) {
                tag.transitarPara(StatusTag.ATIVA);
                tags.save(tag);
            } else if (!pronto && (tag.getStatus() == StatusTag.ATIVA || tag.getStatus() == StatusTag.MODO_PERDIDO)) {
                // Voltou a faltar dado: a tag deixa de exibir perfil em vez de
                // mostrar uma pagina de resgate sem como acionar ninguem.
                tag.setModoPerdido(false);
                tag.transitarPara(StatusTag.REIVINDICADA);
                tags.save(tag);
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean perfilPronto(Pet pet, Long usuarioId) {
        return motivoNaoPronto(pet, usuarioId) == null;
    }

    /** Devolve null quando esta pronto; senao, o que falta — para o painel exibir. */
    @Transactional(readOnly = true)
    public String motivoNaoPronto(Pet pet, Long usuarioId) {
        if (pet.getNome() == null || pet.getNome().isBlank()) {
            return "Preencha o nome do pet.";
        }
        Optional<Usuario> tutor = usuarios.findById(usuarioId);
        if (tutor.isEmpty()) {
            return "Cadastro do tutor incompleto.";
        }
        VisibilidadePerfil v = visibilidade(pet);
        boolean telefoneUtil = v.isMostrarTelefone() && preenchido(tutor.get().getTelefonePrincipal());
        boolean whatsappUtil = v.isMostrarWhatsapp() && preenchido(tutor.get().getWhatsapp());

        if (!telefoneUtil && !whatsappUtil) {
            return "Cadastre um telefone ou WhatsApp e deixe ao menos um deles visivel.";
        }
        return null;
    }

    private boolean preenchido(String s) {
        return s != null && !s.isBlank();
    }
}
