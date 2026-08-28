package br.com.conectapet.tag;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.seguranca.UsuarioAutenticado;
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

/**
 * Os testes de seguranca da reivindicacao — a rota mais atacavel do sistema.
 */
class ReivindicacaoIT extends TesteIntegracao {

    @Autowired ReivindicacaoServico servico;
    @Autowired LoteServico loteServico;
    @Autowired TagRepositorio tags;
    @Autowired TentativaRepositorio tentativas;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired PasswordEncoder encoder;

    private UsuarioAutenticado ana;
    private UsuarioAutenticado bruno;
    private Tag tag;
    private String codigoAtivacao;

    @BeforeEach
    void preparar() {
        tentativas.deleteAll();
        tags.deleteAll();

        ana = criarUsuario("ana@teste.com");
        bruno = criarUsuario("bruno@teste.com");

        Lote lote = loteServico.gerar("Lote de teste", 3, ModeloTag.CLASSICA, null);
        tag = tags.findByLoteId(lote.getId()).get(0);
        codigoAtivacao = tag.getCodigoAtivacaoClaro();
    }

    @Test
    @DisplayName("1. codigo de ativacao correto reivindica a tag")
    void codigoCorreto() {
        Tag resultado = servico.reivindicar(tag.getCodigoPublico(), codigoAtivacao, ana, "ip-ana");

        assertThat(resultado.getStatus()).isEqualTo(StatusTag.REIVINDICADA);
        assertThat(resultado.getUsuarioId()).isEqualTo(ana.id());
        assertThat(resultado.getReivindicadaEm()).isNotNull();
    }

    @Test
    @DisplayName("1b. codigo de ativacao incorreto e recusado")
    void codigoIncorreto() {
        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), "ZZZZZZZZ", ana, "ip-ana"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.CODIGO_INVALIDO);

        assertThat(tags.findByCodigoPublico(tag.getCodigoPublico()).orElseThrow().getUsuarioId()).isNull();
    }

    @Test
    @DisplayName("2. tag ja reivindicada devolve 409, com orientacao sobre transferencia")
    void jaReivindicada() {
        servico.reivindicar(tag.getCodigoPublico(), codigoAtivacao, ana, "ip-ana");

        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), codigoAtivacao, bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.TAG_JA_REIVINDICADA);

        // continua da Ana
        assertThat(tags.findByCodigoPublico(tag.getCodigoPublico()).orElseThrow().getUsuarioId())
                .isEqualTo(ana.id());
    }

    @Test
    @DisplayName("3. forca bruta e bloqueada depois do limite, por IP")
    void forcaBrutaPorIp() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), "ZZZZZZZZ", ana, "ip-atacante"))
                    .isInstanceOf(ProblemaException.class);
        }
        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), codigoAtivacao, ana, "ip-atacante"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.BLOQUEADO);
    }

    @Test
    @DisplayName("3b. o balde por codigo e GLOBAL: trocar de IP nao contorna o limite")
    void forcaBrutaTrocandoDeIp() {
        // Um atacante com muitos enderecos: se o balde por codigo tivesse IP na
        // chave, cada IP novo daria 5 tentativas frescas no mesmo codigo.
        for (int i = 0; i < 5; i++) {
            String ipDiferente = "ip-atacante-" + i;
            assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), "ZZZZZZZZ", ana, ipDiferente))
                    .isInstanceOf(ProblemaException.class);
        }
        assertThatThrownBy(() ->
                servico.reivindicar(tag.getCodigoPublico(), codigoAtivacao, ana, "ip-completamente-novo"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.BLOQUEADO);
    }

    @Test
    @DisplayName("5. codigo inexistente e codigo errado sao indistinguiveis")
    void indistinguivel() {
        ProblemaException naoExiste = catchThrowableOfType(
                () -> servico.reivindicar("ZZZZZZZZZZ", "ZZZZZZZZ", ana, "ip-a"),
                ProblemaException.class);

        ProblemaException existeMasErrado = catchThrowableOfType(
                () -> servico.reivindicar(tag.getCodigoPublico(), "YYYYYYYY", ana, "ip-b"),
                ProblemaException.class);

        // Mesmo tipo, mesmo status, mesmo titulo, mesmo detalhe. Se diferissem,
        // criar uma conta bastaria para enumerar todos os codigos por esta rota.
        assertThat(naoExiste.tipo()).isEqualTo(existeMasErrado.tipo());
        assertThat(naoExiste.tipo().status()).isEqualTo(existeMasErrado.tipo().status());
        assertThat(naoExiste.detalhe()).isEqualTo(existeMasErrado.detalhe());
    }

    @Test
    @DisplayName("erro de digitacao na forma nao consome tentativa do limite")
    void formaInvalidaNaoGastaTentativa() {
        // "0" e "L" nao existem no alfabeto: e erro de leitura do cartao,
        // nao tentativa de adivinhacao.
        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), "0LLLLLLL", ana, "ip-ana"))
                .isInstanceOf(ProblemaException.class);

        assertThat(tentativas.count()).isZero();
    }

    @Test
    @DisplayName("tentativa bem-sucedida nao conta para o limite (Kit Multipet)")
    void sucessoNaoContaParaOLimite() {
        // Quem compra o Kit Multipet ativa varias tags seguidas e nao pode ser
        // trancado por isso.
        Lote lote = loteServico.gerar("Kit", 5, ModeloTag.SLIM, null);
        for (Tag t : tags.findByLoteId(lote.getId())) {
            servico.reivindicar(t.getCodigoPublico(), t.getCodigoAtivacaoClaro(), ana, "ip-ana");
        }
        assertThat(tags.findByUsuarioIdOrderByCriadoEmDesc(ana.id())).hasSize(5);
    }

    private UsuarioAutenticado criarUsuario(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setNome("Teste");
        u.setSenhaHash(encoder.encode("senha-de-teste-123"));
        usuarios.save(u);
        return new UsuarioAutenticado(u.getId(), u.getUuid(), email, Papel.TUTOR, false);
    }
}
