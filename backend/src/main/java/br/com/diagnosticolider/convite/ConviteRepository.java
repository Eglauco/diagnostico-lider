package br.com.diagnosticolider.convite;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConviteRepository extends JpaRepository<Convite, Long> {

	@EntityGraph(attributePaths = { "campanha", "campanha.empresa", "cargo", "diagnostico" })
	Optional<Convite> findByToken(String token);

	@EntityGraph(attributePaths = { "campanha", "campanha.empresa", "cargo", "diagnostico" })
	Optional<Convite> findWithCampanhaById(Long id);

	/** Todos os convites dos links da empresa, o mais recente primeiro. */
	@EntityGraph(attributePaths = { "cargo", "diagnostico" })
	@Query("select c from Convite c where c.campanha.empresa.id = :empresaId order by c.criadoEm desc, c.id desc")
	List<Convite> daEmpresa(Long empresaId);

	/** E-mails (em minúsculas) que já têm convite no link, ativo ou inativo. */
	@Query("select lower(c.email) from Convite c where c.campanha.id = :campanhaId")
	List<String> emailsDoLink(Long campanhaId);

	/** Os que estão inativos (para dizer "reative" em vez de "já tem convite"). */
	@Query("select lower(c.email) from Convite c where c.campanha.id = :campanhaId and c.inativadoEm is not null")
	List<String> emailsInativos(Long campanhaId);

	boolean existsByToken(String token);

	Optional<Convite> findByDiagnosticoId(UUID diagnosticoId);

	@EntityGraph(attributePaths = { "campanha", "campanha.empresa", "cargo" })
	List<Convite> findByIdIn(Collection<Long> ids);

}
