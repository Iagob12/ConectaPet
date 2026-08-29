package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ProcessadorImagemTest {

    private final ProcessadorImagem processador = new ProcessadorImagem(40_000_000L);

    // ---- 8. Conteudo real, nao extensao ------------------------------------

    @Test
    @DisplayName("8. arquivo com nome de imagem mas conteudo de outra coisa e recusado")
    void recusaConteudoFalso() {
        // O caso classico: .jpg que na verdade e HTML com script. Servido do
        // nosso dominio, seria XSS armazenado.
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processador.processar(html))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.TIPO_NAO_SUPORTADO);

        assertThat(TipoImagem.detectar(html)).isNull();
    }

    @Test
    @DisplayName("8b. PDF, ZIP e SVG tambem sao recusados")
    void recusaOutrosFormatos() {
        assertThat(TipoImagem.detectar("%PDF-1.7 blablabla".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(TipoImagem.detectar(new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0})).isNull();
        assertThat(TipoImagem.detectar("<svg xmlns='http://www.w3.org/2000/svg'>".getBytes(StandardCharsets.UTF_8)))
                .isNull();
    }

    @Test
    @DisplayName("assinatura valida com corpo corrompido nao derruba a aplicacao")
    void assinaturaValidaCorpoQuebrado() {
        byte[] fingeSerJpeg = new byte[64];
        fingeSerJpeg[0] = (byte) 0xFF;
        fingeSerJpeg[1] = (byte) 0xD8;
        fingeSerJpeg[2] = (byte) 0xFF;

        assertThatThrownBy(() -> processador.processar(fingeSerJpeg))
                .isInstanceOf(ProblemaException.class);
    }

    @Test
    @DisplayName("reconhece JPEG, PNG e WebP pelos bytes")
    void reconheceOsTresFormatos() throws Exception {
        assertThat(TipoImagem.detectar(jpegSimples(50, 50))).isEqualTo(TipoImagem.JPEG);
        assertThat(TipoImagem.detectar(pngSimples(50, 50))).isEqualTo(TipoImagem.PNG);

        byte[] webp = new byte[]{'R','I','F','F', 0x1A, 0, 0, 0, 'W','E','B','P', 'V','P','8',' '};
        assertThat(TipoImagem.detectar(webp)).isEqualTo(TipoImagem.WEBP);
    }

    // ---- 9. EXIF removido --------------------------------------------------

    @Test
    @DisplayName("9. o EXIF sai da foto — e com ele o GPS da casa do tutor")
    void removeExif() throws Exception {
        byte[] comExif = jpegComExifGps(600, 400);

        // a marca esta la antes
        assertThat(contem(comExif, "GPSLatitude".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(contem(comExif, "Exif".getBytes(StandardCharsets.US_ASCII))).isTrue();

        var v = processador.processar(comExif);

        // e nao esta em nenhuma das variantes
        for (byte[] saida : new byte[][]{v.pequena(), v.media(), v.original()}) {
            assertThat(contem(saida, "GPSLatitude".getBytes(StandardCharsets.US_ASCII)))
                    .as("coordenada nao pode sobreviver ao reencode").isFalse();
            assertThat(contem(saida, "Exif".getBytes(StandardCharsets.US_ASCII))).isFalse();
            assertThat(TipoImagem.detectar(saida)).isEqualTo(TipoImagem.JPEG);
        }
    }

    // ---- Redimensionamento -------------------------------------------------

    @Test
    @DisplayName("gera tres variantes, cada uma dentro do seu lado maior")
    void geraTresVariantes() throws Exception {
        var v = processador.processar(jpegSimples(2000, 1500));

        assertThat(ladoMaior(v.pequena())).isLessThanOrEqualTo(ProcessadorImagem.LADO_PEQUENA);
        assertThat(ladoMaior(v.media())).isLessThanOrEqualTo(ProcessadorImagem.LADO_MEDIA);
        assertThat(ladoMaior(v.original())).isLessThanOrEqualTo(ProcessadorImagem.LADO_ORIGINAL);

        // a media e a que a pagina de resgate baixa: precisa ser leve
        assertThat(v.media().length).isLessThan(120_000);
    }

    @Test
    @DisplayName("mantem a proporcao, sem esticar o pet")
    void mantemProporcao() throws Exception {
        var v = processador.processar(jpegSimples(1600, 400));
        BufferedImage media = ler(v.media());

        double origem = 1600.0 / 400.0;
        double saida = (double) media.getWidth() / media.getHeight();
        assertThat(saida).isCloseTo(origem, within(0.05));
    }

    @Test
    @DisplayName("imagem menor que a variante nao e esticada")
    void naoAmplia() throws Exception {
        var v = processador.processar(jpegSimples(80, 60));
        assertThat(ladoMaior(v.media())).isEqualTo(80);
    }

    @Test
    @DisplayName("PNG transparente vira fundo branco, nao preto")
    void transparenciaViraBranco() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        // deixa tudo transparente
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", saida);

        var v = processador.processar(saida.toByteArray());
        BufferedImage resultado = ler(v.media());

        Color canto = new Color(resultado.getRGB(5, 5));
        assertThat(canto.getRed()).isGreaterThan(240);
        assertThat(canto.getGreen()).isGreaterThan(240);
        assertThat(canto.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("bomba de descompressao e barrada pelo cabecalho, sem decodificar")
    void bombaDeDescompressao() throws Exception {
        // Um limite baixo simula a imagem enorme: o ponto e que a checagem
        // acontece antes do decode, e nao depois de estourar a memoria.
        var apertado = new ProcessadorImagem(1000L);

        assertThatThrownBy(() -> apertado.processar(jpegSimples(200, 200)))
                .isInstanceOf(ProblemaException.class)
                .extracting(e -> ((ProblemaException) e).tipo())
                .isEqualTo(TipoErro.ARQUIVO_GRANDE);
    }

    // ---- Apoio -------------------------------------------------------------

    private byte[] jpegSimples(int largura, int altura) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(120, 180, 90));
        g.fillRect(0, 0, largura, altura);
        g.setColor(Color.WHITE);
        g.fillOval(largura / 4, altura / 4, largura / 2, altura / 2);
        g.dispose();

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", saida);
        return saida.toByteArray();
    }

    private byte[] pngSimples(int largura, int altura) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", saida);
        return saida.toByteArray();
    }

    /** JPEG com um segmento APP1 contendo marcas de EXIF/GPS, como sai de um celular. */
    private byte[] jpegComExifGps(int largura, int altura) throws Exception {
        byte[] base = jpegSimples(largura, altura);

        byte[] exif = ("Exif\0\0MM\0*GPSLatitude=-23.5505;GPSLongitude=-46.6333;"
                + "Make=Apple;Model=iPhone").getBytes(StandardCharsets.US_ASCII);
        int tamanho = exif.length + 2;

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(base, 0, 2);                       // SOI
        saida.write(0xFF);
        saida.write(0xE1);                             // APP1
        saida.write((tamanho >> 8) & 0xFF);
        saida.write(tamanho & 0xFF);
        saida.write(exif);
        saida.write(base, 2, base.length - 2);         // resto do JPEG
        return saida.toByteArray();
    }

    private BufferedImage ler(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private int ladoMaior(byte[] bytes) throws Exception {
        BufferedImage img = ler(bytes);
        return Math.max(img.getWidth(), img.getHeight());
    }

    private boolean contem(byte[] alvo, byte[] agulha) {
        outer:
        for (int i = 0; i <= alvo.length - agulha.length; i++) {
            for (int j = 0; j < agulha.length; j++) {
                if (alvo[i + j] != agulha[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
