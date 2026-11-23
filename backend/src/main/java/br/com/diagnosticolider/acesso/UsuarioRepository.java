package br.com.diagnosticolider.acesso;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	/** Ativos primeiro, depois por nome. */
	List<Usuario> findAllByOrderByAtivoDescNomeAsc();

}
