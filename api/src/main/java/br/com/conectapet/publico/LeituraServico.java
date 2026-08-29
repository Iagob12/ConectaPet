package br.com.conectapet.publico;

import br.com.conectapet.leitura.Leitura;
import br.com.conectapet.leitura.LeituraRepositorio;
import br.com.conectapet.leitura.OrigemLeitura;
import br.com.conectapet.notificacao.Notificacao;
import br.com.conectapet.notificacao.NotificacaoServico;
import br.com.conectapet.pet.Pet;
import br.com.conectapet.pet.PetRepositorio;
import br.com.conectapet.pet.PetSaudeRepositorio;
import br.com.conectapet.pet.VisibilidadeRepositorio;
import br.com.conectapet.pet.ContatoRepositorio;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registrar leitura e notificar tutor sao duas coisas, e essa separacao e a
 * decisao mais importante deste arquivo.
 *
 * REGISTRAR acontece sempre, no servidor, ao servir o perfil — inclusive para
 * robo, marcado como tal.
 *
 * NOTIFICAR acontece so a partir da confirmacao do cliente via sendBeacon, com
 * deduplicacao por hash de IP numa janela de 10 minutos.
 *
 * Filtrar notificacao por user-agent seria uma corrida que se perde: cada rede
 * social tem o seu, eles mudam, e Telegram e LinkedIn fazem o mesmo. Com a
 * separacao, o robo de preview do WhatsApp nunca dispara "seu pet foi
 * encontrado" — e o proprio tutor deixa de receber push a cada aproximacao
 * enquanto testa a tag durante o cadastro.
 */
@Service
public class LeituraServico {

    private static final Logger log = LoggerFactory.getLogger(LeituraServico.class);

    private final LeituraRepositorio leituras;
    private final PetRepositorio pets;
    private final UsuarioRepositorio usuarios;
    private final NotificacaoServico notificacoes;
    private final Duration janelaDedup;

    public LeituraServico(LeituraRepositorio leituras, PetRepositorio pets,
                          UsuarioRepositorio usuarios, NotificacaoServico notificacoes,
                          @Value("${conectapet.leitura.janela-dedup}") Duration janelaDedup) {
        this.leituras = leituras;
        this.pets = pets;
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
        this.janelaDedup = janelaDedup;
    }

    /** Chamado ao servir o perfil. Registra e nunca notifica. */
    @Transactional
    public void registrarAcesso(Tag tag, String ipHash, String userAgent) {
        OrigemLeitura origem = DetectorRobo.ehRobo(userAgent) ? OrigemLeitura.ROBO : OrigemLeitura.SERVIDOR;
        gravar(tag, origem, ipHash, userAgent, null);
    }

    /**
     * Chamado pelo beacon do cliente. Registra e, se nao for repeticao recente,
     * enfileira a notificacao ao tutor.
     */
    @Transactional
    public void confirmarLeituraHumana(Tag tag, String ipHash, String userAgent, DadosDeQuemEncontrou dados) {
        Leitura leitura = gravar(tag, OrigemLeitura.CLIENTE, ipHash, userAgent, dados);

        boolean repeticao = leituras.notificacoesRecentes(
                tag.getId(), ipHash, Instant.now().minus(janelaDedup)) > 0;

        if (repeticao) {
            log.debug("Leitura repetida da mesma origem em {} — nao notifica", janelaDedup);
            return;
        }
        notificar(tag, leitura);
    }

    private Leitura gravar(Tag tag, OrigemLeitura origem, String ipHash,
                           String userAgent, DadosDeQuemEncontrou dados) {
        Leitura l = new Leitura();
        l.setTagId(tag.getId());
        l.setPetId(tag.getPetId());
        l.setOrigem(origem);
        l.setIpHash(ipHash);
        l.setUserAgent(truncar(userAgent, 300));

        if (dados != null) {
            // Localizacao so quando a pessoa tocou no botao e o navegador
            // concedeu permissao. Nunca pedida automaticamente ao abrir.
            if (dados.localizacaoCompartilhada() && dados.latitude() != null && dados.longitude() != null) {
                l.setLocalizacaoCompartilhada(true);
                l.setLatitude(dados.latitude());
                l.setLongitude(dados.longitude());
                l.setPrecisaoM(dados.precisaoM());
            }
            l.setMensagemDeQuemEncontrou(truncar(dados.mensagem(), 500));
            l.setTelefoneDeQuemEncontrou(dados.telefone());
        }
        return leituras.save(l);
    }

    private void notificar(Tag tag, Leitura leitura) {
        Optional<Pet> pet = tag.getPetId() == null ? Optional.empty() : pets.findById(tag.getPetId());
        if (pet.isEmpty()) {
            return;
        }
        Optional<Usuario> tutor = usuarios.findById(pet.get().getUsuarioId());
        if (tutor.isEmpty()) {
            return;
        }

        Map<String, Object> conteudo = new HashMap<>();
        conteudo.put("petNome", pet.get().getNome());
        conteudo.put("petUuid", pet.get().getUuid().toString());
        conteudo.put("ocorridaEm", leitura.getOcorridaEm().toString());
        conteudo.put("temLocalizacao", leitura.isLocalizacaoCompartilhada());
        conteudo.put("temMensagem", leitura.getMensagemDeQuemEncontrou() != null);
        // O telefone de quem encontrou NAO entra aqui: o corpo da notificacao
        // vira log e fila. Ele fica so na tabela de leituras, e o tutor o ve
        // no painel, sob autenticacao.

        notificacoes.enfileirar(Notificacao.Tipo.LEITURA_TAG, tutor.get().getEmail(), conteudo);

        leitura.setNotificadaEm(Instant.now());
        leituras.save(leitura);
    }

    private String truncar(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public record DadosDeQuemEncontrou(boolean localizacaoCompartilhada, BigDecimal latitude,
                                       BigDecimal longitude, Integer precisaoM,
                                       String mensagem, String telefone) {}
}
