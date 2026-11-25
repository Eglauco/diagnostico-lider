package br.com.diagnosticolider.convite;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EventoConviteRepository extends JpaRepository<EventoConvite, Long> {

	List<EventoConvite> findByConviteIdOrderByQuandoAscIdAsc(Long conviteId);

	List<EventoConvite> findByConviteIdInAndTipoInOrderByQuandoAscIdAsc(Collection<Long> conviteIds,
			Collection<TipoEvento> tipos);

	Optional<EventoConvite> findFirstByConviteIdAndTipoOrderByIdDesc(Long conviteId, TipoEvento tipo);

	/** Toda a auditoria de uma empresa, para a aba do Excel: por líder e em ordem. */
	@Query("""
			select e from EventoConvite e join fetch e.convite c
			where c.campanha.empresa.id = :empresaId
			order by lower(c.nome), c.id, e.quando, e.id""")
	List<EventoConvite> daEmpresa(Long empresaId);

}
