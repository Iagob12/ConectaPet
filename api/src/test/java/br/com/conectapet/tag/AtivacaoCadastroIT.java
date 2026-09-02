package br.com.conectapet.tag;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.pet.Especie;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetRepositorio;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.usuario.Papel;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Trava a garantia central do primeiro cadastro: ou termina tudo, ou nada. */
class AtivacaoCadastroIT extends TesteIntegracao {

    @Autowired AtivacaoCadastroServico ativacao;
    @Autowired ReivindicacaoServico reivindicacao;
    @Autowired LoteServico lotes;
    @Autowired TagRepositorio tags;
    @Autowired PetRepositorio pets;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired PasswordEncoder encoder;

    private Tag tag;

    @BeforeEach
    void preparar() {
        Lote lote = lotes.gerar("Primeiro cadastro", 1, ModeloTag.CLASSICA, null);
        tag = tags.findByLoteId(lote.getId()).getFirst();
    }

    @Test
    @DisplayName("confirmar cria o pet e ativa a NFC na mesma transacao")
    void confirmaTudoJunto() {
        UsuarioAutenticado tutor = criarUsuario("tutor@teste.com", "+5511999990000");

        AtivacaoCadastroServico.Resultado resultado = ativacao.confirmar(
                tag.getCodigoPublico(), pet("Nina"), tutor, "ip-tutor");

        Tag gravada = tags.findByUuid(resultado.tag().getUuid()).orElseThrow();
        assertThat(gravada.getStatus()).isEqualTo(StatusTag.ATIVA);
        assertThat(gravada.getUsuarioId()).isEqualTo(tutor.id());
        assertThat(gravada.getPetId()).isEqualTo(resultado.pet().getId());
        assertThat(gravada.getReivindicadaEm()).isNotNull();
        assertThat(pets.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("falha no perfil desfaz pet e reivindicacao, deixando o link livre")
    void falhaNaoBloqueiaANfc() {
        UsuarioAutenticado semContato = criarUsuario("sem-contato@teste.com", null);

        assertThatThrownBy(() -> ativacao.confirmar(
                tag.getCodigoPublico(), pet("Nina"), semContato, "ip-tutor"))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.DADOS_INVALIDOS);

        Tag livre = tags.findByCodigoPublico(tag.getCodigoPublico()).orElseThrow();
        assertThat(livre.getStatus()).isEqualTo(StatusTag.CRIADA);
        assertThat(livre.getUsuarioId()).isNull();
        assertThat(livre.getPetId()).isNull();
        assertThat(livre.getReivindicadaEm()).isNull();
        assertThat(pets.count()).isZero();
    }

    @Test
    @DisplayName("o mesmo tutor consegue concluir uma tag presa pelo fluxo antigo")
    void retomaCadastroAntigo() {
        UsuarioAutenticado tutor = criarUsuario("retomada@teste.com", "+5511999990000");
        reivindicacao.reivindicar(tag.getCodigoPublico(), tutor, "ip-antigo");

        AtivacaoCadastroServico.Resultado resultado = ativacao.confirmar(
                tag.getCodigoPublico(), pet("Nina"), tutor, "ip-retomada");

        Tag gravada = tags.findByUuid(resultado.tag().getUuid()).orElseThrow();
        assertThat(gravada.getStatus()).isEqualTo(StatusTag.ATIVA);
        assertThat(gravada.getPetId()).isEqualTo(resultado.pet().getId());
    }

    private Pet pet(String nome) {
        Pet pet = new Pet();
        pet.setNome(nome);
        pet.setEspecie(Especie.CACHORRO);
        return pet;
    }

    private UsuarioAutenticado criarUsuario(String email, String telefone) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNome("Tutor de teste");
        usuario.setTelefonePrincipal(telefone);
        usuario.setSenhaHash(encoder.encode("senha-de-teste-123"));
        usuarios.save(usuario);
        return new UsuarioAutenticado(usuario.getId(), usuario.getUuid(), email, Papel.TUTOR, false);
    }
}
