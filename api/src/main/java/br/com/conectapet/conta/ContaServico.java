package br.com.conectapet.conta;

import br.com.conectapet.autenticacao.AutenticacaoServico;
import br.com.conectapet.comum.util.Telefone;
import br.com.conectapet.foto.FotoServico;
import br.com.conectapet.leitura.Leitura;
import br.com.conectapet.leitura.LeituraRepositorio;
import br.com.conectapet.pet.*;
import br.com.conectapet.tag.StatusTag;
import br.com.conectapet.tag.Tag;
import br.com.conectapet.tag.TagRepositorio;
import br.com.conectapet.usuario.Usuario;
import br.com.conectapet.usuario.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportacao e encerramento da conta.
 *
 * O encerramento anonimiza em vez de apagar linha. A escolha ja estava no
 * desenho do banco — `anonimizado_em` existe desde a primeira migracao, e a
 * auditoria grava o ator como UUID justamente para sobreviver a isso sem virar
 * dado pessoal. Apagar a linha do usuario levaria junto o rastro de quem
 * reivindicou e transferiu cada tag, que e o que permite responder mais tarde
 * "de quem era esta tag quando ela mudou de dono".
 *
 * O que some e o que identifica a pessoa: nome, e-mail, telefones, fotos e os
 * perfis dos pets. O que fica e um esqueleto sem nome, ligado a um UUID.
 */
@Service
public class ContaServico {

    private static final Logger log = LoggerFactory.getLogger(ContaServico.class);

    private final UsuarioRepositorio usuarios;
    private final PetRepositorio pets;
    private final PetSaudeRepositorio saudes;
    private final ContatoRepositorio contatos;
    private final VisibilidadeRepositorio visibilidades;
    private final TagRepositorio tags;
    private final LeituraRepositorio leituras;
    private final FotoServico fotos;
    private final AutenticacaoServico autenticacao;

    public ContaServico(UsuarioRepositorio usuarios, PetRepositorio pets, PetSaudeRepositorio saudes,
                        ContatoRepositorio contatos, VisibilidadeRepositorio visibilidades,
                        TagRepositorio tags, LeituraRepositorio leituras, FotoServico fotos,
                        AutenticacaoServico autenticacao) {
        this.usuarios = usuarios;
        this.pets = pets;
        this.saudes = saudes;
        this.contatos = contatos;
        this.visibilidades = visibilidades;
        this.tags = tags;
        this.leituras = leituras;
        this.fotos = fotos;
        this.autenticacao = autenticacao;
    }

