package br.com.conectapet.autenticacao;

import br.com.conectapet.comum.erro.ProblemaException;
import br.com.conectapet.comum.erro.TipoErro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio na adivinhacao de senha.
 *
 * A reivindicacao de tag tinha limite desde o inicio; o login nao tinha
 * nenhum. Um script podia tentar senha atras de senha, sem teto, contra
 * qualquer e-mail — a porta melhor protegida do sistema era a dos fundos.
 *
 * Dois baldes, pelo mesmo raciocinio da reivindicacao:
 *
 * - Por CONTA, para conter quem escolheu uma vitima e vai insistir nela. E o
 *   ataque que importa, porque senha fraca de uma pessoa especifica e o que
 *   realmente cai.
 * - Por IP, mais largo, para conter quem espalha uma senha comum por muitas
 *   contas. Largo de proposito: operadora movel brasileira compartilha um
 *   endereco de saida entre muitos assinantes, e apertar aqui trancaria gente
 *   inocente num shopping.
 *
 * So conta tentativa FALHA, e acertar limpa o balde da conta. Quem erra a
 * senha tres vezes, lembra e entra nao pode sair penalizado.
 *
 * Em memoria, seguindo o que ja vale para os limites de minuto neste projeto:
 * perder o contador num restart e aceitavel porque quem ataca nao consegue
 * provocar restart. O limite que nao podia viver em memoria — o de
 * reivindicacao, de 5 por hora — continua no banco.
 */
@Component
public class LimiteTentativasLogin {

    private static final Logger log = LoggerFactory.getLogger(LimiteTentativasLogin.class);

    private record Contador(int falhas, Instant expiraEm) {}

    private final Map<String, Contador> porConta = new ConcurrentHashMap<>();
    private final Map<String, Contador> porIp = new ConcurrentHashMap<>();

    private final int limitePorConta;
    private final int limitePorIp;
    private final Duration janela;

    public LimiteTentativasLogin(
            @Value("${conectapet.limites.login-por-conta:10}") int limitePorConta,
            @Value("${conectapet.limites.login-por-ip:30}") int limitePorIp,
            @Value("${conectapet.limites.login-janela:PT15M}") Duration janela) {
        this.limitePorConta = limitePorConta;
        this.limitePorIp = limitePorIp;
        this.janela = janela;
    }

    /** Chamado ANTES de conferir a senha. Estoura quando o teto foi atingido. */
    public void verificar(String email, String ipHash) {
        if (atingiu(porConta, chaveConta(email), limitePorConta)) {
            // Mensagem sem dizer se a conta existe: a tela de login inteira e
            // construida para nao responder essa pergunta.
            throw new ProblemaException(TipoErro.BLOQUEADO,
                    "Muitas tentativas seguidas. Aguarde alguns minutos e tente de novo.");
        }
        if (atingiu(porIp, ipHash, limitePorIp)) {
            throw new ProblemaException(TipoErro.BLOQUEADO,
                    "Muitas tentativas deste dispositivo. Aguarde alguns minutos.");
        }
    }

    public void registrarFalha(String email, String ipHash) {
        int falhasDaConta = incrementar(porConta, chaveConta(email));
        incrementar(porIp, ipHash);

        if (falhasDaConta == limitePorConta) {
            // Uma linha por bloqueio, nao por tentativa: serve para perceber um
            // ataque em curso sem afogar o log.
            log.warn("Login bloqueado por excesso de tentativas. contaHash={}", chaveConta(email));
        }
    }

    /** Acerto limpa o balde da conta; o do IP fica, porque ele cobre varias. */
    public void registrarSucesso(String email) {
        porConta.remove(chaveConta(email));
    }

    private boolean atingiu(Map<String, Contador> mapa, String chave, int limite) {
        Contador c = mapa.get(chave);
        if (c == null || c.expiraEm().isBefore(Instant.now())) {
            return false;
        }
        return c.falhas() >= limite;
    }

    private int incrementar(Map<String, Contador> mapa, String chave) {
        // A janela conta a partir da PRIMEIRA falha e nao e estendida pelas
        // seguintes: renovar a cada tentativa deixaria a conta trancada
        // enquanto o atacante insistisse, que e o bloqueio virando o ataque.
        return mapa.compute(chave, (k, atual) -> {
            Instant agora = Instant.now();
            if (atual == null || atual.expiraEm().isBefore(agora)) {
                return new Contador(1, agora.plus(janela));
            }
            return new Contador(atual.falhas() + 1, atual.expiraEm());
        }).falhas();
    }

    /**
     * O e-mail nao vira chave em claro: este mapa fica em memoria e aparece em
     * dump de heap. O hash basta para contar.
     */
    private String chaveConta(String email) {
        return br.com.conectapet.comum.util.Hashes.sha256(
                email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    /** Evita crescer sem fim: entradas vencidas saem de tempos em tempos. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT10M")
    void limpar() {
        Instant agora = Instant.now();
        porConta.values().removeIf(c -> c.expiraEm().isBefore(agora));
        porIp.values().removeIf(c -> c.expiraEm().isBefore(agora));
    }
}
