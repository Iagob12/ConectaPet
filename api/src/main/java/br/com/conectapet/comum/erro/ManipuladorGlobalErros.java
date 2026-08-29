package br.com.conectapet.comum.erro;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Toda resposta de erro sai em RFC 7807 (application/problem+json).
 *
 * A mensagem que chega ao usuario e sempre a do catalogo, em portugues. O detalhe
 * tecnico e a stack ficam no log — nunca no corpo, para nao entregar a estrutura
 * interna a quem esta sondando a API.
 */
@RestControllerAdvice
public class ManipuladorGlobalErros {

    private static final Logger log = LoggerFactory.getLogger(ManipuladorGlobalErros.class);
    private static final String BASE_TIPO = "https://api.conectapet.com.br/erros/";

    @ExceptionHandler(ProblemaException.class)
    public ResponseEntity<ProblemDetail> problema(ProblemaException e, HttpServletRequest req) {
        ProblemDetail pd = montar(e.tipo(), e.detalhe(), req);
        if (e.tipo().status().is5xxServerError()) {
            log.error("Erro em {}: {}", req.getRequestURI(), e.detalhe(), e);
        }
        return ResponseEntity.status(e.tipo().status()).body(pd);
    }

    /** Erro de Bean Validation: devolve campo a campo, para a interface exibir no campo certo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        ProblemDetail pd = montar(TipoErro.DADOS_INVALIDOS, null, req);
        List<Map<String, String>> erros = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of(
                        "campo", f.getField(),
                        "mensagem", f.getDefaultMessage() == null ? "Valor invalido" : f.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", erros);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> arquivoGrande(HttpServletRequest req) {
        return ResponseEntity.status(TipoErro.ARQUIVO_GRANDE.status())
                .body(montar(TipoErro.ARQUIVO_GRANDE, null, req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> acessoNegado(HttpServletRequest req) {
        return ResponseEntity.status(TipoErro.SEM_PERMISSAO.status())
                .body(montar(TipoErro.SEM_PERMISSAO, null, req));
    }

    /**
     * Rota inexistente e 404, nao 500.
     *
     * Sem este tratamento a rede de seguranca abaixo engole a excecao do proprio
     * Spring e transforma um endereco digitado errado em erro de servidor — o que
     * enche o monitoramento de alarme falso e esconde erro de verdade.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> naoEncontrado(HttpServletRequest req) {
        return ResponseEntity.status(TipoErro.NAO_ENCONTRADO.status())
                .body(montar(TipoErro.NAO_ENCONTRADO, null, req));
    }

    /**
     * Metodo HTTP errado na rota certa tambem nao e erro de servidor.
     *
     * O corpo precisa dizer 405 igual ao cabecalho: reaproveitar o problema de
     * "nao encontrado" fazia a resposta se contradizer, e quem depura o cliente
     * via 405 no header e 404 no JSON.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> metodoInvalido(HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(405);
        pd.setType(URI.create(BASE_TIPO + "metodo-nao-permitido"));
        pd.setTitle("Metodo nao permitido");
        pd.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(405).body(pd);
    }

    /**
     * Rede de seguranca. Qualquer excecao nao prevista vira 500 generico:
     * a mensagem original pode conter nome de tabela, SQL ou dado pessoal.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception e, HttpServletRequest req) {
        log.error("Erro nao tratado em {}", req.getRequestURI(), e);
        return ResponseEntity.status(TipoErro.ERRO_INTERNO.status())
                .body(montar(TipoErro.ERRO_INTERNO, null, req));
    }

    private ProblemDetail montar(TipoErro tipo, String detalhe, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(tipo.status());
        pd.setType(URI.create(BASE_TIPO + tipo.slug()));
        pd.setTitle(tipo.titulo());
        if (detalhe != null) {
            pd.setDetail(detalhe);
        }
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }
}
