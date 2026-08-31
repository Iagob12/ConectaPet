package br.com.conectapet.foto;

import br.com.conectapet.TesteIntegracao;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O caminho inteiro do upload, com uma foto de verdade e o banco de verdade.
 *
 * Testar so o armazenamento provaria que um byte[] volta igual. O que interessa
 * e o que acontece com uma FOTO: ela e decodificada, reduzida a tres tamanhos,
 * reencodada em JPEG, gravada, e depois lida de volta como imagem valida.
 * Qualquer elo quebrado nesse meio da um retrato vazio para quem achou o pet.
 */
@TestPropertySource(properties = "conectapet.foto.armazenamento=banco")
class FotoNoBancoIT extends TesteIntegracao {

    @Autowired private FotoServico fotos;
    @Autowired private PetRepositorio pets;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ArmazenamentoFotos armazenamento;
    @Autowired private br.com.conectapet.usuario.UsuarioRepositorio usuarios;

    private byte[] fotoDeVerdade() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/thor.jpg")) {
            assertThat(in).as("a foto de teste precisa existir em test/resources").isNotNull();
            return in.readAllBytes();
        }
    }

    /** O pet precisa de um dono de verdade: ha chave estrangeira. */
    private Pet petSalvo() {
        br.com.conectapet.usuario.Usuario dono = new br.com.conectapet.usuario.Usuario();
        dono.setEmail("dono-" + System.nanoTime() + "@exemplo.invalid");
        dono.setNome("Dono de teste");
        dono.setSenhaHash("$2a$10$abcdefghijklmnopqrstuv");
        dono.setTelefonePrincipal("+5511999990000");
        usuarios.save(dono);

        Pet p = new Pet();
        p.setUsuarioId(dono.getId());
        p.setNome("Thor");
        p.setEspecie(br.com.conectapet.pet.Especie.CACHORRO);
        return pets.save(p);
    }

    @Test
    @DisplayName("a implementacao escolhida e mesmo a do banco")
    void usaOBanco() {
        // Se o ConditionalOnProperty errar, o resto do teste passaria gravando
        // em disco e ninguem perceberia.
        assertThat(armazenamento).isInstanceOf(ArmazenamentoBanco.class);
    }

    @Test
    @DisplayName("enviar uma foto grava as tres variantes e todas voltam como imagem valida")
    void uploadCompleto() throws Exception {
        Pet thor = petSalvo();

        String chave = fotos.enviar(thor, fotoDeVerdade());

        assertThat(chave).isNotBlank();
        assertThat(pets.findById(thor.getId()).orElseThrow().getFotoChave()).isEqualTo(chave);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fotos_arquivo WHERE chave = ?", Integer.class, chave))
                .as("pequena, media e original")
                .isEqualTo(3);

        // Cada variante precisa voltar decodificavel — e no tamanho certo.
        int[] ladoMaximo = { ProcessadorImagem.LADO_PEQUENA,
                             ProcessadorImagem.LADO_MEDIA,
                             ProcessadorImagem.LADO_ORIGINAL };
        ArmazenamentoFotos.Variante[] variantes = { ArmazenamentoFotos.Variante.PEQUENA,
                                                    ArmazenamentoFotos.Variante.MEDIA,
                                                    ArmazenamentoFotos.Variante.ORIGINAL };
        for (int i = 0; i < variantes.length; i++) {
            Optional<byte[]> lida = armazenamento.ler(chave, variantes[i]);
            assertThat(lida).as("variante %s", variantes[i]).isPresent();

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(lida.get()));
            assertThat(img).as("%s precisa ser uma imagem decodificavel", variantes[i]).isNotNull();
            assertThat(Math.max(img.getWidth(), img.getHeight()))
                    .as("%s nao pode passar do lado maximo", variantes[i])
                    .isLessThanOrEqualTo(ladoMaximo[i]);
        }
    }

    @Test
    @DisplayName("trocar a foto apaga a anterior de verdade")
    void trocarApagaAAnterior() throws Exception {
        Pet thor = petSalvo();
        String primeira = fotos.enviar(thor, fotoDeVerdade());
        String segunda = fotos.enviar(thor, fotoDeVerdade());

        assertThat(segunda).isNotEqualTo(primeira);
        // A chave antiga nao pode virar lixo acumulando no banco — que e
        // exatamente o risco de guardar arquivo em tabela.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fotos_arquivo WHERE chave = ?", Integer.class, primeira))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fotos_arquivo WHERE chave = ?", Integer.class, segunda))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("o tamanho gravado bate com a estimativa que justificou a decisao")
    void tamanhoCabeNaConta() throws Exception {
        // A escolha de guardar no banco foi justificada por "~28 kB por pet".
        // Se um dia alguem mexer no processamento e isso virar 2 MB, a decisao
        // deixa de valer — e o teste avisa em vez de o Aiven avisar.
        Pet thor = petSalvo();
        String chave = fotos.enviar(thor, fotoDeVerdade());

        Integer total = jdbc.queryForObject(
                "SELECT SUM(tamanho) FROM fotos_arquivo WHERE chave = ?", Integer.class, chave);

        assertThat(total).isNotNull();
        assertThat(total)
                .as("as tres variantes somadas, em bytes")
                .isLessThan(300 * 1024);
    }
}
