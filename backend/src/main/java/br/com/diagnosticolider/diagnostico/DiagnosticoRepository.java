package br.com.diagnosticolider.diagnostico;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DiagnosticoRepository extends JpaRepository<Diagnostico, UUID> {

	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("update Diagnostico d set d.statusEmail = :status, d.emailEnviadoEm = :enviadoEm where d.id = :id")
	int atualizarStatusEmail(UUID id, StatusEmail status, Instant enviadoEm);

}
