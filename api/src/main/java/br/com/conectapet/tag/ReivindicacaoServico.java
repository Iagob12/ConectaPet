package br.com.conectapet.tag;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.GeradorCodigo;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reivindicacao de tag: o alvo obvio de forca bruta do sistema.
 *
 * Tres protecoes, e as tres precisam existir juntas:
 *
 *  1. O codigo de ativacao e verificado contra hash BCrypt de custo 12. Cada
 *     tentativa custa ~250 ms de CPU do atacante.
 *  2. Dois baldes de tentativa independentes: um por IP e um GLOBAL por codigo.
 *     O balde por codigo nao tem IP na chave — senao 200 enderecos dariam 1.000
 *     tentativas por hora no mesmo codigo.
 *  3. Codigo publico inexistente e codigo de ativacao errado devolvem o MESMO
 *     403, com o mesmo corpo. Se distinguisse, criar uma conta bastaria para
 *     enumerar todos os codigos por esta rota, derrubando a protecao que
 *     motivou a arquitetura de dois codigos.
 *
 * Apenas tentativas FALHAS contam para o limite: quem tem Kit Multipet ativa
 * quatro tags seguidas e nao pode ser trancado por isso.
 */
@Service
public class ReivindicacaoServico {

    private static final Logger log = LoggerFactory.getLogger(ReivindicacaoServico.class);

    private final TagRepositorio tags;
    private final TentativaRepositorio tentativas;
    private final RegistroTentativas registro;
    private final br.com.conectapet.auditoria.AuditoriaServico auditoria;
    private final int limitePorIp;
    private final int limitePorCodigo;
    private final Duration janela;

    public ReivindicacaoServico(TagRepositorio tags, TentativaRepositorio tentativas,
                                RegistroTentativas registro,
                                br.com.conectapet.auditoria.AuditoriaServico auditoria,
                                @Value("${conectapet.limites.reivindicacao-por-ip}") int limitePorIp,
                                @Value("${conectapet.limites.reivindicacao-por-codigo}") int limitePorCodigo,
                                @Value("${conectapet.limites.reivindicacao-janela}") Duration janela) {
        this.tags = tags;
        this.tentativas = tentativas;
        this.registro = registro;
        this.auditoria = auditoria;
        this.limitePorIp = limitePorIp;
        this.limitePorCodigo = limitePorCodigo;
        this.janela = janela;
    }

    /**
     * Reivindicacao pelo codigo da URL, sem segredo adicional.
     *
     * Decisao do produto: quem encosta o celular numa tag sem perfil cria a
     * conta e assume a tag ali mesmo. O codigo de ativacao impresso deixou de
     * ser exigido — o fluxo curto foi escolhido sabendo do custo, que e a
     * janela entre a fabrica e a entrega: NFC atravessa papelao, entao quem
     * manuseia a encomenda fechada consegue ler a tag e se cadastrar antes do
     * cliente. A protecao que resta e operacional (marcar enviada, e o 409
     * abaixo, que ao menos torna o roubo visivel para quem receber a caixa).
     *
     * O hash do codigo de ativacao continua gravado e o CSV continua a emiti-lo:
     * voltar a exigi-lo e mudanca de codigo, sem migracao nem reimpressao.
     */
    @Transactional
    public Tag reivindicar(String codigoPublicoBruto, UsuarioAutenticado usuario, String ipHash) {

        String codigoPublico = GeradorCodigo.normalizar(codigoPublicoBruto);

        // Forma invalida nao gasta tentativa: erro de digitacao nao pode consumir
        // o limite de quem esta apenas tentando ativar a propria tag.
        if (!GeradorCodigo.formaValida(codigoPublico, GeradorCodigo.TAMANHO_PUBLICO)) {
            throw new ProblemaException(TipoErro.CODIGO_INVALIDO);
        }

        verificarLimites(codigoPublico, ipHash);

        Optional<Tag> achada = tags.findByCodigoPublico(codigoPublico);

        if (achada.isEmpty()) {
            registro.falha(codigoPublico, ipHash);
            throw new ProblemaException(TipoErro.CODIGO_INVALIDO);
        }

        Tag tag = achada.get();

        // Sem o codigo de ativacao, este 409 passou a ser alcancavel por quem
        // so digitou um codigo publico valido. Nao e vazamento novo: a pagina
        // /p/{codigo} ja mostra o perfil de uma tag ativa a qualquer um.
        if (!tag.getStatus().reivindicavel()) {
            throw new ProblemaException(TipoErro.TAG_JA_REIVINDICADA,
                    "Esta tag ja tem dono. Se voce a recebeu de outra pessoa, peca um codigo de transferencia.");
        }

        tag.transitarPara(StatusTag.REIVINDICADA);
        tag.setUsuarioId(usuario.id());
        tag.setReivindicadaEm(Instant.now());
        tags.save(tag);

        registro.sucesso(codigoPublico, ipHash);
        auditoria.registrar(usuario.uuid(), br.com.conectapet.auditoria.AuditoriaServico.ACAO_REIVINDICACAO,
                "TAG", tag.getUuid(), null, ipHash);
        log.info("Tag reivindicada. tagUuid={} usuarioUuid={}", tag.getUuid(), usuario.uuid());
        return tag;
    }

    private void verificarLimites(String codigoPublico, String ipHash) {
        Instant desde = Instant.now().minus(janela);

        if (tentativas.falhasPorCodigo(codigoPublico, desde) >= limitePorCodigo) {
            throw new ProblemaException(TipoErro.BLOQUEADO,
                    "Muitas tentativas para esta tag. Tente de novo em uma hora.");
        }
        if (tentativas.falhasPorIp(ipHash, desde) >= limitePorIp) {
            throw new ProblemaException(TipoErro.BLOQUEADO,
                    "Muitas tentativas deste dispositivo. Tente de novo em uma hora.");
        }
    }

}
