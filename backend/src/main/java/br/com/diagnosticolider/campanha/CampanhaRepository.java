package br.com.diagnosticolider.campanha;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

	@EntityGraph(attributePaths = { "empresa", "questionario" })
	Optional<Campanha> findByCodigoAndAtivaTrue(String codigo);

}
