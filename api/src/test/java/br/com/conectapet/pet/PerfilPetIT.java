package br.com.conectapet.pet;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.tag.*;
import br.com.conectapet.usuario.Papel;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PerfilPetIT extends TesteIntegracao {

    @Autowired PetServico petServico;
    @Autowired ReivindicacaoServico reivindicacao;
    @Autowired LoteServico loteServico;
    @Autowired PetRepositorio pets;
    @Autowired ContatoRepositorio contatos;
    @Autowired TagRepositorio tags;
    @Autowired TentativaRepositorio tentativas;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired PasswordEncoder encoder;

    private UsuarioAutenticado ana;
    private UsuarioAutenticado bruno;
    private Tag tagDaAna;

    @BeforeEach
    void preparar() {
        contatos.deleteAll();
        tags.deleteAll();
        pets.deleteAll();
        tentativas.deleteAll();

        ana = criarUsuario("ana@teste.com", "+5511999990000");
        bruno = criarUsuario("bruno@teste.com", "+5511988880000");

        Lote lote = loteServico.gerar("Lote", 2, ModeloTag.CLASSICA, null);
        Tag bruta = tags.findByLoteId(lote.getId()).get(0);
        tagDaAna = reivindicacao.reivindicar(bruta.getCodigoPublico(), ana, "ip-ana");
    }

    // ---- Criterio de ATIVA -------------------------------------------------

    @Test
    @DisplayName("tag vai de REIVINDICADA para ATIVA sozinha ao vincular um perfil completo")
    void viraAtivaAoVincular() {
        assertThat(tagDaAna.getStatus()).isEqualTo(StatusTag.REIVINDICADA);

        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);

        assertThat(tags.findByUuid(tagDaAna.getUuid()).orElseThrow().getStatus())
                .isEqualTo(StatusTag.ATIVA);
        assertThat(petServico.motivoNaoPronto(nina, ana.id())).isNull();
    }

    @Test
    @DisplayName("sem telefone no tutor a tag NAO fica ativa, mesmo com a chave de visibilidade ligada")
    void semTelefoneNaoAtiva() {
        // Chave ligada sem numero cadastrado produziria uma pagina de resgate
        // que nao aciona ninguem.
        UsuarioAutenticado semFone = criarUsuario("semfone@teste.com", null);
        Lote lote = loteServico.gerar("Lote 2", 1, ModeloTag.SLIM, null);
        Tag bruta = tags.findByLoteId(lote.getId()).get(0);
        Tag tag = reivindicacao.reivindicar(bruta.getCodigoPublico(),
                semFone, "ip-x");

        Pet p = petServico.criar(pet("Rex"), tag.getUuid(), semFone);

        assertThat(tags.findByUuid(tag.getUuid()).orElseThrow().getStatus())
                .isEqualTo(StatusTag.REIVINDICADA);
        assertThat(petServico.motivoNaoPronto(p, semFone.id()))
                .contains("telefone");
    }

    @Test
    @DisplayName("perfil que deixa de estar pronto derruba a tag de ATIVA de volta para REIVINDICADA")
    void voltaParaReivindicadaSePerderDado() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);
        assertThat(tags.findByUuid(tagDaAna.getUuid()).orElseThrow().getStatus()).isEqualTo(StatusTag.ATIVA);

        // tutor apaga o telefone: nao ha mais canal de contato util
        Usuario u = usuarios.findById(ana.id()).orElseThrow();
        u.setTelefonePrincipal(null);
        u.setWhatsapp(null);
        usuarios.save(u);

        petServico.reavaliarTags(nina, ana);

        assertThat(tags.findByUuid(tagDaAna.getUuid()).orElseThrow().getStatus())
                .isEqualTo(StatusTag.REIVINDICADA);
    }

    // ---- Visibilidade ------------------------------------------------------

    @Test
    @DisplayName("recusa esconder telefone E WhatsApp ao mesmo tempo")
    void recusaPerfilSemCanalDeContato() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);

        VisibilidadePerfil semContato = new VisibilidadePerfil();
        semContato.setMostrarTelefone(false);
        semContato.setMostrarWhatsapp(false);

        assertThatThrownBy(() -> petServico.salvarVisibilidade(nina.getUuid(), semContato, ana))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.DADOS_INVALIDOS);

        // a configuracao anterior continua intacta
        assertThat(petServico.visibilidade(nina).isMostrarTelefone()).isTrue();
    }

    @Test
    @DisplayName("microchip nasce oculto; telefone, WhatsApp e saude nascem visiveis")
    void defaultsDoDesignSystem() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);
        VisibilidadePerfil v = petServico.visibilidade(nina);

        assertThat(v.isMostrarTelefone()).isTrue();
        assertThat(v.isMostrarWhatsapp()).isTrue();
        assertThat(v.isMostrarSaude()).isTrue();
        assertThat(v.isMostrarCidade()).isTrue();
        assertThat(v.isMostrarMicrochip()).isFalse();
    }

    // ---- Modo perdido ------------------------------------------------------

    @Test
    @DisplayName("modo perdido propaga para todas as tags do pet")
    void modoPerdidoPropaga() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);

        Lote lote = loteServico.gerar("Reserva", 1, ModeloTag.SLIM, null);
        Tag bruta = tags.findByLoteId(lote.getId()).get(0);
        Tag segunda = reivindicacao.reivindicar(bruta.getCodigoPublico(),
                ana, "ip-ana");
        petServico.vincularTag(segunda.getUuid(), nina, ana);

        petServico.definirModoPerdido(nina.getUuid(), true, ana);

        assertThat(tags.findByPetId(nina.getId()))
                .allMatch(Tag::isModoPerdido)
                .allMatch(t -> t.getStatus() == StatusTag.MODO_PERDIDO);
    }

    @Test
    @DisplayName("modo perdido em pet sem tag devolve 409")
    void modoPerdidoSemTag() {
        Pet solto = petServico.criar(pet("Sem tag"), null, ana);

        assertThatThrownBy(() -> petServico.definirModoPerdido(solto.getUuid(), true, ana))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.ESTADO_INVALIDO);
    }

    // ---- Posse (IDOR) ------------------------------------------------------

    @Test
    @DisplayName("6. Bruno nao alcanca pet, visibilidade, contato nem tag da Ana")
    void brunoNaoAlcancaNadaDaAna() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);

        ContatoEmergencia c = new ContatoEmergencia();
        c.setNome("Carlos");
        c.setTelefone("+5511977770000");
        ContatoEmergencia doAna = petServico.adicionarContato(nina.getUuid(), c, ana);

        assertThatThrownBy(() -> petServico.meuPet(nina.getUuid(), bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.atualizar(nina.getUuid(), pet("Roubado"), bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.salvarSaude(nina.getUuid(), new PetSaude(), bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.salvarVisibilidade(nina.getUuid(), new VisibilidadePerfil(), bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.definirModoPerdido(nina.getUuid(), true, bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.removerContato(nina.getUuid(), doAna.getUuid(), bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.vincularTag(tagDaAna.getUuid(), nina, bruno))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> petServico.excluir(nina.getUuid(), bruno))
                .isInstanceOf(ProblemaException.class);

        // e o pet segue intacto
        assertThat(pets.findByUuidAndExcluidoEmIsNull(nina.getUuid()).orElseThrow().getNome())
                .isEqualTo("Nina");
    }

    @Test
    @DisplayName("contato de outro pet nao pode ser editado pelo dono do primeiro")
    void contatoDeOutroPet() {
        Pet daAna = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);
        Pet doBruno = petServico.criar(pet("Thor"), null, bruno);

        ContatoEmergencia c = new ContatoEmergencia();
        c.setNome("Carlos");
        c.setTelefone("+5511977770000");
        ContatoEmergencia doBrunoContato = petServico.adicionarContato(doBruno.getUuid(), c, bruno);

        // Ana e dona do proprio pet, mas o contato e de outro: nao vale
        assertThatThrownBy(() ->
                petServico.removerContato(daAna.getUuid(), doBrunoContato.getUuid(), ana))
                .isInstanceOf(ProblemaException.class);
    }

    // ---- Teto de contatos --------------------------------------------------

    @Test
    @DisplayName("plano Free barra o segundo contato de emergencia")
    void tetoDoPlanoFree() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);

        ContatoEmergencia primeiro = new ContatoEmergencia();
        primeiro.setNome("Carlos");
        primeiro.setTelefone("+5511977770000");
        petServico.adicionarContato(nina.getUuid(), primeiro, ana);

        ContatoEmergencia segundo = new ContatoEmergencia();
        segundo.setNome("Marina");
        segundo.setTelefone("+5511966660000");

        assertThatThrownBy(() -> petServico.adicionarContato(nina.getUuid(), segundo, ana))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.LIMITE_PLANO);
    }

    // ---- Exclusao ----------------------------------------------------------

    @Test
    @DisplayName("excluir o pet devolve a tag para REIVINDICADA, sem apagar a tag")
    void excluirPetLiberaTag() {
        Pet nina = petServico.criar(pet("Nina"), tagDaAna.getUuid(), ana);
        petServico.excluir(nina.getUuid(), ana);

        Tag tag = tags.findByUuid(tagDaAna.getUuid()).orElseThrow();
        assertThat(tag.getStatus()).isEqualTo(StatusTag.REIVINDICADA);
        assertThat(tag.getPetId()).isNull();
        assertThat(tag.getUsuarioId()).isEqualTo(ana.id());
    }

    private Pet pet(String nome) {
        Pet p = new Pet();
        p.setNome(nome);
        p.setEspecie(Especie.CACHORRO);
        return p;
    }

    private UsuarioAutenticado criarUsuario(String email, String telefone) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setNome("Teste");
        u.setSenhaHash(encoder.encode("senha-de-teste-123"));
        u.setTelefonePrincipal(telefone);
        usuarios.save(u);
        return new UsuarioAutenticado(u.getId(), u.getUuid(), email, Papel.TUTOR, false);
    }
}
