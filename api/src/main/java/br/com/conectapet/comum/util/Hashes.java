package br.com.conectapet.comum.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hashes {

    private Hashes() {}

    /** Para token opaco guardado em banco: nunca o token em claro. */
    public static String sha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    /**
     * Pseudonimiza o IP de quem le a tag.
     *
     * HMAC com pimenta guardada FORA do banco, truncado a 16 bytes. Salt fixo
     * dentro do banco nao serviria: o IPv4 tem 4 bilhoes de valores, e quem
     * obtivesse o banco calcularia a tabela inteira uma vez e reverteria todos
     * os IPs de todas as leituras. Com a pimenta fora, vazar o banco sozinho
     * nao desanonimiza ninguem.
     */
    public static String ipPseudonimo(String ip, String pimenta) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pimenta.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bruto = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            byte[] truncado = new byte[16];
            System.arraycopy(bruto, 0, truncado, 0, 16);
            return HexFormat.of().formatHex(truncado);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponivel", e);
        }
    }
}
