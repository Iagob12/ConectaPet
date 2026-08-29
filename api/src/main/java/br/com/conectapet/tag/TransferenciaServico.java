package br.com.conectapet.tag;

import br.com.conectapet.auditoria.AuditoriaServico;
import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.GeradorCodigo;
import br.com.conectapet.comum.util.Hashes;
import br.com.conectapet.notificacao.Notificacao;
import br.com.conectapet.notificacao.NotificacaoServico;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetRepositorio;
import br.com.conectapet.pet.PetServico;
import br.com.conectapet.pet.PetSaudeRepositorio;
import br.com.conectapet.pet.VisibilidadeRepositorio;
import br.com.conectapet.pet.ContatoRepositorio;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Dois fluxos opostos que a especificacao original chamava pelo mesmo nome.
 *
 *  TRANSFERIR TITULARIDADE — a tag muda de dono. Ela e DESVINCULADA do pet e do
 *  tutor anterior, e o registro do pet NAO e apagado: o dado do tutor A morre
 *  com a conta dele, nao com a venda de um chaveiro. O novo dono recebe uma tag
 *  em branco.
 *
 *  MIGRAR PERFIL — mesmo tutor, tag nova. E o que o FAQ do site promete: "voce
 *  compra uma nova e transfere o perfil do seu pet para ela, sem preencher tudo
 *  de novo". Como pets 1:N tags, e uma troca de ponteiro; nada e perdido.
 */
