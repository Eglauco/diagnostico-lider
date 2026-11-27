package br.com.diagnosticolider.rh;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CodigoRhRepository extends JpaRepository<CodigoRh, Long> {

	Optional<CodigoRh> findFirstByEmailAndTipoOrderByCriadoEmDescIdDesc(String email, CodigoRh.Tipo tipo);

	Optional<CodigoRh> findByLinkHash(String linkHash);

	/** O último convite mandado pelo administrador para cada e-mail (para o painel). */
	@Query("""
			select c.email, max(c.criadoEm) from CodigoRh c
			where c.tipo = br.com.diagnosticolider.rh.CodigoRh.Tipo.CONVITE and c.email in :emails
			group by c.email
			""")
	List<Object[]> ultimosConvites(List<String> emails);

}
