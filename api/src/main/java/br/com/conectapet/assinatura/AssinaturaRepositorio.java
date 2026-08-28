package br.com.conectapet.assinatura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssinaturaRepositorio extends JpaRepository<Assinatura, Long> {

    Optional<Assinatura> findFirstByUsuarioIdOrderByIdDesc(Long usuarioId);
}
