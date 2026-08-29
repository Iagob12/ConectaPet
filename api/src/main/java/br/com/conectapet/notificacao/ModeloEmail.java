package br.com.conectapet.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Os textos que o cliente recebe.
 *
 * Cada mensagem sai em duas versoes: texto puro e HTML. O texto puro nao e
 * cortesia — e o que aparece na previa da notificacao do celular, e o que resta
 * quando o cliente de e-mail bloqueia HTML, que e o padrao de varios deles.
 * Por isso ele e escrito primeiro e sozinho ja precisa bastar; o HTML so
 * acrescenta hierarquia.
 *
 * Tres regras que valem para todos:
 *
 * 1. O assunto diz o que aconteceu, nao o nome do produto. "ConectaPet -
 *    notificacao" obriga a abrir para descobrir se importa; numa tela de
 *    bloqueio, obrigar a abrir e obrigar a ignorar.
 * 2. Nenhum segredo no corpo. Codigo de transferencia e telefone de quem
 *    encontrou ficam fora: e-mail e o canal mais provavel de estar
 *    comprometido, e e o unico que a pessoa reenvia sem pensar.
 * 3. Todo link e absoluto e visivel tambem como texto, porque cliente que
 *    bloqueia HTML transforma <a> em nada.
 */
@Component
public class ModeloEmail {

    private static final Logger log = LoggerFactory.getLogger(ModeloEmail.class);

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'às' HH'h'mm", new Locale("pt", "BR"));

    private final ObjectMapper json;
    private final String urlSite;

    public ModeloEmail(ObjectMapper json, @Value("${conectapet.site.url}") String urlSite) {
        this.json = json;
        this.urlSite = urlSite;
    }

    public record Mensagem(String assunto, String texto, String html) {}

    public Mensagem montar(Notificacao n) {
        JsonNode c = ler(n.getConteudo());
        return switch (n.getTipo()) {
            case LEITURA_TAG -> leituraTag(c);
            case VERIFICACAO_EMAIL -> verificacaoEmail(c);
            case RESET_SENHA -> resetSenha(c);
            case TRANSFERENCIA_SOLICITADA -> transferencia(c);
            case TAG_REIVINDICADA -> tagReivindicada(c);
            case RESUMO_DIARIO -> resumoDiario(c);
        };
    }

    // ---- Leitura da tag ----------------------------------------------------

    /**
     * A mensagem mais importante do produto, e a mais facil de errar.
     *
     * Ela dispara em toda leitura humana, inclusive quando o proprio dono
     * encosta o celular na tag para testar. Por isso o assunto do caso comum e
     * factual — "alguem abriu a pagina do Thor" — e nao "seu pet foi
     * encontrado": prometer resgate a cada teste ensina a pessoa a ignorar o
     * aviso, e no dia que importa ela ignora tambem.
     *
     * Com o modo perdido ligado a situacao e outra e o assunto muda de tom.
     */
    private Mensagem leituraTag(JsonNode c) {
        String pet = texto(c, "petNome", "seu pet");
        boolean perdido = c.path("modoPerdido").asBoolean(false);
        boolean localizacao = c.path("temLocalizacao").asBoolean(false);
        boolean mensagem = c.path("temMensagem").asBoolean(false);
        String quando = data(c.path("ocorridaEm").asText(null));
        String link = urlSite + "/app/pet/" + texto(c, "petUuid", "") + "/leituras";

        String assunto = perdido
                ? "Alguém acabou de ler a tag do " + pet
                : "A página do " + pet + " foi aberta";

        StringBuilder t = new StringBuilder();
        t.append(perdido
                ? "Alguém encostou o celular na tag do " + pet + ".\n\n"
                : "Alguém abriu a página do " + pet + ".\n\n");
        t.append("Quando: ").append(quando).append("\n");

        if (localizacao && mensagem) {
            t.append("A pessoa enviou a localização e deixou um recado.\n");
        } else if (localizacao) {
            t.append("A pessoa enviou a localização de onde encontrou.\n");
        } else if (mensagem) {
            t.append("A pessoa deixou um recado.\n");
        } else {
            t.append("A pessoa não deixou recado nem localização.\n");
        }

        // O telefone e o recado ficam fora do e-mail de proposito: sao dados de
        // um terceiro que ajudou, e o painel exige senha para le-los.
        t.append("\nO recado, o telefone e o mapa estão no painel:\n").append(link).append("\n");

        if (!perdido) {
            t.append("\nSe foi você testando a tag, está tudo certo — é assim que ela funciona.\n");
        }

        return new Mensagem(assunto, rodapeTexto(t.toString()),
                html(assunto, corpoHtml(t.toString(), "Ver no painel", link)));
    }

