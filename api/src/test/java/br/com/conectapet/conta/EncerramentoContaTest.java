package br.com.conectapet.conta;

import br.com.conectapet.autenticacao.AutenticacaoServico;
import br.com.conectapet.foto.FotoServico;
import br.com.conectapet.leitura.LeituraRepositorio;
import br.com.conectapet.pet.*;
import br.com.conectapet.tag.ModeloTag;
import br.com.conectapet.tag.StatusTag;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.tag.TagRepositorio;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Encerrar conta e irreversivel e mexe em cinco tabelas. E o tipo de operacao
 * que ninguem testa a mao duas vezes.
 */
class EncerramentoContaTest {

    private UsuarioRepositorio usuarios;
    private PetRepositorio pets;
    private PetSaudeRepositorio saudes;
    private ContatoRepositorio contatos;
    private VisibilidadeRepositorio visibilidades;
    private TagRepositorio tags;
    private LeituraRepositorio leituras;
    private FotoServico fotos;
    private AutenticacaoServico autenticacao;
    private ContaServico servico;

    private Usuario usuario;
    private Pet pet;
    private Tag tagAtiva;
    private Tag tagJaDesativada;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        pets = mock(PetRepositorio.class);
        saudes = mock(PetSaudeRepositorio.class);
        contatos = mock(ContatoRepositorio.class);
        visibilidades = mock(VisibilidadeRepositorio.class);
        tags = mock(TagRepositorio.class);
        leituras = mock(LeituraRepositorio.class);
        fotos = mock(FotoServico.class);
        autenticacao = mock(AutenticacaoServico.class);

        usuario = new Usuario();
        usuario.setId(11L);
        usuario.setUuid(UUID.randomUUID());
        usuario.setEmail("tutor@exemplo.com");
        usuario.setNome("Tutor Silva");
        usuario.setTelefonePrincipal("+5511999990000");
        usuario.setWhatsapp("+5511999990000");
        usuario.setEmailVerificadoEm(Instant.now());

        pet = new Pet();
        pet.setId(5L);
        pet.setUuid(UUID.randomUUID());
        pet.setUsuarioId(11L);
        pet.setNome("Bidu");
        pet.setNumeroMicrochip("982000123456789");
        pet.setCidade("Santos");

        tagAtiva = new Tag();
        tagAtiva.setId(21L);
        tagAtiva.setUuid(UUID.randomUUID());
        tagAtiva.setUsuarioId(11L);
        tagAtiva.setPetId(5L);
        tagAtiva.setModelo(ModeloTag.CLASSICA);
        tagAtiva.setStatus(StatusTag.ATIVA);
        tagAtiva.setModoPerdido(true);

        tagJaDesativada = new Tag();
        tagJaDesativada.setId(22L);
        tagJaDesativada.setUuid(UUID.randomUUID());
        tagJaDesativada.setUsuarioId(11L);
        tagJaDesativada.setModelo(ModeloTag.SLIM);
        tagJaDesativada.setStatus(StatusTag.DESATIVADA);

        when(usuarios.findById(11L)).thenReturn(Optional.of(usuario));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(pets.findByUsuarioIdAndExcluidoEmIsNullOrderByCriadoEmDesc(11L)).thenReturn(List.of(pet));
        when(pets.save(any(Pet.class))).thenAnswer(i -> i.getArgument(0));
        when(tags.findByUsuarioIdOrderByCriadoEmDesc(11L)).thenReturn(List.of(tagAtiva, tagJaDesativada));
        when(tags.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));
        when(contatos.findByPetIdOrderByOrdemAscIdAsc(5L)).thenReturn(List.of());
        when(saudes.findById(5L)).thenReturn(Optional.empty());

        servico = new ContaServico(usuarios, pets, saudes, contatos, visibilidades,
                tags, leituras, fotos, autenticacao);
    }

    @Test
    @DisplayName("apaga o que identifica a pessoa")
    void anonimizaUsuario() {
        servico.encerrar(11L);

        assertThat(usuario.getNome()).isEqualTo("Conta removida");
        assertThat(usuario.getTelefonePrincipal()).isNull();
        assertThat(usuario.getWhatsapp()).isNull();
        assertThat(usuario.getEmail()).doesNotContain("tutor@exemplo.com");
        assertThat(usuario.emailVerificado()).isFalse();
        assertThat(usuario.isAtivo()).isFalse();
        assertThat(usuario.getAnonimizadoEm()).isNotNull();
        assertThat(usuario.getExcluidoEm()).isNotNull();
    }

    @Test
    @DisplayName("o e-mail anonimo continua unico por conta")
    void emailAnonimoUnico() {
        // A coluna tem indice unico: dois encerramentos com o mesmo texto
        // quebrariam a insercao do segundo.
        servico.encerrar(11L);
        assertThat(usuario.getEmail())
                .contains(usuario.getUuid().toString())
                .endsWith("@conectapet.invalid");
    }

    @Test
    @DisplayName("desativa as tags em vez de apenas desvincular")
    void desativaTags() {
        servico.encerrar(11L);

        // Uma tag viva depois disso ficaria na coleira apontando para um perfil
        // que nao existe mais.
        assertThat(tagAtiva.getStatus()).isEqualTo(StatusTag.DESATIVADA);
        assertThat(tagAtiva.getPetId()).isNull();
        assertThat(tagAtiva.isModoPerdido()).isFalse();
        assertThat(tagAtiva.getDesativadaEm()).isNotNull();
    }

    @Test
    @DisplayName("tag que ja estava desativada nao quebra a transicao")
    void tagJaDesativadaSegue() {
        servico.encerrar(11L);
        assertThat(tagJaDesativada.getStatus()).isEqualTo(StatusTag.DESATIVADA);
    }

    @Test
    @DisplayName("limpa o perfil do pet e tira a foto do armazenamento")
    void limpaPet() {
        servico.encerrar(11L);

        assertThat(pet.getNome()).isEqualTo("Pet removido");
        assertThat(pet.getNumeroMicrochip()).isNull();
        assertThat(pet.getCidade()).isNull();
        assertThat(pet.getExcluidoEm()).isNotNull();
        // Marcar a linha nao basta: o arquivo continuaria no disco, e ele e
        // dado pessoal.
        verify(fotos).remover(pet);
    }

    @Test
    @DisplayName("derruba as sessoes abertas")
    void revogaSessoes() {
        servico.encerrar(11L);
        verify(autenticacao).revogarTudo(11L);
    }
}
