package br.com.conectapet.pet;

import br.com.conectapet.seguranca.UsuarioAtual;
import br.com.conectapet.seguranca.UsuarioAutenticado;
import br.com.conectapet.tag.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pets")
public class PetControlador {

    private final PetServico servico;
    private final UsuarioAtual usuarioAtual;

    public PetControlador(PetServico servico, UsuarioAtual usuarioAtual) {
        this.servico = servico;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public List<PetResposta> listar() {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return servico.meusPets(u).stream().map(p -> montar(p, u)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PetResposta criar(@Valid @RequestBody PetEntrada dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        Pet criado = servico.criar(dto.paraEntidade(), dto.tagUuid(), u);
        return montar(criado, u);
    }

    @GetMapping("/{uuid}")
    public PetResposta detalhe(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return montar(servico.meuPet(uuid, u), u);
    }

    @PutMapping("/{uuid}")
    public PetResposta atualizar(@PathVariable UUID uuid, @Valid @RequestBody PetEntrada dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return montar(servico.atualizar(uuid, dto.paraEntidade(), u), u);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID uuid) {
        servico.excluir(uuid, usuarioAtual.obrigatorio());
    }

    // ---- Saude -------------------------------------------------------------

    /**
     * Sem esta leitura a ficha so podia ser escrita, nunca relida: o painel
     * abria o formulario em branco e um novo salvamento apagava a alergia que
     * ja estava la.
     */
    @GetMapping("/{uuid}/saude")
    public SaudeDto saude(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return SaudeDto.de(servico.saude(servico.meuPet(uuid, u)));
    }

    @PutMapping("/{uuid}/saude")
    public SaudeDto salvarSaude(@PathVariable UUID uuid, @Valid @RequestBody SaudeDto dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return SaudeDto.de(servico.salvarSaude(uuid, dto.paraEntidade(), u));
    }

    // ---- Visibilidade ------------------------------------------------------

    @GetMapping("/{uuid}/visibilidade")
    public VisibilidadeDto visibilidade(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return VisibilidadeDto.de(servico.visibilidade(servico.meuPet(uuid, u)));
    }

    @PutMapping("/{uuid}/visibilidade")
    public VisibilidadeDto salvarVisibilidade(@PathVariable UUID uuid, @Valid @RequestBody VisibilidadeDto dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return VisibilidadeDto.de(servico.salvarVisibilidade(uuid, dto.paraEntidade(), u));
    }

    // ---- Contatos de emergencia --------------------------------------------

    @GetMapping("/{uuid}/contatos")
    public List<ContatoDto> contatos(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return servico.contatos(servico.meuPet(uuid, u)).stream().map(ContatoDto::de).toList();
    }

    @PostMapping("/{uuid}/contatos")
    @ResponseStatus(HttpStatus.CREATED)
    public ContatoDto adicionarContato(@PathVariable UUID uuid, @Valid @RequestBody ContatoDto dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return ContatoDto.de(servico.adicionarContato(uuid, dto.paraEntidade(), u));
    }

    @PutMapping("/{uuid}/contatos/{uuidContato}")
    public ContatoDto atualizarContato(@PathVariable UUID uuid, @PathVariable UUID uuidContato,
                                       @Valid @RequestBody ContatoDto dto) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return ContatoDto.de(servico.atualizarContato(uuid, uuidContato, dto.paraEntidade(), u));
    }

    @DeleteMapping("/{uuid}/contatos/{uuidContato}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerContato(@PathVariable UUID uuid, @PathVariable UUID uuidContato) {
        servico.removerContato(uuid, uuidContato, usuarioAtual.obrigatorio());
    }

    // ---- Modo perdido ------------------------------------------------------

    @PostMapping("/{uuid}/modo-perdido")
    public List<UUID> ligarModoPerdido(@PathVariable UUID uuid) {
        UsuarioAutenticado u = usuarioAtual.obrigatorio();
        return servico.definirModoPerdido(uuid, true, u).stream().map(Tag::getUuid).toList();
    }

    @DeleteMapping("/{uuid}/modo-perdido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desligarModoPerdido(@PathVariable UUID uuid) {
        servico.definirModoPerdido(uuid, false, usuarioAtual.obrigatorio());
    }

    // ---- Montagem ----------------------------------------------------------

    private PetResposta montar(Pet p, UsuarioAutenticado u) {
        // Uma chamada so: motivoNaoPronto le tutor e visibilidade do banco, e
        // chama-lo duas vezes dobrava as consultas de cada pet da lista.
        String falta = servico.motivoNaoPronto(p, u.id());
        return new PetResposta(
                p.getUuid(), p.getNome(), p.getEspecie().name(),
                p.getRaca(), p.getSexo() == null ? null : p.getSexo().name(),
                p.getDataNascimento(), p.getPesoKg(), p.getCor(), p.getCastrado(),
                p.getNumeroMicrochip(), p.getCidade(), p.getEstado(), p.getObservacoes(),
                p.getFotoChave() != null, falta == null, falta);
    }

    // ---- DTOs. Nenhuma entidade JPA cruza a fronteira do controlador. -------

    public record PetEntrada(
            @NotBlank @Size(min = 1, max = 60) String nome,
            @NotNull Especie especie,
            @Size(max = 60) String raca,
            Sexo sexo,
            @PastOrPresent LocalDate dataNascimento,
            @DecimalMin("0.0") @DecimalMax("200.0") BigDecimal pesoKg,
            @Size(max = 40) String cor,
            Boolean castrado,
            @Size(max = 20) String numeroMicrochip,
            @Size(max = 80) String cidade,
            @Size(min = 2, max = 2) String estado,
            @Size(max = 500) String observacoes,
            UUID tagUuid) {

        Pet paraEntidade() {
            Pet p = new Pet();
            p.setNome(nome);
            p.setEspecie(especie);
            p.setRaca(raca);
            p.setSexo(sexo);
            p.setDataNascimento(dataNascimento);
            p.setPesoKg(pesoKg);
            p.setCor(cor);
            p.setCastrado(castrado);
            p.setNumeroMicrochip(numeroMicrochip);
            p.setCidade(cidade);
            p.setEstado(estado == null ? null : estado.toUpperCase());
            p.setObservacoes(observacoes);
            return p;
        }
    }

    /** `pronto` e `oQueFalta` alimentam o "falta X para sua tag ficar ativa" do painel. */
    public record PetResposta(UUID uuid, String nome, String especie, String raca, String sexo,
                              LocalDate dataNascimento, BigDecimal pesoKg, String cor, Boolean castrado,
                              String numeroMicrochip, String cidade, String estado, String observacoes,
                              boolean temFoto, boolean pronto, String oQueFalta) {}

    public record SaudeDto(
            @Size(max = 300) String alergias,
            @Size(max = 300) String medicacaoContinua,
            @Size(max = 300) String condicoes,
            @Size(max = 300) String cuidadosEspeciais,
            @Size(max = 120) String veterinarioNome,
            String veterinarioTelefone,
            @Size(max = 120) String clinica) {

        PetSaude paraEntidade() {
            PetSaude s = new PetSaude();
            s.setAlergias(alergias);
            s.setMedicacaoContinua(medicacaoContinua);
            s.setCondicoes(condicoes);
            s.setCuidadosEspeciais(cuidadosEspeciais);
            s.setVeterinarioNome(veterinarioNome);
            s.setVeterinarioTelefone(veterinarioTelefone);
            s.setClinica(clinica);
            return s;
        }

        static SaudeDto de(PetSaude s) {
            return new SaudeDto(s.getAlergias(), s.getMedicacaoContinua(), s.getCondicoes(),
                    s.getCuidadosEspeciais(), s.getVeterinarioNome(), s.getVeterinarioTelefone(), s.getClinica());
        }
    }

    public record VisibilidadeDto(boolean mostrarTelefone, boolean mostrarWhatsapp,
                                  boolean mostrarContatosEmergencia, boolean mostrarSaude,
                                  boolean mostrarCidade, boolean mostrarMicrochip,
                                  @Size(max = 200) String mensagemPersonalizada) {

        VisibilidadePerfil paraEntidade() {
            VisibilidadePerfil v = new VisibilidadePerfil();
            v.setMostrarTelefone(mostrarTelefone);
            v.setMostrarWhatsapp(mostrarWhatsapp);
            v.setMostrarContatosEmergencia(mostrarContatosEmergencia);
            v.setMostrarSaude(mostrarSaude);
            v.setMostrarCidade(mostrarCidade);
            v.setMostrarMicrochip(mostrarMicrochip);
            v.setMensagemPersonalizada(mensagemPersonalizada);
            return v;
        }

        static VisibilidadeDto de(VisibilidadePerfil v) {
            return new VisibilidadeDto(v.isMostrarTelefone(), v.isMostrarWhatsapp(),
                    v.isMostrarContatosEmergencia(), v.isMostrarSaude(), v.isMostrarCidade(),
                    v.isMostrarMicrochip(), v.getMensagemPersonalizada());
        }
    }

    public record ContatoDto(UUID uuid,
                             @NotBlank @Size(min = 2, max = 120) String nome,
                             @NotBlank String telefone,
                             @Size(max = 40) String parentesco,
                             @Min(0) Integer ordem) {

        ContatoEmergencia paraEntidade() {
            ContatoEmergencia c = new ContatoEmergencia();
            c.setNome(nome);
            c.setTelefone(telefone);
            c.setParentesco(parentesco);
            c.setOrdem(ordem == null ? 0 : ordem);
            return c;
        }

        static ContatoDto de(ContatoEmergencia c) {
            return new ContatoDto(c.getUuid(), c.getNome(), c.getTelefone(), c.getParentesco(), c.getOrdem());
        }
    }
}