    // ---- Conta -------------------------------------------------------------

    private Mensagem verificacaoEmail(JsonNode c) {
        String nome = primeiroNome(texto(c, "nome", ""));
        String link = texto(c, "link", urlSite);
        String assunto = "Confirme seu e-mail na ConectaPet";

        String t = (nome.isBlank() ? "Olá!" : "Olá, " + nome + "!") + "\n\n"
                + "Confirme este endereço para poder recuperar sua conta se esquecer a senha,\n"
                + "e para transferir uma tag para outra pessoa:\n\n"
                + link + "\n\n"
                + "Sua tag já funciona normalmente enquanto isso — nada depende deste passo\n"
                + "para o seu pet estar protegido.\n\n"
                + "Se não foi você quem criou a conta, ignore este e-mail.\n";

        return new Mensagem(assunto, rodapeTexto(t),
                html(assunto, corpoHtml(t, "Confirmar meu e-mail", link)));
    }

    private Mensagem resetSenha(JsonNode c) {
        String nome = primeiroNome(texto(c, "nome", ""));
        String link = texto(c, "link", urlSite);
        long minutos = c.path("validadeMinutos").asLong(60);
        String assunto = "Criar uma nova senha na ConectaPet";

        String t = (nome.isBlank() ? "Olá!" : "Olá, " + nome + "!") + "\n\n"
                + "Use o endereço abaixo para escolher uma senha nova. Ele vale por "
                + duracao(minutos) + "\ne só pode ser usado uma vez:\n\n"
                + link + "\n\n"
                + "Ao salvar a senha nova, todos os aparelhos conectados à sua conta são\n"
                + "desconectados.\n\n"
                // Nao alarma sem motivo: pedido de senha e frequentemente engano
                // da propria pessoa. Mas diz o que fazer se nao for.
                + "Se não foi você que pediu, ignore este e-mail — sua senha atual continua\n"
                + "valendo e ninguém consegue entrar sem ela.\n";

        return new Mensagem(assunto, rodapeTexto(t),
                html(assunto, corpoHtml(t, "Criar nova senha", link)));
    }

    /**
     * Alerta de seguranca, nao instrucao de uso.
     *
     * O codigo de transferencia nao esta aqui — quem gerou ja o viu na tela, e
     * mandar por e-mail um codigo que transfere a posse da tag seria colocar o
     * portador no canal mais facil de vazar. Este e-mail existe para o caso
     * inverso: avisar o dono quando NAO foi ele que gerou.
     */
    private Mensagem transferencia(JsonNode c) {
        long minutos = c.path("validadeMinutos").asLong(15);
        String link = urlSite + "/app/tag/" + texto(c, "tagUuid", "");
        String assunto = "Um código de transferência foi gerado para a sua tag";

        String t = "Alguém gerou um código para passar uma das suas tags para outra pessoa.\n\n"
                + "O código vale por " + duracao(minutos) + " e serve uma vez só. Quem digitá-lo\n"
                + "passa a ser o dono da tag.\n\n"
                + "Por segurança, o código não vai neste e-mail: ele aparece apenas na tela\n"
                + "de quem o gerou.\n\n"
                + "Se foi você, não precisa fazer nada.\n\n"
                + "Se não foi, cancele agora e troque sua senha:\n"
                + link + "\n";

        return new Mensagem(assunto, rodapeTexto(t),
                html(assunto, corpoHtml(t, "Ver a tag", link)));
    }

    /** Ainda nao e enfileirado por ninguem; o texto existe para quando for. */
    private Mensagem tagReivindicada(JsonNode c) {
        String pet = texto(c, "petNome", "seu pet");
        String link = urlSite + "/app";
        String assunto = "Sua tag foi ativada";

        String t = "A tag do " + pet + " foi ativada e já está funcionando.\n\n"
                + "A partir de agora, quem encostar o celular nela vê o perfil dele e\n"
                + "consegue falar com você.\n\n"
                + "Confira o que aparece na página pública:\n" + link + "\n\n"
                + "Se não foi você que ativou, fale com a gente imediatamente.\n";

        return new Mensagem(assunto, rodapeTexto(t),
                html(assunto, corpoHtml(t, "Ver meus pets", link)));
    }

