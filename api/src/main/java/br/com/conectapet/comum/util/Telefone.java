package br.com.conectapet.comum.util;

/**
 * Normalizacao de telefone brasileiro.
 *
 * A API devolve dois campos — `telefoneExibicao` e `telefoneE164` — porque o
 * `wa.me` exige `5511999990000` e a tela exige `(11) 99999-0000`. Com um campo
 * so, cada cliente reimplementaria isto e erraria nos mesmos lugares: nono
 * digito, DDD com zero na frente, e o zero de operadora em ligacao interurbana.
 *
 * Guardamos em E.164 e derivamos a exibicao. O contrario perderia informacao.
 */
public final class Telefone {

    private Telefone() {}

    private static final String DDI_BR = "55";

    /**
     * Converte o que a pessoa digitou para E.164. Devolve null quando o numero
     * nao tem forma de telefone brasileiro — melhor recusar do que gravar algo
     * que vai gerar um link de WhatsApp quebrado no pior momento.
     */
    public static String paraE164(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String d = bruto.replaceAll("\\D", "");

        // Ja veio com DDI
        if (d.length() >= 12 && d.startsWith(DDI_BR)) {
            d = d.substring(DDI_BR.length());
        }
        // Zero de operadora na frente do DDD.
        //
        // So removemos no caso de 12 digitos (0 + DDD + 9 digitos de celular),
        // que e a unica leitura possivel. Com 11 digitos ha ambiguidade real:
        // "01999990000" tanto pode ser zero + DDD 19 + fixo quanto DDD 01 +
        // celular. Remover o zero ali transformaria um DDD invalido digitado
        // errado num numero valido e DIFERENTE — e a pagina de resgate ligaria
        // para um estranho. Preferimos recusar e pedir que redigite.
        if (d.length() == 12 && d.startsWith("0")) {
            d = d.substring(1);
        }
        // 10 digitos = fixo com DDD; 11 = celular com nono digito
        if (d.length() != 10 && d.length() != 11) {
            return null;
        }
        int ddd = Integer.parseInt(d.substring(0, 2));
        if (ddd < 11 || ddd > 99) {
            return null;
        }
        // Celular no Brasil sempre comeca com 9 depois do DDD
        if (d.length() == 11 && d.charAt(2) != '9') {
            return null;
        }
        return "+" + DDI_BR + d;
    }

    /** (11) 99999-0000 ou (11) 3333-0000. Devolve null se o E.164 for invalido. */
    public static String paraExibicao(String e164) {
        if (e164 == null || e164.isBlank()) {
            return null;
        }
        String d = e164.replaceAll("\\D", "");
        if (d.startsWith(DDI_BR)) {
            d = d.substring(DDI_BR.length());
        }
        if (d.length() == 11) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        }
        return e164;
    }

    /** Sem o "+", como o wa.me espera. */
    public static String paraWhatsApp(String e164) {
        return e164 == null ? null : e164.replaceAll("\\D", "");
    }

    /**
     * Normaliza para gravar. Vazio continua vazio; invalido e recusado.
     *
     * Recusar em vez de gravar cru e deliberado: o numero errado so aparece no
     * pior momento, quando alguem tenta ligar com o pet no colo.
     */
    public static String paraGravar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String e164 = paraE164(bruto);
        if (e164 == null) {
            throw new br.com.conectapet.comum.erro.ProblemaException(
                    br.com.conectapet.comum.erro.TipoErro.DADOS_INVALIDOS,
                    "Telefone invalido: use DDD e numero, como (11) 99999-0000.");
        }
        return e164;
    }

    public static boolean valido(String bruto) {
        return paraE164(bruto) != null;
    }
}
