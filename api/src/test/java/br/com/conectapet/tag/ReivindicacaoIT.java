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

import static org.assertj.core.api.Assertions.*;

/**
 * Reivindicacao: a rota mais atacavel do sistema.
 *
 * Reescrito depois que o codigo de ativacao impresso deixou de ser exigido. Os
 * testes que mediam "codigo de ativacao errado" sairam porque a coisa que eles
 * mediam nao existe mais — manter versoes adaptadas deles daria a impressao de
 * cobertura sobre uma protecao que foi removida de proposito.
 *
 * O que sobrou de defesa e o que continua testado aqui: o limite de tentativas
 * (que agora conta codigo inexistente), a indistinguibilidade entre codigos que
 * existem e que nao existem, e o 409 de tag com dono.
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

    @BeforeEach
    void preparar() {
        tentativas.deleteAll();
        tags.deleteAll();

        ana = criarUsuario("ana@teste.com");
        bruno = criarUsuario("bruno@teste.com");

        Lote lote = loteServico.gerar("Lote de teste", 3, ModeloTag.CLASSICA, null);
        tag = tags.findByLoteId(lote.getId()).get(0);
    }

    @Test
    @DisplayName("1. o codigo da URL basta para assumir a tag")
    void codigoDaUrlBasta() {
        Tag resultado = servico.reivindicar(tag.getCodigoPublico(), ana, "ip-ana");

        assertThat(resultado.getStatus()).isEqualTo(StatusTag.REIVINDICADA);
        assertThat(resultado.getUsuarioId()).isEqualTo(ana.id());
        assertThat(resultado.getReivindicadaEm()).isNotNull();
    }

    @Test
    @DisplayName("1b. codigo inexistente e recusado")
    void codigoInexistente() {
        assertThatThrownBy(() -> servico.reivindicar("ZZZZZZZZZZ", ana, "ip-ana"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.CODIGO_INVALIDO);
    }

    @Test
    @DisplayName("2. tag ja reivindicada devolve 409, com orientacao sobre transferencia")
    void jaReivindicada() {
        servico.reivindicar(tag.getCodigoPublico(), ana, "ip-ana");

        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), bruno, "ip-bruno"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.TAG_JA_REIVINDICADA);

        // Continua da Ana. Este 409 e a unica coisa que torna visivel um roubo
        // de tag na cadeia de entrega: o cliente ve que ela ja tem dono.
        assertThat(tags.findByCodigoPublico(tag.getCodigoPublico()).orElseThrow().getUsuarioId())
                .isEqualTo(ana.id());
    }

    @Test
    @DisplayName("3. varredura de codigos e bloqueada depois do limite, por IP")
    void varreduraPorIp() {
        // Sem o codigo de ativacao, o ataque que resta e varrer o espaco de
        // codigos publicos atras de uma tag ainda sem dono.
        for (int i = 0; i < 5; i++) {
            String inexistente = "ZZZZZZZZZ" + (char) ('2' + i);
            assertThatThrownBy(() -> servico.reivindicar(inexistente, ana, "ip-atacante"))
                    .isInstanceOf(ProblemaException.class);
        }
        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), ana, "ip-atacante"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.BLOQUEADO);
    }

    @Test
    @DisplayName("3b. o balde por codigo e GLOBAL: trocar de IP nao contorna o limite")
    void varreduraTrocandoDeIp() {
        // Um atacante com muitos enderecos, insistindo no MESMO codigo. Se o
        // balde por codigo tivesse IP na chave, cada IP novo daria 5 tentativas
        // frescas contra a mesma tag.
        tags.delete(tag);
        for (int i = 0; i < 5; i++) {
            String ip = "ip-atacante-" + i;
            assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), ana, ip))
                    .isInstanceOf(ProblemaException.class);
        }
        assertThatThrownBy(() -> servico.reivindicar(tag.getCodigoPublico(), ana, "ip-completamente-novo"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.BLOQUEADO);
    }

    @Test
    @DisplayName("5. a resposta a um codigo inexistente nao diz mais do que \"invalido\"")
    void codigoInexistenteNaoRevelaNada() {
        // Sem o codigo de ativacao, esta rota passou a distinguir codigo que
        // existe (409, ja tem dono) de codigo que nao existe (400, invalido).
        // Nao e vazamento novo: /p/{codigo} ja mostra o perfil de uma tag ativa
        // a qualquer um. O que continua travado aqui e o outro lado — o codigo
        // inexistente nao pode devolver nada especifico, senao a varredura
        // ganharia um sinal melhor do que o limite de tentativas consegue conter.
        servico.reivindicar(tag.getCodigoPublico(), ana, "ip-a");

        ProblemaException naoExiste = catchThrowableOfType(ProblemaException.class,
                () -> servico.reivindicar("ZZZZZZZZZZ", bruno, "ip-b"));

        assertThat(naoExiste.tipo()).isEqualTo(TipoErro.CODIGO_INVALIDO);
        assertThat(naoExiste.detalhe()).isNull();
    }

    @Test
    @DisplayName("erro de digitacao na forma nao consome tentativa do limite")
    void formaInvalidaNaoGastaTentativa() {
        // "0" e "L" nao existem no alfabeto: e erro de leitura, nao adivinhacao.
        assertThatThrownBy(() -> servico.reivindicar("0LLLLLLLLL", ana, "ip-ana"))
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
            servico.reivindicar(t.getCodigoPublico(), ana, "ip-ana");
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
