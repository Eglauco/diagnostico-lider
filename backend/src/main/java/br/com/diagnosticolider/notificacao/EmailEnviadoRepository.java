package br.com.diagnosticolider.notificacao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEnviadoRepository extends JpaRepository<EmailEnviado, Long> {

	Optional<EmailEnviado> findByProvedorId(String provedorId);

}
