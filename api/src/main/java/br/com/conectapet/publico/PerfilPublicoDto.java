package br.com.conectapet.publico;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que a pagina de resgate recebe.
 *
 * Campo nulo nao e serializado (jackson: non_null no application.yml), entao
 * campo oculto pelo tutor simplesmente nao aparece no JSON — nem como null,
 * nem como string vazia. Isso e verificavel no codigo-fonte da pagina, nao so
 * na tela.
 */
public record PerfilPublicoDto(
        String estado,
        boolean modoPerdido,
        PetDto pet,
        TutorDto tutor,
        SaudeDto saude,
        List<ContatoDto> contatosEmergencia,
        String mensagemPersonalizada) {

    /**
     * Resposta unica para "tag nao ativada" E para "codigo nao existe".
     *
     * Um metodo so, usado nos dois casos de proposito: se fossem dois caminhos,
     * alguem acabaria acrescentando um detalhe em um deles e reintroduzindo a
     * enumeracao que a arquitetura de dois codigos existe para impedir.
     */
    public static PerfilPublicoDto naoAtivada() {
        return new PerfilPublicoDto("NAO_ATIVADA", false, null, null, null, null, null);
    }

    public record PetDto(String nome, String especie, String raca, String sexo,
                         BigDecimal pesoKg, String cor, Boolean castrado,
                         String numeroMicrochip, String cidade, String estado,
                         String observacoes, FotoDto foto) {}

    public record FotoDto(String pequena, String media, String original) {}

    /** Nunca e-mail, endereco completo, documento nem id interno. */
    public record TutorDto(String nome,
                           String telefoneExibicao, String telefoneE164,
                           String whatsappExibicao, String whatsappE164) {}

    public record SaudeDto(String alergias, String medicacaoContinua, String condicoes,
                           String cuidadosEspeciais, String veterinarioNome,
                           String veterinarioTelefoneExibicao, String veterinarioTelefoneE164,
                           String clinica) {}

    public record ContatoDto(String nome, String parentesco,
                             String telefoneExibicao, String telefoneE164) {}
}
