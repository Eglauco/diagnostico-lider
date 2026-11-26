package br.com.diagnosticolider.diagnostico;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.empresa.ContagemCargo;

public interface DiagnosticoRepository extends JpaRepository<Diagnostico, UUID>, JpaSpecificationExecutor<Diagnostico> {

	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("update Diagnostico d set d.statusEmail = :status, d.emailEnviadoEm = :enviadoEm where d.id = :id")
	int atualizarStatusEmail(UUID id, StatusEmail status, Instant enviadoEm);

	/** Lista do painel: já traz campanha, empresa e faixa para não fazer uma consulta por linha. */
	@Override
	@EntityGraph(attributePaths = { "campanha", "empresa", "faixa", "cargo" })
	Page<Diagnostico> findAll(Specification<Diagnostico> filtro, Pageable pagina);

	/** A lista do painel inteira (exportação), com o mesmo carregamento antecipado. */
	@Override
	@EntityGraph(attributePaths = { "campanha", "empresa", "faixa", "cargo" })
	List<Diagnostico> findAll(Specification<Diagnostico> filtro, Sort ordem);

	@Query("""
			select new br.com.diagnosticolider.diagnostico.LinhaResumo(d.status, f.codigo, d.pontuacao)
			from Diagnostico d left join d.faixa f
			where d.inativadoEm is null
			""")
	List<LinhaResumo> resumoGeral();

	@Query("""
			select new br.com.diagnosticolider.diagnostico.LinhaResumo(d.status, f.codigo, d.pontuacao)
			from Diagnostico d left join d.faixa f
			where d.campanha.codigo = :campanha and d.inativadoEm is null
			""")
	List<LinhaResumo> resumoDaCampanha(String campanha);

	@Query("""
			select new br.com.diagnosticolider.diagnostico.LinhaResumo(d.status, f.codigo, d.pontuacao)
			from Diagnostico d left join d.faixa f
			where d.campanha.codigo = :campanha and d.cargo.id = :cargo and d.inativadoEm is null
			""")
	List<LinhaResumo> resumoDaCampanhaNoCargo(String campanha, Long cargo);

	@Query("""
			select new br.com.diagnosticolider.diagnostico.LinhaResumo(d.status, f.codigo, d.pontuacao)
			from Diagnostico d left join d.faixa f
			where d.campanha.codigo = :campanha and d.cargoOutro = true and d.inativadoEm is null
			""")
	List<LinhaResumo> resumoDaCampanhaOutroCargo(String campanha);

	/** Quantos diagnósticos do link escolheram cada cargo (para os filtros do painel). */
	@Query("""
			select new br.com.diagnosticolider.empresa.ContagemCargo(c.id, count(d))
			from Diagnostico d join d.cargo c
			where d.campanha.codigo = :campanha and d.inativadoEm is null
			group by c.id
			""")
	List<ContagemCargo> contagemPorCargoDaCampanha(String campanha);

	long countByCampanhaCodigoAndCargoOutroTrueAndInativadoEmIsNull(String campanha);

	@Query("""
			select new br.com.diagnosticolider.diagnostico.ContagemPorCampanha(c.codigo, count(d))
			from Diagnostico d join d.campanha c
			where d.inativadoEm is null
			group by c.codigo
			""")
	List<ContagemPorCampanha> contagemPorCampanha();

	@Query("""
			select new br.com.diagnosticolider.diagnostico.LinhaResumo(d.status, f.codigo, d.pontuacao)
			from Diagnostico d left join d.faixa f
			where d.empresa.id = :empresa and d.inativadoEm is null
			""")
	List<LinhaResumo> resumoDaEmpresa(Long empresa);

	@Query("""
			select new br.com.diagnosticolider.diagnostico.ContagemPorEmpresa(e.id, count(d),
				sum(case when d.status = br.com.diagnosticolider.diagnostico.StatusDiagnostico.CONCLUIDO then 1 else 0 end),
				max(d.iniciadoEm))
			from Diagnostico d join d.empresa e
			where d.inativadoEm is null
			group by e.id
			""")
	List<ContagemPorEmpresa> contagemPorEmpresa();

	/** Diagnósticos (ativos) de uma empresa; as respostas e dimensões vêm em lote, pelo batch fetch. */
	@EntityGraph(attributePaths = { "faixa", "versao", "cargo" })
	List<Diagnostico> findByEmpresaIdAndStatusAndInativadoEmIsNull(Long empresa, StatusDiagnostico status);

	long countByEmpresaIdAndStatusAndInativadoEmIsNull(Long empresa, StatusDiagnostico status);

	long countByEmpresaIdAndInativadoEmIsNull(Long empresa);

	@Query("""
			select new br.com.diagnosticolider.diagnostico.MediaGeral(avg(d.pontuacao), count(d))
			from Diagnostico d
			where d.status = br.com.diagnosticolider.diagnostico.StatusDiagnostico.CONCLUIDO and d.inativadoEm is null
			""")
	MediaGeral mediaGeral();

	@Query("""
			select new br.com.diagnosticolider.diagnostico.MediaDimensao(dd.dimensao.codigo, avg(dd.pontuacao))
			from DiagnosticoDimensao dd
			where dd.diagnostico.inativadoEm is null
			group by dd.dimensao.codigo
			""")
	List<MediaDimensao> mediaGeralPorDimensao();

}
