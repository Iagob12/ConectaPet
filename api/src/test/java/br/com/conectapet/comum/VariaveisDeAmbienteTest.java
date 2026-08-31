package br.com.conectapet.comum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda variavel declarada no render.yaml precisa ser lida pelo application.yml.
 *
 * O Spring nao adivinha que DIAGNOSTICO_PROXY se refere a
 * conectapet.privacidade.diagnostico-proxy: sem a linha ${DIAGNOSTICO_PROXY}
 * no application.yml, ele procura por CONECTAPET_PRIVACIDADE_DIAGNOSTICOPROXY
 * e a variavel definida na hospedagem nao faz absolutamente nada.
 *
 * E o pior tipo de falha: a aplicacao sobe, nao ha erro, nao ha aviso, e a
 * configuracao simplesmente nao vale. Aconteceu com o diagnostico de proxy —
 * ficou horas ligado no painel e desligado no processo, enquanto eu procurava
 * o problema no visualizador de log.
 *
 * O teste le os dois arquivos porque a ligacao entre eles nao existe em lugar
 * nenhum do codigo: e uma convencao, e convencao sem teste quebra calada.
 */
class VariaveisDeAmbienteTest {

    @Test
    @DisplayName("nenhuma variavel do render.yaml fica sem efeito")
    void todasAsVariaveisSaoLidas() throws Exception {
        Path raiz = Path.of("..");
        String render = Files.readString(raiz.resolve("render.yaml"), StandardCharsets.UTF_8);
        String yml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("^\s+- key: ([A-Z0-9_]+)", Pattern.MULTILINE).matcher(render);
        List<String> declaradas = new ArrayList<>();
        while (m.find()) {
            declaradas.add(m.group(1));
        }

        assertThat(declaradas)
                .as("o render.yaml deveria declarar variaveis; se esvaziou, o teste perdeu o sentido")
                .isNotEmpty();

        List<String> semEfeito = declaradas.stream()
                .filter(v -> !yml.contains("${" + v))
                .toList();

        assertThat(semEfeito)
                .as("declaradas no render.yaml mas nunca lidas pelo application.yml — "
                  + "defini-las na hospedagem nao muda nada")
                .isEmpty();
    }
}
