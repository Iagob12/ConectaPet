package br.com.conectapet.publico;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.leitura.LeituraRepositorio;
import br.com.conectapet.leitura.OrigemLeitura;
import br.com.conectapet.notificacao.NotificacaoRepositorio;
import br.com.conectapet.pet.*;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.tag.*;
import br.com.conectapet.usuario.Papel;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PerfilPublicoIT extends TesteIntegracao {

    @Autowired PerfilPublicoServico perfis;
    @Autowired LeituraServico leituraServico;
    @Autowired PetServico petServico;
    @Autowired ReivindicacaoServico reivindicacao;
    @Autowired LoteServico loteServico;
    @Autowired TagRepositorio tags;
    @Autowired PetRepositorio pets;
    @Autowired PetSaudeRepositorio saudes;
    @Autowired ContatoRepositorio contatos;
    @Autowired LeituraRepositorio leituras;
    @Autowired NotificacaoRepositorio notificacoes;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired TentativaRepositorio tentativas;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private UsuarioAutenticado ana;
    private Tag tag;
    private Pet nina;

    @BeforeEach
    void preparar() {
        leituras.deleteAll();
        notificacoes.deleteAll();
        contatos.deleteAll();
        tags.deleteAll();
        pets.deleteAll();
        tentativas.deleteAll();

        ana = criarUsuario();
        Lote lote = loteServico.gerar("Lote", 1, ModeloTag.CLASSICA, null);
        Tag bruta = tags.findByLoteId(lote.getId()).get(0);
        tag = reivindicacao.reivindicar(bruta.getCodigoPublico(), ana, "ip");

        Pet p = new Pet();
        p.setNome("Nina");
        p.setEspecie(Especie.CACHORRO);
        p.setCidade("Sao Paulo");
        p.setEstado("SP");
        p.setNumeroMicrochip("900123456789012");
        nina = petServico.criar(p, tag.getUuid(), ana);
        // Reler: criar o pet ligou a tag a ele NO BANCO, e o objeto em memoria
        // ficou com petId nulo. Sem isto os testes de notificacao rodavam contra
        // uma tag que, para o servico, nao tem pet nenhum.
        tag = tags.findByUuid(tag.getUuid()).orElseThrow();

        PetSaude s = new PetSaude();
        s.setAlergias("Alergia a dipirona");
        s.setVeterinarioNome("Clinica Bicho Bom");
        s.setVeterinarioTelefone("+551133330000");
        petServico.salvarSaude(nina.getUuid(), s, ana);

        ContatoEmergencia c = new ContatoEmergencia();
        c.setNome("Carlos");
        c.setTelefone("+5511988880000");
        petServico.adicionarContato(nina.getUuid(), c, ana);
    }

    // ---- 4. Nao vazar campo oculto, campo a campo --------------------------

    @Test
    @DisplayName("4. microchip so aparece quando o tutor liga a chave")
    void microchipRespeitaVisibilidade() throws Exception {
        // Default do design system: oculto
        assertThat(serializar()).doesNotContain("900123456789012");

        ligar(v -> v.setMostrarMicrochip(true));
        assertThat(serializar()).contains("900123456789012");
    }

    @Test
    @DisplayName("4b. saude some inteira do JSON quando oculta — nao vira null nem bloco vazio")
    void saudeRespeitaVisibilidade() throws Exception {
        assertThat(serializar()).contains("dipirona");

        ligar(v -> v.setMostrarSaude(false));
        String oculto = serializar();

        assertThat(oculto).doesNotContain("dipirona", "Bicho Bom", "3333-0000");
        // A chave nem existe: sem ela nao ha rotulo orfao nem espaco reservado
        assertThat(oculto).doesNotContain("\"saude\"");
    }

    @Test
    @DisplayName("4c. contatos de emergencia respeitam a chave")
    void contatosRespeitamVisibilidade() throws Exception {
        assertThat(serializar()).contains("Carlos");

        ligar(v -> v.setMostrarContatosEmergencia(false));
        String oculto = serializar();
        assertThat(oculto).doesNotContain("Carlos", "98888");
        assertThat(oculto).doesNotContain("\"contatosEmergencia\"");
    }

    @Test
    @DisplayName("4d. cidade respeita a chave")
    void cidadeRespeitaVisibilidade() throws Exception {
        assertThat(serializar()).contains("Sao Paulo");

        ligar(v -> v.setMostrarCidade(false));
        assertThat(serializar()).doesNotContain("Sao Paulo");
    }

    @Test
    @DisplayName("4e. telefone e WhatsApp respeitam as chaves, um de cada vez")
    void telefonesRespeitamVisibilidade() throws Exception {
        assertThat(serializar()).contains("+5511999990000");

        ligar(v -> { v.setMostrarTelefone(false); v.setMostrarWhatsapp(true); });
        String soWhats = serializar();

        // A assercao olha o objeto do TUTOR, nao o JSON inteiro: "telefoneE164"
        // tambem e o nome do campo do contato de emergencia, que continua
        // visivel de proposito. Varrer o documento todo reprovava o
        // comportamento certo.
        var tutor = json.readTree(soWhats).path("tutor");
        assertThat(tutor.has("telefoneE164")).isFalse();
        assertThat(tutor.has("telefoneExibicao")).isFalse();
        assertThat(tutor.path("whatsappE164").asText()).isEqualTo("5511999990000");
    }

    @Test
    @DisplayName("4f. e-mail, sobrenome, id interno e codigo de ativacao nunca aparecem")
    void nuncaVazaDadoInterno() throws Exception {
        String corpo = serializar();

        assertThat(corpo).doesNotContain("ana@teste.com");        // e-mail do tutor
        assertThat(corpo).doesNotContain("Ana Souza");            // nome completo
        assertThat(corpo).contains("Ana");                        // so o primeiro nome
        assertThat(corpo).doesNotContain(tag.getCodigoAtivacaoHash());
        assertThat(corpo).doesNotContain("\"id\"");               // id sequencial
        assertThat(corpo).doesNotContain("usuarioId", "petId", "senhaHash");
    }

    // ---- 5. Indistinguibilidade -------------------------------------------

    @Test
    @DisplayName("5. tag inexistente e tag nao ativada produzem resposta identica")
    void inexistenteEIgualANaoAtivada() throws Exception {
        // uma tag que existe mas ainda nao foi ativada
        Lote lote = loteServico.gerar("Nova", 1, ModeloTag.SLIM, null);
        Tag naoAtivada = tags.findByLoteId(lote.getId()).get(0);

        String corpoNaoAtivada = json.writeValueAsString(perfis.montar(Optional.of(naoAtivada)));
        String corpoInexistente = json.writeValueAsString(perfis.montar(Optional.empty()));

        assertThat(corpoNaoAtivada).isEqualTo(corpoInexistente);
        assertThat(corpoNaoAtivada).contains("NAO_ATIVADA");
        assertThat(corpoNaoAtivada).doesNotContain(naoAtivada.getCodigoPublico());
    }

    @Test
    @DisplayName("5b. tag REIVINDICADA sem perfil tambem responde como nao ativada")
    void reivindicadaSemPerfilTambemEhOpaca() throws Exception {
        petServico.excluir(nina.getUuid(), ana);   // devolve a tag para REIVINDICADA
        Tag semPerfil = tags.findByUuid(tag.getUuid()).orElseThrow();

        assertThat(semPerfil.getStatus()).isEqualTo(StatusTag.REIVINDICADA);
        assertThat(json.writeValueAsString(perfis.montar(Optional.of(semPerfil))))
                .isEqualTo(json.writeValueAsString(perfis.montar(Optional.empty())));
    }

    // ---- Registrar leitura x notificar tutor -------------------------------

    @Test
    @DisplayName("robo de preview registra leitura mas NAO notifica o tutor")
    void roboNaoNotifica() {
        leituraServico.registrarAcesso(tag, "ip-robo",
                "WhatsApp/2.23 facebookexternalhit/1.1");

        assertThat(leituras.count()).isEqualTo(1);
        assertThat(leituras.findAll().get(0).getOrigem()).isEqualTo(OrigemLeitura.ROBO);
        assertThat(notificacoes.count()).as("robo nao pode disparar 'seu pet foi encontrado'").isZero();
    }

    @Test
    @DisplayName("navegador humano registra como SERVIDOR e ainda assim nao notifica")
    void servidorNaoNotifica() {
        leituraServico.registrarAcesso(tag, "ip-gente",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit/605.1.15");

        assertThat(leituras.findAll().get(0).getOrigem()).isEqualTo(OrigemLeitura.SERVIDOR);
        assertThat(notificacoes.count()).isZero();
    }

    @Test
    @DisplayName("so a confirmacao do cliente notifica, e uma vez so na janela de dedup")
    void clienteNotificaUmaVez() {
        var dados = new LeituraServico.DadosDeQuemEncontrou(false, null, null, null, "Achei ela na praca", null);

        leituraServico.confirmarLeituraHumana(tag, "ip-gente", "Mozilla/5.0", dados);
        assertThat(notificacoes.count()).isEqualTo(1);

        // mesma pessoa aproximando de novo em seguida
        leituraServico.confirmarLeituraHumana(tag, "ip-gente", "Mozilla/5.0", dados);
        leituraServico.confirmarLeituraHumana(tag, "ip-gente", "Mozilla/5.0", dados);

        assertThat(leituras.count()).as("as tres leituras ficam no historico").isEqualTo(3);
        assertThat(notificacoes.count()).as("mas so um push").isEqualTo(1);
    }

    @Test
    @DisplayName("a notificacao nao carrega o telefone de quem encontrou")
    void notificacaoNaoCarregaDadoDeTerceiro() {
        var dados = new LeituraServico.DadosDeQuemEncontrou(
                false, null, null, null, "Achei ela", "+5511977770000");

        leituraServico.confirmarLeituraHumana(tag, "ip-gente", "Mozilla/5.0", dados);

        // O corpo da notificacao vira fila e log; o telefone fica so na tabela
        // de leituras, visivel ao tutor sob autenticacao.
        assertThat(notificacoes.findAll().get(0).getConteudo()).doesNotContain("977770000");
        assertThat(leituras.findAll().get(0).getTelefoneDeQuemEncontrou()).isEqualTo("+5511977770000");
    }

    @Test
    @DisplayName("localizacao so e gravada com consentimento explicito")
    void localizacaoExigeConsentimento() {
        var semConsentimento = new LeituraServico.DadosDeQuemEncontrou(
                false, new java.math.BigDecimal("-23.55"), new java.math.BigDecimal("-46.63"), 20, null, null);

        leituraServico.confirmarLeituraHumana(tag, "ip-a", "Mozilla/5.0", semConsentimento);

        var gravada = leituras.findAll().get(0);
        assertThat(gravada.isLocalizacaoCompartilhada()).isFalse();
        assertThat(gravada.getLatitude()).isNull();
        assertThat(gravada.getLongitude()).isNull();
    }

    // ---- Apoio -------------------------------------------------------------

    private String serializar() throws Exception {
        Tag atual = tags.findByUuid(tag.getUuid()).orElseThrow();
        return json.writeValueAsString(perfis.montar(Optional.of(atual)));
    }

    private void ligar(java.util.function.Consumer<VisibilidadePerfil> ajuste) {
        VisibilidadePerfil v = petServico.visibilidade(nina);
        VisibilidadePerfil novo = new VisibilidadePerfil();
        novo.setMostrarTelefone(v.isMostrarTelefone());
        novo.setMostrarWhatsapp(v.isMostrarWhatsapp());
        novo.setMostrarContatosEmergencia(v.isMostrarContatosEmergencia());
        novo.setMostrarSaude(v.isMostrarSaude());
        novo.setMostrarCidade(v.isMostrarCidade());
        novo.setMostrarMicrochip(v.isMostrarMicrochip());
        ajuste.accept(novo);
        petServico.salvarVisibilidade(nina.getUuid(), novo, ana);
    }

    private UsuarioAutenticado criarUsuario() {
        Usuario u = new Usuario();
        u.setEmail("ana@teste.com");
        u.setNome("Ana Souza");
        u.setSenhaHash(encoder.encode("senha-de-teste-123"));
        u.setTelefonePrincipal("+5511999990000");
        u.setWhatsapp("+5511999990000");
        usuarios.save(u);
        return new UsuarioAutenticado(u.getId(), u.getUuid(), u.getEmail(), Papel.TUTOR, false);
    }
}
