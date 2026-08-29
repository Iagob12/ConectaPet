package br.com.conectapet.foto;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * Decodifica, redimensiona e reencoda a foto do pet.
 *
 * Duas coisas acontecem aqui que nao sao obvias no codigo:
 *
 *  1. O EXIF some. Nao ha uma chamada "remover EXIF": ao decodificar para
 *     BufferedImage e reencodar do zero, os metadados da origem simplesmente
 *     nao sao copiados. Isso importa muito — a foto do pet quase sempre carrega
 *     as coordenadas GPS da casa do tutor, e ela vai para uma pagina publica.
 *
 *  2. As dimensoes sao lidas do cabecalho ANTES de decodificar. Um PNG de 60 KB
 *     pode declarar 30000x30000 e estourar a memoria da aplicacao inteira ao ser
 *     decodificado — a chamada bomba de descompressao. Checar o tamanho do
 *     arquivo nao protege disso.
 */
@Component
public class ProcessadorImagem {

    /** Lado maior de cada variante. A media e a que a pagina de resgate usa. */
    public static final int LADO_PEQUENA = 160;
    public static final int LADO_MEDIA = 400;
    public static final int LADO_ORIGINAL = 1200;

    private final long maxPixels;

    public ProcessadorImagem(@Value("${conectapet.foto.max-pixels:40000000}") long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public Variantes processar(byte[] bruto) {
        TipoImagem tipo = TipoImagem.detectar(bruto);
        if (tipo == null) {
            throw new ProblemaException(TipoErro.TIPO_NAO_SUPORTADO,
                    "Envie uma imagem JPEG, PNG ou WebP.");
        }

        BufferedImage original = decodificarComLimite(bruto);
        try {
            return new Variantes(
                    reencodar(original, LADO_PEQUENA),
                    reencodar(original, LADO_MEDIA),
                    reencodar(original, LADO_ORIGINAL));
        } finally {
            original.flush();
        }
    }

    /** Le o cabecalho primeiro; so decodifica se o tamanho declarado for sao. */
    private BufferedImage decodificarComLimite(byte[] bruto) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bruto))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new ProblemaException(TipoErro.TIPO_NAO_SUPORTADO,
                        "Nao consegui ler esta imagem.");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada);
                long largura = leitor.getWidth(0);
                long altura = leitor.getHeight(0);

                if (largura * altura > maxPixels) {
                    throw new ProblemaException(TipoErro.ARQUIVO_GRANDE,
                            "Esta imagem tem resolucao alta demais. Envie uma menor.");
                }
                BufferedImage img = leitor.read(0);
                if (img == null) {
                    throw new ProblemaException(TipoErro.TIPO_NAO_SUPORTADO,
                            "Nao consegui ler esta imagem.");
                }
                return img;
            } finally {
                leitor.dispose();
            }
        } catch (ProblemaException e) {
            throw e;
        } catch (Exception e) {
            // Arquivo com assinatura valida mas conteudo corrompido cai aqui.
            throw new ProblemaException(TipoErro.TIPO_NAO_SUPORTADO,
                    "Nao consegui ler esta imagem.");
        }
    }

    /**
     * Reencoda em JPEG. A saida perde qualquer metadado da origem, que e o
     * ponto: e assim que o EXIF com o GPS da casa do tutor desaparece.
     */
    private byte[] reencodar(BufferedImage origem, int ladoMaior) {
        BufferedImage redimensionada = redimensionar(origem, ladoMaior);
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {

            ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
            try {
                escritor.setOutput(fluxo);
                ImageWriteParam param = escritor.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.82f);
                // metadata nulo: nada da imagem de origem viaja junto
                escritor.write(null, new IIOImage(redimensionada, null, null), param);
            } finally {
                escritor.dispose();
            }
            fluxo.flush();
            return saida.toByteArray();
        } catch (Exception e) {
            throw new ProblemaException(TipoErro.ERRO_INTERNO, "Falha ao processar a imagem.");
        } finally {
            if (redimensionada != origem) {
                redimensionada.flush();
            }
        }
    }

    private BufferedImage redimensionar(BufferedImage origem, int ladoMaior) {
        int l = origem.getWidth();
        int a = origem.getHeight();
        if (l <= ladoMaior && a <= ladoMaior) {
            return achatar(origem);
        }
        double escala = (double) ladoMaior / Math.max(l, a);
        int nl = Math.max(1, (int) Math.round(l * escala));
        int na = Math.max(1, (int) Math.round(a * escala));

        // TYPE_INT_RGB porque JPEG nao tem canal alfa: um PNG transparente
        // viraria fundo preto se copiado direto.
        BufferedImage destino = new BufferedImage(nl, na, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nl, na);
            g.drawImage(origem, 0, 0, nl, na, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** Tira o alfa mesmo quando nao houve redimensionamento. */
    private BufferedImage achatar(BufferedImage origem) {
        if (origem.getType() == BufferedImage.TYPE_INT_RGB) {
            return origem;
        }
        BufferedImage destino = new BufferedImage(origem.getWidth(), origem.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, origem.getWidth(), origem.getHeight());
            g.drawImage(origem, 0, 0, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    public record Variantes(byte[] pequena, byte[] media, byte[] original) {}
}
