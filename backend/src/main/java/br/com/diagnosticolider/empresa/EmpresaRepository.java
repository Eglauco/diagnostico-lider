package br.com.diagnosticolider.empresa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

	boolean existsByNomeIgnoreCase(String nome);

	boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

	List<Empresa> findAllByOrderByCriadoEmDesc();

}
