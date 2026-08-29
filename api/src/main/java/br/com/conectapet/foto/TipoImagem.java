package br.com.conectapet.foto;

/**
 * Deteccao pelos bytes reais do arquivo.
 *
 * Nunca pela extensao nem pelo Content-Type declarado: os dois sao escolhidos
 * por quem envia. Um .jpg que na verdade e um HTML com script, servido do nosso
 * dominio, seria XSS armazenado.
 */
public enum TipoImagem {

    JPEG, PNG, WEBP;

    public static TipoImagem detectar(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        // FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        // 89 P N G \r \n 1A \n
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && (b[6] & 0xFF) == 0x1A && b[7] == 0x0A) {
            return PNG;
        }
        // RIFF ???? WEBP
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return WEBP;
        }
        return null;
    }
}
