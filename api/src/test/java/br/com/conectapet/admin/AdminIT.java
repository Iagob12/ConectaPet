package br.com.conectapet.admin;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class AdminIT extends TesteIntegracao {

    private static final String SENHA = "senha-de-admin-123";

    @Autowired LoteServico loteServico;
    @Autowired ReautenticacaoServico reautenticacao;
    @Autowired MetricasServico metricas;
    @Autowired LoteRepositorio lotes;
    @Autowired TagRepositorio tags;
    @Autowired TentativaRepositorio tentativas;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired PasswordEncoder encoder;

    private UsuarioAutenticado admin;

    @BeforeEach
    void preparar() {
        tentativas.deleteAll();
        tags.deleteAll();
        lotes.deleteAll();

        Usuario u = new Usuario();
        u.setEmail("admin@teste.com");
        u.setNome("Admin");
        u.setSenhaHash(encoder.encode(SENHA));
        u.setPapel(Papel.ADMIN);
        u.setEmailVerificadoEm(Instant.now());
        usuarios.save(u);
        admin = new UsuarioAutenticado(u.getId(), u.getUuid(), u.getEmail(), Papel.ADMIN, true);
    }

    // ---- Reautenticacao -----------------------------------------------------

    @Test
    @DisplayName("senha errada nao eleva")
    void senhaErradaNaoEleva() {
        assertThatThrownBy(() -> reautenticacao.elevar(admin, "senha-errada"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.SEM_PERMISSAO);
    }

    @Test
    @DisplayName("token de elevacao nao vale para outro admin")
    void elevacaoNaoAtravessaUsuario() {
        var elevacao = reautenticacao.elevar(admin, SENHA);

        Usuario outro = new Usuario();
        outro.setEmail("outro@teste.com");
        outro.setNome("Outro");
        outro.setSenhaHash(encoder.encode(SENHA));
        outro.setPapel(Papel.ADMIN);
        usuarios.save(outro);
        var outroAdmin = new UsuarioAutenticado(outro.getId(), outro.getUuid(),
                outro.getEmail(), Papel.ADMIN, true);

        assertThatThrownBy(() -> reautenticacao.exigir(elevacao.token(), outroAdmin))
                .isInstanceOf(ProblemaException.class);

        // para o dono, vale
        assertThatCode(() -> reautenticacao.exigir(elevacao.token(), admin)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("token ausente ou inventado nao passa")
    void tokenInvalido() {
        assertThatThrownBy(() -> reautenticacao.exigir(null, admin))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> reautenticacao.exigir("", admin))
                .isInstanceOf(ProblemaException.class);
        assertThatThrownBy(() -> reautenticacao.exigir("token-inventado", admin))
                .isInstanceOf(ProblemaException.class);
    }

    // ---- Ciclo de vida do lote ----------------------------------------------

    @Test
    @DisplayName("lote nasce NAO_CONFIRMADO com os codigos recuperaveis")
    void loteNasceRecuperavel() {
        Lote lote = loteServico.gerar("Piloto", 5, ModeloTag.SLIM, null);

        assertThat(lote.getStatus()).isEqualTo(StatusLote.NAO_CONFIRMADO);
        assertThat(tags.findByLoteId(lote.getId()))
                .hasSize(5)
                .allSatisfy(t -> assertThat(t.getCodigoAtivacaoClaro()).isNotBlank());

        String csv = loteServico.csv(lote.getId(), "https://conectapet.com.br/p/");
        assertThat(csv.lines().count()).isEqualTo(6);   // cabecalho + 5
        assertThat(csv).startsWith("codigo_publico,codigo_ativacao,url");
    }

    @Test
    @DisplayName("confirmar apaga os codigos em claro e o CSV deixa de existir")
    void confirmarEhIrreversivel() {
        Lote lote = loteServico.gerar("Piloto", 3, ModeloTag.CLASSICA, null);
        loteServico.confirmar(lote.getId());

        assertThat(lotes.findById(lote.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusLote.CONFIRMADO);
        assertThat(tags.findByLoteId(lote.getId()))
                .allSatisfy(t -> assertThat(t.getCodigoAtivacaoClaro()).isNull());

        assertThatThrownBy(() -> loteServico.csv(lote.getId(), "https://x/"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.ESTADO_INVALIDO);
    }

    @Test
    @DisplayName("o hash sobrevive a confirmacao: a tag continua reivindicavel")
    void hashSobreviveAConfirmacao() {
        Lote lote = loteServico.gerar("Piloto", 1, ModeloTag.CLASSICA, null);
        Tag tag = tags.findByLoteId(lote.getId()).get(0);
        String ativacao = tag.getCodigoAtivacaoClaro();

        loteServico.confirmar(lote.getId());

        // Apagar o claro nao pode inutilizar a tag ja gravada e enviada.
        Tag depois = tags.findByUuid(tag.getUuid()).orElseThrow();
        assertThat(depois.getCodigoAtivacaoClaro()).isNull();
        assertThat(encoder.matches(ativacao, depois.getCodigoAtivacaoHash())).isTrue();
    }

    @Test
    @DisplayName("confirmar duas vezes nao quebra nem reapaga nada")
    void confirmarEhIdempotente() {
        Lote lote = loteServico.gerar("Piloto", 2, ModeloTag.SLIM, null);
        loteServico.confirmar(lote.getId());
        assertThatCode(() -> loteServico.confirmar(lote.getId())).doesNotThrowAnyException();
    }

    // ---- Codigos gerados ----------------------------------------------------

    @Test
    @DisplayName("um lote grande nao repete codigo publico")
    void codigosUnicosNoLote() {
        Lote lote = loteServico.gerar("Grande", 200, ModeloTag.CLASSICA, null);

        assertThat(tags.findByLoteId(lote.getId()))
                .hasSize(200)
                .extracting(Tag::getCodigoPublico)
                .doesNotHaveDuplicates();
    }

    // ---- Metricas -----------------------------------------------------------

    @Test
    @DisplayName("taxa de ativacao usa as ENVIADAS como base, nao as produzidas")
    void taxaUsaEnviadas() {
        Lote lote = loteServico.gerar("Piloto", 10, ModeloTag.CLASSICA, null);
        var doLote = tags.findByLoteId(lote.getId());

        // 4 enviadas, das quais 2 ja reivindicadas
        for (int i = 0; i < 4; i++) {
            Tag t = doLote.get(i);
            t.transitarPara(StatusTag.ENVIADA);
            t.setEnviadaEm(Instant.now());
            if (i < 2) {
                t.transitarPara(StatusTag.REIVINDICADA);
                t.setReivindicadaEm(Instant.now());
            }
            tags.save(t);
        }

        var m = metricas.calcular(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));

        assertThat(m.tagsProduzidas()).isEqualTo(10);
        assertThat(m.tagsEnviadas()).isEqualTo(4);
        assertThat(m.tagsAtivadas()).isEqualTo(2);
        // 2 de 4 enviadas = 50%. Sobre as 10 produzidas seriam 20%, o que
        // faria a metrica parecer pior por causa de estoque parado.
        assertThat(m.taxaAtivacao()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("sem tags enviadas a taxa e zero, nao divisao por zero")
    void taxaSemEnviadas() {
        loteServico.gerar("Estoque", 5, ModeloTag.SLIM, null);
        var m = metricas.calcular(Instant.now().minusSeconds(60), Instant.now());

        assertThat(m.tagsEnviadas()).isZero();
        assertThat(m.taxaAtivacao()).isZero();
    }
}