    /**
     * Tudo o que a conta guarda, num JSON so.
     *
     * Inclui as leituras das tags, que sao o dado que a pessoa nao consegue
     * reconstruir sozinha em lugar nenhum. A foto sai como referencia, nao
     * embutida: um export com varias imagens em base64 vira um arquivo que o
     * navegador nao abre.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();

        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("geradoEm", Instant.now().toString());
        raiz.put("conta", Map.of(
                "uuid", u.getUuid().toString(),
                "email", nn(u.getEmail()),
                "nome", nn(u.getNome()),
                "telefonePrincipal", nn(Telefone.paraExibicao(u.getTelefonePrincipal())),
                "telefoneSecundario", nn(Telefone.paraExibicao(u.getTelefoneSecundario())),
                "whatsapp", nn(Telefone.paraExibicao(u.getWhatsapp())),
                "emailVerificado", u.emailVerificado(),
                "criadoEm", String.valueOf(u.getCriadoEm())));

        List<Map<String, Object>> listaPets = new ArrayList<>();
        for (Pet p : pets.findByUsuarioIdAndExcluidoEmIsNullOrderByCriadoEmDesc(usuarioId)) {
            Map<String, Object> mp = new LinkedHashMap<>();
            mp.put("uuid", p.getUuid().toString());
            mp.put("nome", nn(p.getNome()));
            mp.put("especie", String.valueOf(p.getEspecie()));
            mp.put("raca", nn(p.getRaca()));
            mp.put("cor", nn(p.getCor()));
            mp.put("dataNascimento", String.valueOf(p.getDataNascimento()));
            mp.put("microchip", nn(p.getNumeroMicrochip()));
            mp.put("cidade", nn(p.getCidade()));
            mp.put("estado", nn(p.getEstado()));
            mp.put("observacoes", nn(p.getObservacoes()));
            mp.put("temFoto", p.getFotoChave() != null);

            saudes.findById(p.getId()).ifPresent(s -> mp.put("saude", Map.of(
                    "alergias", nn(s.getAlergias()),
                    "medicacaoContinua", nn(s.getMedicacaoContinua()),
                    "condicoes", nn(s.getCondicoes()),
                    "cuidadosEspeciais", nn(s.getCuidadosEspeciais()),
                    "veterinario", nn(s.getVeterinarioNome()),
                    "veterinarioTelefone", nn(Telefone.paraExibicao(s.getVeterinarioTelefone())),
                    "clinica", nn(s.getClinica()))));

            visibilidades.findById(p.getId()).ifPresent(v -> mp.put("visibilidade", Map.of(
                    "telefone", v.isMostrarTelefone(),
                    "whatsapp", v.isMostrarWhatsapp(),
                    "contatosEmergencia", v.isMostrarContatosEmergencia(),
                    "saude", v.isMostrarSaude(),
                    "cidade", v.isMostrarCidade(),
                    "microchip", v.isMostrarMicrochip(),
                    "recado", nn(v.getMensagemPersonalizada()))));

            mp.put("contatosEmergencia", contatos.findByPetIdOrderByOrdemAscIdAsc(p.getId()).stream()
                    .map(c -> Map.of(
                            "nome", nn(c.getNome()),
                            "telefone", nn(Telefone.paraExibicao(c.getTelefone())),
                            "parentesco", nn(c.getParentesco())))
                    .toList());

            mp.put("leituras", leituras
                    .findByPetIdOrderByOcorridaEmDesc(p.getId(), PageRequest.of(0, 500))
                    .getContent().stream()
                    .map(this::leituraExportada)
                    .toList());

            listaPets.add(mp);
        }
        raiz.put("pets", listaPets);

        raiz.put("tags", tags.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .map(t -> Map.of(
                        "codigoPublico", t.getCodigoPublico(),
                        "modelo", String.valueOf(t.getModelo()),
                        "status", String.valueOf(t.getStatus()),
                        "ativadaEm", String.valueOf(t.getReivindicadaEm())))
                .toList());

        return raiz;
    }

    /**
     * Encerra a conta.
     *
     * As tags sao desativadas junto, e nao apenas desvinculadas: uma tag que
     * continuasse viva depois disso ficaria na coleira apontando para um perfil
     * que nao existe mais, e quem encontrasse o pet leria uma pagina vazia sem
     * entender por que. Desativada, ela ao menos diz claramente que nao ha nada
     * ali.
     */
    @Transactional
    public void encerrar(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        Instant agora = Instant.now();

        for (Pet p : pets.findByUsuarioIdAndExcluidoEmIsNullOrderByCriadoEmDesc(usuarioId)) {
            // A foto sai do armazenamento de verdade: marcar a linha como
            // excluida deixaria o arquivo no disco, e ele e dado pessoal.
            fotos.remover(p);
            contatos.deleteAll(contatos.findByPetIdOrderByOrdemAscIdAsc(p.getId()));
            saudes.findById(p.getId()).ifPresent(saudes::delete);
            p.setNome("Pet removido");
            p.setRaca(null);
            p.setCor(null);
            p.setNumeroMicrochip(null);
            p.setObservacoes(null);
            p.setCidade(null);
            p.setEstado(null);
            p.setExcluidoEm(agora);
            pets.save(p);
        }

        for (Tag t : tags.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)) {
            t.setPetId(null);
            t.setModoPerdido(false);
            if (t.getStatus() != StatusTag.DESATIVADA) {
                t.transitarPara(StatusTag.DESATIVADA);
                t.setDesativadaEm(agora);
            }
            tags.save(t);
        }

        // O e-mail precisa continuar unico: a coluna tem indice unico, e dois
        // encerramentos com o mesmo texto quebrariam a insercao.
        u.setEmail("removido+" + u.getUuid() + "@conectapet.invalid");
        u.setNome("Conta removida");
        u.setTelefonePrincipal(null);
        u.setTelefoneSecundario(null);
        u.setWhatsapp(null);
        u.setEmailVerificadoEm(null);
        u.setAtivo(false);
        u.setAnonimizadoEm(agora);
        u.setExcluidoEm(agora);
        usuarios.save(u);

        autenticacao.revogarTudo(usuarioId);
        log.info("Conta encerrada e anonimizada. uuid={}", u.getUuid());
    }

    private Map<String, Object> leituraExportada(Leitura l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quando", String.valueOf(l.getOcorridaEm()));
        m.put("origem", String.valueOf(l.getOrigem()));
        m.put("cidade", nn(l.getCidade()));
        m.put("regiao", nn(l.getRegiao()));
        m.put("mensagem", nn(l.getMensagemDeQuemEncontrou()));
        return m;
    }

    /** Jackson aceita null, mas Map.of nao: troca por vazio para simplificar. */
    private String nn(String s) {
        return s == null ? "" : s;
    }
}
