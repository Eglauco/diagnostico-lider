package br.com.diagnosticolider.empresa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

	/** Todos, inclusive os arquivados (relatórios e painel). */
	List<Cargo> findByEmpresaIdOrderByOrdemAscIdAsc(Long empresaId);

	/** Só os que aparecem para o líder escolher. */
	List<Cargo> findByEmpresaIdAndAtivoTrueOrderByOrdemAscIdAsc(Long empresaId);

	/** Quantos diagnósticos (de qualquer situação) cada cargo da empresa já recebeu. */
	@Query("""
			select new br.com.diagnosticolider.empresa.ContagemCargo(c.id, count(d))
			from br.com.diagnosticolider.diagnostico.Diagnostico d join d.cargo c
			where c.empresa.id = :empresaId
			group by c.id
			""")
	List<ContagemCargo> contagemDeDiagnosticos(Long empresaId);

}
