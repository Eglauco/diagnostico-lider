package br.com.diagnosticolider.empresa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

	boolean existsByNomeIgnoreCase(String nome);

	boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

	List<Empresa> findAllByOrderByCriadoEmDesc();

	@Query("select e.id from Empresa e order by e.id")
	List<Long> ids();

	/** Trava a empresa até o fim da transação (ex.: duas telas pedindo a mesma foto do resultado). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Empresa e where e.id = :id")
	Optional<Empresa> travar(Long id);

	/** As empresas em que o e-mail está na lista de gestores (o acesso do RH). */
	@Query("select distinct e from Empresa e join e.gestores g where lower(g) = lower(:email) order by e.nome")
	List<Empresa> dosGestores(String email);

}
