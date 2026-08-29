package br.com.conectapet.publico;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class ListaEsperaServico {

    private final ListaEsperaRepositorio repo;

    public ListaEsperaServico(ListaEsperaRepositorio repo) {
        this.repo = repo;
    }

    /** Silencioso para e-mail repetido: a resposta nao pode revelar quem ja esta na lista. */
    @Transactional
    public void registrar(String email, String tipoPet, String ipHash) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        if (repo.existsByEmail(normalizado)) {
            return;
        }
        Inscricao i = new Inscricao();
        i.setEmail(normalizado);
        i.setTipoPet(tipoPet);
        i.setIpHash(ipHash);
        repo.save(i);
    }

    @Entity
    @Table(name = "lista_espera")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Inscricao {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, unique = true)
        private String email;

        @Column(name = "tipo_pet")
        private String tipoPet;

        /** Consentimento registrado com hash do IP, nunca o IP em claro. */
        @Column(name = "ip_hash", columnDefinition = "CHAR(32)")
        private String ipHash;

        @Column(name = "criado_em", nullable = false, updatable = false)
        private Instant criadoEm;

        @PrePersist
        void aoCriar() {
            criadoEm = Instant.now();
        }
    }
}
