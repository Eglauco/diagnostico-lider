package br.com.diagnosticolider.acompanhamento;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface EnvioAcompanhamentoRepository extends JpaRepository<EnvioAcompanhamento, Long> {

	List<EnvioAcompanhamento> findTop30ByEmpresaIdOrderByQuandoDescIdDesc(Long empresaId);

	/** O último relatório que chegou ao gestor: base do "+3 desde o último relatório". */
	Optional<EnvioAcompanhamento> findFirstByEmpresaIdAndTipoInAndEnviadoTrueOrderByQuandoDescIdDesc(Long empresaId,
			Collection<EnvioAcompanhamento.Tipo> tipos);

	long countByEmpresaIdAndTipoInAndEnviadoFalseAndQuandoGreaterThanEqual(Long empresaId,
			Collection<EnvioAcompanhamento.Tipo> tipos, Instant desde);

}
