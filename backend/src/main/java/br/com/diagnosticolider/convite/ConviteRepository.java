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

	/** Os que começaram pelo link aberto (para dizer "já está na lista" em vez de "já tem convite"). */
	@Query("select lower(c.email) from Convite c where c.campanha.id = :campanhaId and c.origem = br.com.diagnosticolider.convite.OrigemLider.LINK")
	List<String> emailsDoLinkAberto(Long campanhaId);

	/** O líder da empresa com este e-mail (o ativo primeiro, se houver mais de um link). */
	@EntityGraph(attributePaths = { "campanha", "diagnostico" })
	@Query("select c from Convite c where c.campanha.empresa.id = :empresaId and lower(c.email) = lower(:email) order by c.inativadoEm nulls first, c.id")
	List<Convite> daEmpresaPorEmail(Long empresaId, String email);

	/**
	 * Diagnósticos de links de empresa sem cadastro de líder (e não substituídos), do mais antigo ao
	 * mais novo: quem começou pelo link aberto antes do cadastro existir (ver LideresDoLink).
	 */
	@Query("""
			select d from Diagnostico d
			where d.empresa is not null and d.substituidoPor is null
			  and not exists (select c from Convite c where c.diagnostico = d)
			order by d.iniciadoEm, d.id
			""")
	List<br.com.diagnosticolider.diagnostico.Diagnostico> semCadastro();

	/**
	 * Quem já tinha concluído e começou de novo pelo link aberto: a tentativa nova fica sem cadastro
	 * até terminar (a resposta concluída vale até lá). O painel e a página do RH mostram que o líder
	 * está respondendo de novo.
	 */
	@Query("""
			select d from Diagnostico d
			where d.empresa.id = :empresaId and d.substituidoPor is null and d.inativadoEm is null
			  and d.status = br.com.diagnosticolider.diagnostico.StatusDiagnostico.EM_ANDAMENTO
			  and not exists (select c from Convite c where c.diagnostico = d)
			order by d.iniciadoEm
			""")
	List<br.com.diagnosticolider.diagnostico.Diagnostico> tentativasSemCadastro(Long empresaId);

	/** Quantos líderes de cada origem a empresa tem (ativos). */
	@Query("select c.origem, count(c) from Convite c where c.campanha.empresa.id = :empresaId and c.inativadoEm is null group by c.origem")
	List<Object[]> origensDaEmpresa(Long empresaId);

	/** Os que estão inativos (para dizer "reative" em vez de "já tem convite"). */
	@Query("select lower(c.email) from Convite c where c.campanha.id = :campanhaId and c.inativadoEm is not null")
	List<String> emailsInativos(Long campanhaId);

	boolean existsByToken(String token);

	Optional<Convite> findByDiagnosticoId(UUID diagnosticoId);

	@EntityGraph(attributePaths = { "campanha", "campanha.empresa", "cargo" })
	List<Convite> findByIdIn(Collection<Long> ids);

}
