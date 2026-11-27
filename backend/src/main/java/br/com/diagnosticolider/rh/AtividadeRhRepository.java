package br.com.diagnosticolider.rh;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AtividadeRhRepository extends JpaRepository<AtividadeRh, Long> {

	/** A atividade dos gestores de uma empresa, a mais recente primeiro. */
	List<AtividadeRh> findByEmpresaIdOrderByQuandoDesc(Long empresaId);

	boolean existsByEmpresaIdAndEmailAndTipoAndQuandoAfter(Long empresaId, String email, AtividadeRh.Tipo tipo,
			Instant depois);

}
