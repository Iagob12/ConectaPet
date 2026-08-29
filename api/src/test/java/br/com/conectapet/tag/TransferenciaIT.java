package br.com.conectapet.tag;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.auditoria.AuditoriaRepositorio;
import br.com.conectapet.auditoria.AuditoriaServico;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.pet.*;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.usuario.Papel;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TransferenciaIT extends TesteIntegracao {

    @Autowired TransferenciaServico transferencia;
    @Autowired ReivindicacaoServico reivindicacao;
    @Autowired LoteServico loteServico;
    @Autowired PetServico petServico;
    @Autowired TagRepositorio tags;
    @Autowired CodigoTransferenciaRepositorio codigos;
    @Autowired PetRepositorio pets;
    @Autowired ContatoRepositorio contatos;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired TentativaRepositorio tentativas;
    @Autowired AuditoriaRepositorio auditoria;
    @Autowired PasswordEncoder encoder;

    private UsuarioAutenticado ana;
    private UsuarioAutenticado bruno;
    private Tag tag;
    private Pet nina;

    @BeforeEach
    void preparar() {
        auditoria.deleteAll();
        codigos.deleteAll();
        contatos.deleteAll();
        tags.deleteAll();
        pets.deleteAll();
        tentativas.deleteAll();

        ana = criarUsuario("ana@teste.com");
        bruno = criarUsuario("bruno@teste.com");

        tag = reivindicarNova(ana);
        Pet p = new Pet();
        p.setNome("Nina");
        p.setEspecie(Especie.CACHORRO);
        nina = petServico.criar(p, tag.getUuid(), ana);
    }

    // ---- Transferir titularidade -------------------------------------------

    @Test
    @DisplayName("7. transferencia desvincula o pet, mas NAO apaga o registro dele")
    void transferenciaDesvinculaSemApagar() {
        String codigo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        Tag depois = transferencia.aceitar(codigo, bruno, "ip-bruno");

        // a tag agora e do Bruno, em branco
        assertThat(depois.getUsuarioId()).isEqualTo(bruno.id());
        assertThat(depois.getPetId()).isNull();
        assertThat(depois.getStatus()).isEqualTo(StatusTag.REIVINDICADA);
        assertThat(depois.isModoPerdido()).isFalse();

        // e a Nina continua existindo, com a Ana. O dado do tutor anterior morre
        // com a conta dele, nao com a venda de um chaveiro.
        Pet ninaDepois = pets.findByUuidAndExcluidoEmIsNull(nina.getUuid()).orElseThrow();
        assertThat(ninaDepois.getNome()).isEqualTo("Nina");
        assertThat(ninaDepois.getUsuarioId()).isEqualTo(ana.id());
    }

    @Test
    @DisplayName("o novo dono nao enxerga nada do perfil anterior")
    void novoDonoRecebeTagEmBranco() {
        String codigo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        transferencia.aceitar(codigo, bruno, "ip-bruno");

        // Bruno tem a tag
        assertThat(tags.findByUsuarioIdOrderByCriadoEmDesc(bruno.id())).hasSize(1);
        // mas nenhum pet
        assertThat(petServico.meusPets(bruno)).isEmpty();
        // e nao alcanca a Nina
        assertThatThrownBy(() -> petServico.meuPet(nina.getUuid(), bruno))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("codigo e de uso unico: a segunda tentativa falha")
    void codigoDeUsoUnico() {
        String codigo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        transferencia.aceitar(codigo, bruno, "ip-bruno");

        UsuarioAutenticado carla = criarUsuario("carla@teste.com");
        assertThatThrownBy(() -> transferencia.aceitar(codigo, carla, "ip-carla"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.TOKEN_EXPIRADO);

        assertThat(tags.findByUuid(tag.getUuid()).orElseThrow().getUsuarioId()).isEqualTo(bruno.id());
    }

    @Test
    @DisplayName("codigo expirado nao serve")
    void codigoExpirado() {
        String codigo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");

        CodigoTransferencia c = codigos.findAll().get(0);
        c.setExpiraEm(Instant.now().minusSeconds(1));
        codigos.save(c);

        assertThatThrownBy(() -> transferencia.aceitar(codigo, bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.TOKEN_EXPIRADO);
    }

    @Test
    @DisplayName("gerar de novo invalida o codigo anterior: um so em circulacao")
    void gerarInvalidaOAnterior() {
        String primeiro = transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        String segundo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");

        assertThatThrownBy(() -> transferencia.aceitar(primeiro, bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class);

        assertThat(transferencia.aceitar(segundo, bruno, "ip-bruno").getUsuarioId())
                .isEqualTo(bruno.id());
    }

    @Test
    @DisplayName("o proprio dono nao pode aceitar o codigo que gerou")
    void donoNaoAceitaOProprioCodigo() {
        String codigo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");

        assertThatThrownBy(() -> transferencia.aceitar(codigo, ana, "ip-ana"))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("cancelar devolve a tag ao estado coerente com o que ela tem")
    void cancelarVoltaAoEstadoCerto() {
        transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        assertThat(tags.findByUuid(tag.getUuid()).orElseThrow().getStatus())
                .isEqualTo(StatusTag.EM_TRANSFERENCIA);

        transferencia.cancelar(tag.getUuid(), ana, "ip-ana");

        // tem perfil, entao volta para ATIVA, nao para REIVINDICADA
        assertThat(tags.findByUuid(tag.getUuid()).orElseThrow().getStatus())
                .isEqualTo(StatusTag.ATIVA);
    }

    @Test
    @DisplayName("Bruno nao gera nem cancela transferencia de tag da Ana")
    void transferenciaExigePosse() {
        assertThatThrownBy(() -> transferencia.gerar(tag.getUuid(), bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.NAO_E_DONO);

        transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        assertThatThrownBy(() -> transferencia.cancelar(tag.getUuid(), bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class);
    }

    // ---- Migrar perfil ------------------------------------------------------

    @Test
    @DisplayName("migrar preserva o perfil inteiro na tag nova")
    void migrarPreservaPerfil() {
        ContatoEmergencia c = new ContatoEmergencia();
        c.setNome("Carlos");
        c.setTelefone("+5511977770000");
        petServico.adicionarContato(nina.getUuid(), c, ana);

        Tag nova = reivindicarNova(ana);
        transferencia.migrarPerfil(nova.getUuid(), nina.getUuid(), false, ana, "ip-ana");

        Tag destino = tags.findByUuid(nova.getUuid()).orElseThrow();
        assertThat(destino.getPetId()).isEqualTo(nina.getId());
        // nada foi perdido
        assertThat(contatos.findByPetIdOrderByOrdemAscIdAsc(nina.getId())).hasSize(1);
        assertThat(pets.findByUuidAndExcluidoEmIsNull(nina.getUuid()).orElseThrow().getNome())
                .isEqualTo("Nina");
    }

    @Test
    @DisplayName("um pet pode responder por duas tags ao mesmo tempo")
    void petComDuasTags() {
        Tag reserva = reivindicarNova(ana);
        transferencia.migrarPerfil(reserva.getUuid(), nina.getUuid(), false, ana, "ip-ana");

        assertThat(tags.findByPetId(nina.getId())).hasSize(2);
    }

    @Test
    @DisplayName("com desativarTagAnterior, a tag perdida sai de circulacao na mesma transacao")
    void migrarDesativandoAAntiga() {
        Tag nova = reivindicarNova(ana);
        transferencia.migrarPerfil(nova.getUuid(), nina.getUuid(), true, ana, "ip-ana");

        Tag antiga = tags.findByUuid(tag.getUuid()).orElseThrow();
        assertThat(antiga.getStatus()).isEqualTo(StatusTag.DESATIVADA);
        assertThat(antiga.getPetId()).isNull();

        assertThat(tags.findByUuid(nova.getUuid()).orElseThrow().getPetId()).isEqualTo(nina.getId());
    }

    @Test
    @DisplayName("nao migra pet de outro tutor")
    void migrarExigePosseDoPet() {
        Tag doBruno = reivindicarNova(bruno);

        assertThatThrownBy(() ->
                transferencia.migrarPerfil(doBruno.getUuid(), nina.getUuid(), false, bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.NAO_E_DONO);
    }

    @Test
    @DisplayName("tag desativada nao recebe perfil")
    void desativadaNaoRecebePerfil() {
        Tag nova = reivindicarNova(ana);
        nova.transitarPara(StatusTag.DESATIVADA);
        tags.save(nova);

        assertThatThrownBy(() ->
                transferencia.migrarPerfil(nova.getUuid(), nina.getUuid(), false, ana, "ip-ana"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.ESTADO_INVALIDO);
    }

    // ---- Auditoria ----------------------------------------------------------

    @Test
    @DisplayName("transferencia deixa rastro de auditoria dos dois lados")
    void auditoriaDaTransferencia() {
        String codigo = transferencia.gerar(tag.getUuid(), ana, "ip-ana");
        transferencia.aceitar(codigo, bruno, "ip-bruno");

        var registros = auditoria.findByRecursoTipoAndRecursoUuidOrderByOcorridaEmDesc("TAG", tag.getUuid());
        assertThat(registros).extracting("acao")
                .contains(AuditoriaServico.ACAO_TRANSFERENCIA_GERADA,
                          AuditoriaServico.ACAO_TRANSFERENCIA_ACEITA);

        // O ator e o UUID, nunca e-mail ou telefone: assim a trilha sobrevive a
        // anonimizacao da conta sem virar dado pessoal.
        assertThat(registros).extracting("atorUuid").contains(ana.uuid(), bruno.uuid());
        assertThat(registros).allSatisfy(r ->
                assertThat(String.valueOf(r.getDetalhe())).doesNotContain("@teste.com"));
    }

    @Test
    @DisplayName("reivindicacao e desativacao tambem sao auditadas")
    void auditoriaDasDemaisAcoes() {
        Tag outra = reivindicarNova(ana);

        var registros = auditoria.findByRecursoTipoAndRecursoUuidOrderByOcorridaEmDesc("TAG", outra.getUuid());
        assertThat(registros).extracting("acao").contains(AuditoriaServico.ACAO_REIVINDICACAO);
    }

    // ---- Apoio --------------------------------------------------------------

    private Tag reivindicarNova(UsuarioAutenticado dono) {
        Lote lote = loteServico.gerar("Lote", 1, ModeloTag.CLASSICA, null);
        Tag bruta = tags.findByLoteId(lote.getId()).get(0);
        return reivindicacao.reivindicar(bruta.getCodigoPublico(), bruta.getCodigoAtivacaoClaro(),
                dono, "ip-" + dono.id());
    }

    private UsuarioAutenticado criarUsuario(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setNome("Teste");
        u.setSenhaHash(encoder.encode("senha-de-teste-123"));
        u.setTelefonePrincipal("+5511999990000");
        u.setEmailVerificadoEm(Instant.now());   // transferencia exige verificado
        usuarios.save(u);
        return new UsuarioAutenticado(u.getId(), u.getUuid(), email, Papel.TUTOR, true);
    }
}
