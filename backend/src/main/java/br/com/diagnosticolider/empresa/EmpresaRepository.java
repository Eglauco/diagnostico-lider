package br.com.diagnosticolider.empresa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

	boolean existsByNomeIgnoreCase(String nome);

	boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

	List<Empresa> findAllByOrderByCriadoEmDesc();

	@Query("select e.id from Empresa e order by e.id")
	List<Long> ids();

}
