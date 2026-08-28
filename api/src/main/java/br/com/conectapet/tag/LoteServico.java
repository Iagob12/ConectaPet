package br.com.conectapet.tag;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import br.com.conectapet.comum.util.GeradorCodigo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class LoteServico {

    private final LoteRepositorio lotes;
    private final TagRepositorio tags;
    private final GeradorCodigo gerador;
    private final PasswordEncoder encoder;

    public LoteServico(LoteRepositorio lotes, TagRepositorio tags,
                       GeradorCodigo gerador, PasswordEncoder encoder) {
        this.lotes = lotes;
        this.tags = tags;
        this.gerador = gerador;
        this.encoder = encoder;
    }

    /**
     * Gera N tags com codigos aleatorios e nao sequenciais.
     *
     * O codigo de ativacao fica em claro na coluna enquanto o lote esta
     * NAO_CONFIRMADO, para que ele possa ser recuperado se o download falhar.
     * A confirmacao apaga o claro e deixa so o hash.
     */
    @Transactional
    public Lote gerar(String nome, int quantidade, ModeloTag modelo, String observacoes) {
        Lote lote = new Lote();
        lote.setNome(nome);
        lote.setQuantidade(quantidade);
        lote.setModelo(modelo);
        lote.setObservacoes(observacoes);
        lotes.saveAndFlush(lote);

        List<Tag> novas = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            String publico = codigoPublicoInedito();
            String ativacao = gerador.codigoAtivacao();

            Tag t = new Tag();
            t.setCodigoPublico(publico);
            t.setCodigoAtivacaoClaro(ativacao);
            t.setCodigoAtivacaoHash(encoder.encode(ativacao));
            t.setLoteId(lote.getId());
            t.setModelo(modelo);
            t.setStatus(StatusTag.CRIADA);
            novas.add(t);
        }
        tags.saveAll(novas);
        return lote;
    }

    /** Colisao e improvavel com ~49 bits, mas o unique do banco nao perdoa. */
    private String codigoPublicoInedito() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            String c = gerador.codigoPublico();
            if (!tags.existsByCodigoPublico(c)) {
                return c;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar codigo publico inedito");
    }

    /** Irreversivel: depois disso o codigo de ativacao so existe como hash. */
    @Transactional
    public void confirmar(Long loteId) {
        Lote lote = lotes.findById(loteId)
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));
        if (lote.getStatus() == StatusLote.CONFIRMADO) {
            return;
        }
        List<Tag> doLote = tags.findByLoteId(loteId);
        doLote.forEach(t -> t.setCodigoAtivacaoClaro(null));
        tags.saveAll(doLote);

        lote.setStatus(StatusLote.CONFIRMADO);
        lote.setConfirmadoEm(Instant.now());
        lotes.save(lote);
    }

    @Transactional(readOnly = true)
    public String csv(Long loteId, String urlBase) {
        Lote lote = lotes.findById(loteId)
                .orElseThrow(() -> new ProblemaException(TipoErro.NAO_ENCONTRADO));
        if (lote.getStatus() == StatusLote.CONFIRMADO) {
            throw new ProblemaException(TipoErro.ESTADO_INVALIDO,
                    "Este lote ja foi confirmado. Os codigos de ativacao nao existem mais em claro.");
        }
        StringBuilder sb = new StringBuilder("codigo_publico,codigo_ativacao,url\n");
        for (Tag t : tags.findByLoteId(loteId)) {
            sb.append(t.getCodigoPublico()).append(',')
              .append(t.getCodigoAtivacaoClaro()).append(',')
              .append(urlBase).append(t.getCodigoPublico()).append('\n');
        }
        return sb.toString();
    }
}
