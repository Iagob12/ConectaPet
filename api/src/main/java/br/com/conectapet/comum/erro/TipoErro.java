package br.com.conectapet.comum.erro;

import org.springframework.http.HttpStatus;

/**
 * Catalogo de erros. O titulo e a mensagem sao o que o usuario le, em portugues;
 * o detalhe tecnico fica so no log.
 */
public enum TipoErro {

    DADOS_INVALIDOS(HttpStatus.BAD_REQUEST, "dados-invalidos", "Dados invalidos"),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "nao-autenticado", "Sessao expirada"),
    CREDENCIAIS_INVALIDAS(HttpStatus.UNAUTHORIZED, "credenciais-invalidas", "E-mail ou senha incorretos"),
    NAO_E_DONO(HttpStatus.FORBIDDEN, "nao-e-dono", "Este item nao pertence a voce"),
    SEM_PERMISSAO(HttpStatus.FORBIDDEN, "sem-permissao", "Voce nao tem permissao para isso"),
    EMAIL_NAO_VERIFICADO(HttpStatus.FORBIDDEN, "email-nao-verificado", "Confirme seu e-mail antes de continuar"),

    /**
     * Usado tanto para codigo de ativacao errado quanto para codigo publico
     * inexistente. Corpo identico nos dois casos: se a API distinguisse, criar
     * uma conta bastaria para enumerar todos os codigos por esta rota.
     */
    CODIGO_INVALIDO(HttpStatus.FORBIDDEN, "codigo-invalido", "Codigo de ativacao invalido"),

    NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "nao-encontrado", "Nao encontrado"),
    EMAIL_EM_USO(HttpStatus.CONFLICT, "email-em-uso", "Este e-mail ja esta cadastrado"),
    TAG_JA_REIVINDICADA(HttpStatus.CONFLICT, "tag-ja-reivindicada", "Esta tag ja tem dono"),
    ESTADO_INVALIDO(HttpStatus.CONFLICT, "estado-invalido", "Esta operacao nao e permitida no estado atual"),
    LIMITE_PLANO(HttpStatus.CONFLICT, "limite-plano", "Limite do seu plano atingido"),
    TOKEN_EXPIRADO(HttpStatus.GONE, "token-expirado", "Este link expirou ou ja foi usado"),
    ARQUIVO_GRANDE(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo-grande", "Arquivo acima de 5 MB"),
    TIPO_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "tipo-nao-suportado", "Envie uma imagem JPEG, PNG ou WebP"),
    BLOQUEADO(HttpStatus.LOCKED, "bloqueado", "Muitas tentativas. Tente de novo mais tarde"),
    LIMITE_EXCEDIDO(HttpStatus.TOO_MANY_REQUESTS, "limite-excedido", "Muitas requisicoes. Aguarde um instante"),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Algo deu errado do nosso lado");

    private final HttpStatus status;
    private final String slug;
    private final String titulo;

    TipoErro(HttpStatus status, String slug, String titulo) {
        this.status = status;
        this.slug = slug;
        this.titulo = titulo;
    }

    public HttpStatus status() { return status; }
    public String slug() { return slug; }
    public String titulo() { return titulo; }
}