    /** Idem: reservado, sem remetente ainda. */
    private Mensagem resumoDiario(JsonNode c) {
        String link = urlSite + "/app";
        String assunto = "Seu resumo da ConectaPet";
        String t = "Aqui está o resumo da atividade das suas tags.\n\n" + link + "\n";
        return new Mensagem(assunto, rodapeTexto(t), html(assunto, corpoHtml(t, "Abrir o painel", link)));
    }

    // ---- Montagem ----------------------------------------------------------

    private String rodapeTexto(String corpo) {
        return corpo
                + "\n--\nConectaPet · Aproxima e Protege\n"
                + urlSite + "\n";
    }

    /**
     * HTML deliberadamente pobre: tabela nenhuma, imagem nenhuma, estilo no
     * atributo. Cliente de e-mail nao tem cascata confiavel, e imagem remota
     * vira rastreador de abertura — que aqui nao serve para nada e diz a
     * terceiros quando o tutor leu um aviso sobre o pet dele.
     */
    private String html(String titulo, String corpo) {
        return """
                <!doctype html>
                <html lang="pt-BR"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;padding:24px;background:#FCEBD2;
                             font:16px/1.55 -apple-system,Segoe UI,Roboto,sans-serif;color:#3B4E59">
                <div style="max-width:520px;margin:0 auto;background:#fff;border:2px solid #22313A;
                            border-radius:20px;padding:24px">
                <p style="margin:0 0 20px;font-weight:700;color:#1D6B69;letter-spacing:-.01em">ConectaPet</p>
                %s
                </div>
                <p style="max-width:520px;margin:16px auto 0;font-size:13px;color:#3B4E59">
                ConectaPet · Aproxima e Protege — <a href="%s" style="color:#1D6B69">%s</a>
                </p>
                </body></html>
                """.formatted(escapar(titulo), corpo, urlSite, urlSite);
    }

    /**
     * Converte o texto puro em paragrafos e acrescenta o botao.
     *
     * A URL continua visivel como texto embaixo do botao: cliente que bloqueia
     * HTML apaga o botao, e sem a URL escrita a pessoa fica sem saida.
     */
    private String corpoHtml(String texto, String rotulo, String link) {
        StringBuilder sb = new StringBuilder();
        for (String par : texto.trim().split("\n\n")) {
            String limpo = par.trim();
            if (limpo.isEmpty() || limpo.equals(link)) {
                continue;
            }
            sb.append("<p style=\"margin:0 0 16px\">")
              .append(escapar(limpo).replace("\n", "<br>"))
              .append("</p>");
        }
        sb.append("<p style=\"margin:24px 0 8px\">")
          .append("<a href=\"").append(escapar(link)).append("\" ")
          .append("style=\"display:inline-block;padding:14px 28px;border-radius:9999px;")
          .append("background:#2FA8A5;color:#22313A;font-weight:600;text-decoration:none;")
          .append("border:2px solid #22313A\">").append(escapar(rotulo)).append("</a></p>")
          .append("<p style=\"margin:0;font-size:13px;word-break:break-all\">")
          .append(escapar(link)).append("</p>");
        return sb.toString();
    }

    private JsonNode ler(String conteudo) {
        try {
            return json.readTree(conteudo == null ? "{}" : conteudo);
        } catch (Exception e) {
            log.warn("Conteudo de notificacao ilegivel; usando o texto padrao", e);
            return json.createObjectNode();
        }
    }

    private String texto(JsonNode c, String campo, String padrao) {
        String v = c.path(campo).asText(null);
        return v == null || v.isBlank() ? padrao : v;
    }

    private String data(String iso) {
        if (iso == null || iso.isBlank()) {
            return "agora há pouco";
        }
        try {
            return QUANDO.format(Instant.parse(iso).atZone(FUSO));
        } catch (Exception e) {
            return "agora há pouco";
        }
    }

    /** "1 hora", "30 minutos", "2 dias" — o numero cru pede conta de cabeca. */
    private String duracao(long minutos) {
        if (minutos % 1440 == 0) {
            long d = minutos / 1440;
            return d + (d == 1 ? " dia" : " dias");
        }
        if (minutos % 60 == 0) {
            long h = minutos / 60;
            return h + (h == 1 ? " hora" : " horas");
        }
        return minutos + " minutos";
    }

    private String primeiroNome(String nome) {
        String n = nome == null ? "" : nome.trim();
        int espaco = n.indexOf(' ');
        return espaco > 0 ? n.substring(0, espaco) : n;
    }

    /** O nome do pet vem do usuario e vai parar dentro de HTML. */
    private String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