@Service
public class TransferenciaServico {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaServico.class);

    private final TagRepositorio tags;
    private final CodigoTransferenciaRepositorio codigos;
    private final PetRepositorio pets;
    private final PetServico petServico;
    private final UsuarioRepositorio usuarios;
    private final AuditoriaServico auditoria;
    private final NotificacaoServico notificacoes;
    private final GeradorCodigo gerador;
    private final Duration validade;

    public TransferenciaServico(TagRepositorio tags, CodigoTransferenciaRepositorio codigos,
                                PetRepositorio pets, PetServico petServico, UsuarioRepositorio usuarios,
                                AuditoriaServico auditoria, NotificacaoServico notificacoes,
                                GeradorCodigo gerador,
                                @Value("${conectapet.transferencia.validade}") Duration validade) {
        this.tags = tags;
        this.codigos = codigos;
        this.pets = pets;
        this.petServico = petServico;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificacoes = notificacoes;
        this.gerador = gerador;
        this.validade = validade;
    }

    // ---- Transferir titularidade -------------------------------------------

    /**
     * Gera o codigo que o dono atual entrega ao novo dono.
     *
     * Exige e-mail verificado: entregar a titularidade e irreversivel do lado de
     * quem entrega, e o codigo de ativacao original ja foi consumido — nao ha
     * segunda prova de posse para desfazer um engano.
     */
    @Transactional
    public String gerar(UUID tagUuid, UsuarioAutenticado dono, String ipHash) {
        Tag tag = minhaTag(tagUuid, dono);

        if (tag.getStatus() == StatusTag.DESATIVADA) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Esta tag esta desativada e nao pode ser transferida.");
        }

        // Um codigo ativo por vez: gerar de novo invalida o anterior, senao dois
        // codigos em circulacao disputariam a mesma tag.
        codigos.cancelarPendentesDaTag(tag.getId(), Instant.now());

        String claro = gerador.codigoAtivacao();
        CodigoTransferencia c = new CodigoTransferencia();
        c.setTagId(tag.getId());
        c.setCodigoHash(Hashes.sha256(claro));
        c.setCriadoPor(dono.id());
        c.setExpiraEm(Instant.now().plus(validade));
        codigos.save(c);

        tag.transitarPara(StatusTag.EM_TRANSFERENCIA);
        tags.save(tag);

        auditoria.registrar(dono.uuid(), AuditoriaServico.ACAO_TRANSFERENCIA_GERADA,
                "TAG", tag.getUuid(), Map.of("validadeMinutos", validade.toMinutes()), ipHash);

        usuarios.findById(dono.id()).ifPresent(u -> notificacoes.enfileirar(
                Notificacao.Tipo.TRANSFERENCIA_SOLICITADA, u.getEmail(),
                // O codigo NAO vai no e-mail: ele e um portador, e e-mail e o
                // canal mais provavel de estar comprometido.
                Map.of("tagUuid", tag.getUuid().toString(), "validadeMinutos", validade.toMinutes())));

        return claro;
    }

    @Transactional
    public void cancelar(UUID tagUuid, UsuarioAutenticado dono, String ipHash) {
        Tag tag = minhaTag(tagUuid, dono);

        if (codigos.cancelarPendentesDaTag(tag.getId(), Instant.now()) == 0) {
            throw new ProblemaException(TipoErro.NAO_ENCONTRADO,
                    "Nao ha transferencia pendente para esta tag.");
        }
        // Volta ao estado coerente com o que a tag tem: com perfil, ATIVA; sem, REIVINDICADA.
        tag.transitarPara(tag.getPetId() == null ? StatusTag.REIVINDICADA : StatusTag.ATIVA);
        tags.save(tag);

        auditoria.registrar(dono.uuid(), AuditoriaServico.ACAO_TRANSFERENCIA_CANCELADA,
                "TAG", tag.getUuid(), null, ipHash);
    }

    /**
     * O novo dono consome o codigo.
     *
     * O consumo e atomico: duas pessoas com o mesmo codigo chamando ao mesmo
     * tempo, so uma passa. Ler-depois-gravar deixaria uma janela em que as duas
     * passariam e a tag trocaria de dono duas vezes.
     */
    @Transactional
    public Tag aceitar(String codigoBruto, UsuarioAutenticado novoDono, String ipHash) {
        String codigo = GeradorCodigo.normalizar(codigoBruto);
        if (!GeradorCodigo.formaValida(codigo, GeradorCodigo.TAMANHO_ATIVACAO)) {
            throw new ProblemaException(TipoErro.CODIGO_INVALIDO);
        }

        CodigoTransferencia c = codigos.findByCodigoHash(Hashes.sha256(codigo))
                .orElseThrow(() -> new ProblemaException(TipoErro.CODIGO_INVALIDO));

        if (codigos.consumir(c.getId(), Instant.now()) == 0) {
            throw new ProblemaException(TipoErro.TOKEN_EXPIRADO,
                    "Este codigo de transferencia expirou ou ja foi usado.");
        }

        Tag tag = tags.findById(c.getTagId())
                .orElseThrow(() -> new ProblemaException(TipoErro.CODIGO_INVALIDO));

        if (c.getCriadoPor().equals(novoDono.id())) {
            throw new ProblemaException(TipoErro.DADOS_INVALIDOS,
                    "Este codigo foi gerado por voce. Entregue-o a quem vai receber a tag.");
        }

        Long donoAnterior = tag.getUsuarioId();
        UUID petAnterior = tag.getPetId() == null ? null
                : pets.findById(tag.getPetId()).map(Pet::getUuid).orElse(null);

        // A tag e desvinculada do pet e do dono anterior. O pet permanece:
        // ele pode ter outras tags, e apagar o registro destruiria dado de
        // alguem que so vendeu um chaveiro.
        tag.setPetId(null);
        tag.setModoPerdido(false);
        tag.setUsuarioId(novoDono.id());
        tag.setReivindicadaEm(Instant.now());
        tag.transitarPara(StatusTag.REIVINDICADA);
        tags.save(tag);

        auditoria.registrar(novoDono.uuid(), AuditoriaServico.ACAO_TRANSFERENCIA_ACEITA,
                "TAG", tag.getUuid(),
                Map.of("petDesvinculado", petAnterior == null ? "nenhum" : petAnterior.toString()), ipHash);

        log.info("Titularidade transferida. tagUuid={} de usuarioId={} para usuarioUuid={}",
                tag.getUuid(), donoAnterior, novoDono.uuid());
        return tag;
    }

    // ---- Migrar perfil ------------------------------------------------------

    /**
     * Mesmo tutor, tag nova. Troca de ponteiro: a tag de destino passa a apontar
     * para o pet informado, com todos os dados preservados.
     *
     * Com desativarTagAnterior, a tag antiga vai para DESATIVADA na mesma
     * transacao — e o caso da tag perdida ou quebrada, que nao pode continuar
     * respondendo pelo pet.
     */
    @Transactional
    public Tag migrarPerfil(UUID tagDestinoUuid, UUID petUuid, boolean desativarAnterior,
                            UsuarioAutenticado dono, String ipHash) {
        Tag destino = minhaTag(tagDestinoUuid, dono);

        if (destino.getStatus() == StatusTag.DESATIVADA || destino.getStatus() == StatusTag.EM_TRANSFERENCIA) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Esta tag nao esta em estado de receber um perfil.");
        }

        Pet pet = pets.findByUuidAndExcluidoEmIsNull(petUuid)
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_E_DONO));
        if (!pet.pertenceA(dono.id())) {
            throw new ProblemaException(TipoErro.NAO_E_DONO);
        }

        if (desativarAnterior) {
            for (Tag antiga : tags.findByPetId(pet.getId())) {
                if (!antiga.getId().equals(destino.getId())
                        && antiga.getStatus() != StatusTag.DESATIVADA) {
                    antiga.setPetId(null);
                    antiga.setModoPerdido(false);
                    antiga.transitarPara(StatusTag.DESATIVADA);
                    tags.save(antiga);
                }
            }
        }

        destino.setPetId(pet.getId());
        tags.save(destino);

        // Vincular o pet nao basta: sem reavaliar, a tag fica presa em
        // REIVINDICADA e a pagina publica responde "nao ativada" com o perfil
        // inteiro preenchido do outro lado.
        petServico.reavaliarTags(pet, dono);

        auditoria.registrar(dono.uuid(), AuditoriaServico.ACAO_PERFIL_MIGRADO,
                "TAG", destino.getUuid(),
                Map.of("petUuid", pet.getUuid().toString(), "desativouAnterior", desativarAnterior), ipHash);

        return tags.findByUuid(tagDestinoUuid).orElseThrow();
    }

    // ---- Apoio --------------------------------------------------------------

    /** 403 tambem quando a tag nao existe: a diferenca 403/404 permite enumerar. */
    private Tag minhaTag(UUID uuid, UsuarioAutenticado u) {
        Tag tag = tags.findByUuid(uuid).orElseThrow(() -> new ProblemaException(TipoErro.NAO_E_DONO));
        if (!tag.pertenceA(u.id())) {
            throw new ProblemaException(TipoErro.NAO_E_DONO);
        }
        return tag;
    }
}
