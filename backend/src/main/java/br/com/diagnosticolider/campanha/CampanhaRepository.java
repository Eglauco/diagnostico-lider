package br.com.diagnosticolider.campanha;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

	@EntityGraph(attributePaths = { "empresa", "questionario" })
	Optional<Campanha> findByCodigoAndAtivaTrue(String codigo);

	boolean existsByCodigo(String codigo);

	/** Também os links encerrados (o painel continua mostrando os dados deles). */
	@EntityGraph(attributePaths = { "empresa" })
	Optional<Campanha> findByCodigo(String codigo);

	/** Links das empresas, o mais antigo primeiro: o primeiro é o link principal. */
	List<Campanha> findByEmpresaIdInOrderByIdAsc(Collection<Long> empresas);

}
