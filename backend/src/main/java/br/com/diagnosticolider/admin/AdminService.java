package br.com.diagnosticolider.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.admin.AdminDtos.CampanhaResumo;
import br.com.diagnosticolider.admin.AdminDtos.CargoFiltro;
import br.com.diagnosticolider.admin.AdminDtos.Detalhe;
import br.com.diagnosticolider.admin.AdminDtos.EncontradoEmEmpresa;
import br.com.diagnosticolider.admin.AdminDtos.ItemLista;
import br.com.diagnosticolider.admin.AdminDtos.Pagina;
import br.com.diagnosticolider.admin.AdminDtos.Painel;
import br.com.diagnosticolider.admin.AdminDtos.PontoCurva;
import br.com.diagnosticolider.admin.AdminDtos.Referencia;
import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.campanha.CampanhaRepository;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.convite.OrigemLider;
import br.com.diagnosticolider.diagnostico.ContagemPorCampanha;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.LinhaResumo;
import br.com.diagnosticolider.diagnostico.MediaGeral;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.empresa.CargoService;
import br.com.diagnosticolider.empresa.ContagemCargo;
import br.com.diagnosticolider.questionario.Opcao;
import br.com.diagnosticolider.questionario.Pergunta;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class AdminService {

	private static final int TAMANHO_MAXIMO = 100;

	/** Pontos exibidos na curva do painel; acima disso a curva vira mancha. */
	private static final int MAXIMO_PONTOS_CURVA = 400;

	private final DiagnosticoRepository diagnosticos;

	private final CampanhaRepository campanhas;

	private final CargoService cargos;

	AdminService(DiagnosticoRepository diagnosticos, CampanhaRepository campanhas, CargoService cargos) {
		this.diagnosticos = diagnosticos;
		this.campanhas = campanhas;
		this.cargos = cargos;
	}

	/** Filtro de cargo: o id de um cargo ou "outro". Só vale junto com um link. */
	private record FiltroCargo(Long id, boolean outro) {

		static FiltroCargo de(String valor) {
			if (vazio(valor)) {
				return null;
			}
			if (OUTRO.equals(valor)) {
				return new FiltroCargo(null, true);
			}
			try {
				return new FiltroCargo(Long.valueOf(valor), false);
			}
			catch (NumberFormatException e) {
				return null;
			}
		}

	}

	static final String OUTRO = "outro";

	public Painel painel(String campanha, String cargo) {
		FiltroCargo filtroCargo = vazio(campanha) ? null : FiltroCargo.de(cargo);
		List<LinhaResumo> linhas = vazio(campanha) ? diagnosticos.resumoGeral()
				: filtroCargo == null ? diagnosticos.resumoDaCampanha(campanha)
				: filtroCargo.outro() ? diagnosticos.resumoDaCampanhaOutroCargo(campanha)
				: diagnosticos.resumoDaCampanhaNoCargo(campanha, filtroCargo.id());

		Resumo resumo = Resumo.de(linhas);
		List<PontoCurva> pontos = resumo.pontos();

		Map<String, Long> totais = diagnosticos.contagemPorCampanha()
			.stream()
			.collect(Collectors.toMap(ContagemPorCampanha::campanha, ContagemPorCampanha::total));
		List<CampanhaResumo> resumoCampanhas = campanhas.findAll(Sort.by("nome"))
			.stream()
			.map(c -> new CampanhaResumo(c.getCodigo(), c.getNome(),
					c.vinculadaAEmpresa() ? c.getEmpresa().getNome() : null,
					c.vinculadaAEmpresa() ? c.getEmpresa().getId() : null, totais.getOrDefault(c.getCodigo(), 0L)))
			.toList();

		return new Painel(resumo.total(), resumo.concluidos(), resumo.emAndamento(), resumo.indiceMedio(),
				resumo.porFaixa(), pontos.subList(0, Math.min(pontos.size(), MAXIMO_PONTOS_CURVA)), resumoCampanhas,
				cargosDoLink(campanha), referencia(campanha));
	}

	/** A média geral, para comparar o link de uma empresa com todos os líderes (a mesma do RH). */
	private Referencia referencia(String codigo) {
		boolean deEmpresa = !vazio(codigo)
				&& campanhas.findByCodigo(codigo).map(Campanha::vinculadaAEmpresa).orElse(false);
		if (!deEmpresa) {
			return null;
		}
		MediaGeral geral = diagnosticos.mediaGeral();
		return geral.media() == null ? null : new Referencia((int) Math.round(geral.media()), geral.lideres());
	}

	/**
	 * Busca do Diagnóstico Geral: em quais empresas a pessoa procurada também aparece, para levar
	 * direto à lista delas.
	 */
	public List<EncontradoEmEmpresa> encontradosNasEmpresas(String busca) {
		if (vazio(busca)) {
			return List.of();
		}
		String termo = "%" + escaparLike(busca.strip().toLowerCase(Locale.ROOT)) + "%";
		return diagnosticos.encontradosNasEmpresas(termo)
			.stream()
			.map(l -> new EncontradoEmEmpresa((Long) l[0], (String) l[1], ((Number) l[2]).longValue()))
			.toList();
	}

	/**
	 * Cargos do link escolhido, na ordem da hierarquia, com quantos diagnósticos cada um tem.
	 * Arquivados só aparecem se tiverem diagnósticos; "Outro cargo", se alguém escolheu.
	 */
	private List<CargoFiltro> cargosDoLink(String codigo) {
		if (vazio(codigo)) {
			return List.of();
		}
		Campanha campanha = campanhas.findByCodigo(codigo).orElse(null);
		if (campanha == null || !campanha.vinculadaAEmpresa()) {
			return List.of();
		}
		Map<Long, Long> totais = diagnosticos.contagemPorCargoDaCampanha(codigo)
			.stream()
			.collect(Collectors.toMap(ContagemCargo::cargo, ContagemCargo::diagnosticos));
		List<CargoFiltro> lista = new ArrayList<>(cargos.todos(campanha.getEmpresa().getId())
			.stream()
			.filter(c -> c.isAtivo() || totais.containsKey(c.getId()))
			.map(c -> new CargoFiltro(String.valueOf(c.getId()), c.getNome(), totais.getOrDefault(c.getId(), 0L)))
			.toList());
		long outros = diagnosticos.countByCampanhaCodigoAndCargoOutroTrueAndInativadoEmIsNull(codigo);
		if (outros > 0) {
			lista.add(new CargoFiltro(OUTRO, "Outro cargo", outros));
		}
		return lista;
	}

	/** inativos: false mostra os que entram nos cálculos; true, só os inativados. */
	public Pagina<ItemLista> listar(String busca, StatusDiagnostico status, String faixa, String campanha, String cargo,
			boolean inativos, int pagina, int tamanho) {
		var pedido = PageRequest.of(Math.max(0, pagina), Math.clamp(tamanho, 1, TAMANHO_MAXIMO),
				Sort.by(Sort.Direction.DESC, "iniciadoEm"));
		FiltroCargo filtroCargo = vazio(campanha) ? null : FiltroCargo.de(cargo);
		Page<Diagnostico> resultado = diagnosticos.findAll(filtro(busca, status, faixa, campanha, filtroCargo, inativos), pedido);
		Map<UUID, OrigemLider> origens = origens(resultado.getContent());
		List<ItemLista> itens = resultado.getContent().stream().map(d -> item(d, origens.get(d.getId()))).toList();
		return new Pagina<>(itens, resultado.getTotalElements(), resultado.getNumber(), resultado.getSize(),
				resultado.getTotalPages());
	}

	/**
	 * A lista filtrada inteira, sem paginar e na mesma ordem da tela (mais recentes primeiro):
	 * é o que a exportação em PDF e Excel leva.
	 */
	List<Diagnostico> filtrados(String busca, StatusDiagnostico status, String faixa, String campanha, String cargo,
			boolean inativos) {
		FiltroCargo filtroCargo = vazio(campanha) ? null : FiltroCargo.de(cargo);
		return diagnosticos.findAll(filtro(busca, status, faixa, campanha, filtroCargo, inativos),
				Sort.by(Sort.Direction.DESC, "iniciadoEm"));
	}

	/** O nome do cargo filtrado (ex.: "Gerente" ou "Outro cargo"), para descrever a exportação. */
	String nomeDoCargo(String campanha, String cargo) {
		if (vazio(campanha) || vazio(cargo)) {
			return null;
		}
		return cargosDoLink(campanha).stream().filter(c -> c.valor().equals(cargo)).map(CargoFiltro::nome).findFirst()
			.orElse(null);
	}

	public Detalhe detalhe(UUID id) {
		Diagnostico d = diagnosticos.findById(id)
			.orElseThrow(() -> new NaoEncontradoException("Diagnóstico não encontrado."));

		Map<Long, List<Opcao>> escolhas = d.respostasPorPergunta();
		List<Detalhe.RespostaDetalhe> respostas = new ArrayList<>();
		int numero = 0;
		for (Pergunta p : d.getVersao().getPerguntas()) {
			Set<Long> escolhidas = escolhas.getOrDefault(p.getId(), List.of())
				.stream()
				.map(Opcao::getId)
				.collect(Collectors.toSet());
			var opcoes = p.getOpcoes()
				.stream()
				.map(o -> new Detalhe.OpcaoDetalhe(o.getId(), o.getTexto(), o.getPontos(), escolhidas.contains(o.getId())))
				.toList();
			respostas.add(new Detalhe.RespostaDetalhe(p.getId(), ++numero,
					p.getDimensao() == null ? null : p.getDimensao().getNome(), p.getEnunciado(), !escolhidas.isEmpty(),
					opcoes));
		}

		Detalhe.Resultado resultado = null;
		if (d.concluido()) {
			var f = d.getFaixa();
			resultado = new Detalhe.Resultado(d.getPontuacao(),
					new Detalhe.Faixa(f.getCodigo(), f.getTitulo(), f.getResumo()),
					d.dimensoesOrdenadas()
						.stream()
						.map(dd -> new Detalhe.Dimensao(dd.getDimensao().getCodigo(), dd.getDimensao().getNome(),
								dd.getPontuacao()))
						.toList());
		}

		var r = d.getRespondente();
		Campanha c = d.getCampanha();
		var origem = new Detalhe.Origem(c.getCodigo(), c.getNome(),
				d.getEmpresa() == null ? null : d.getEmpresa().getNome(),
				c.vinculadaAEmpresa() ? c.getEmpresa().getId() : null, d.getVersao().getQuestionario().getTitulo(),
				d.getVersao().getNumero());
		return new Detalhe(d.getId(), d.getStatus(), d.getStatusEmail(), d.getIniciadoEm(), d.getConcluidoEm(),
				d.getConsentimentoEm(), d.getEmailEnviadoEm(),
				new Detalhe.Lider(r.getNome(), r.getEmail(), r.getWhatsapp(), r.getEmpresaInformada(), d.cargoDescrito(),
						d.getTempoCargo() == null ? null : d.getTempoCargo().texto()),
				origem,
				resultado, respostas, d.getInativadoEm(), d.getSubstituidoPor());
	}

	/** De onde veio o líder de cada diagnóstico da página (planilha, cadastro manual ou link). */
	private Map<UUID, OrigemLider> origens(List<Diagnostico> pagina) {
		if (pagina.isEmpty()) {
			return Map.of();
		}
		Map<UUID, OrigemLider> origens = new HashMap<>();
		for (Object[] linha : diagnosticos.origensDosDiagnosticos(pagina.stream().map(Diagnostico::getId).toList())) {
			origens.put((UUID) linha[0], (OrigemLider) linha[1]);
		}
		return origens;
	}

	private static ItemLista item(Diagnostico d, OrigemLider origemDoLider) {
		var r = d.getRespondente();
		var c = d.getCampanha();
		var f = d.getFaixa();
		return new ItemLista(d.getId(), r.getNome(), r.getEmail(), r.getEmpresaInformada(), c.getCodigo(), c.getNome(),
				c.vinculadaAEmpresa(), d.getStatus(), d.getPontuacao(), f == null ? null : f.getCodigo(),
				f == null ? null : f.getTitulo(), d.getStatusEmail(), d.getIniciadoEm(), d.getConcluidoEm(),
				d.perguntasRespondidas(), d.getVersao().getPerguntas().size(), d.cargoDescrito(), origemDoLider);
	}

	/** Busca por nome, e-mail ou empresa (sem diferenciar maiúsculas) e filtros exatos. */
	private static Specification<Diagnostico> filtro(String busca, StatusDiagnostico status, String faixa,
			String campanha, FiltroCargo cargo, boolean inativos) {
		return (raiz, consulta, cb) -> {
			List<Predicate> condicoes = new ArrayList<>();
			condicoes.add(inativos ? cb.isNotNull(raiz.get("inativadoEm")) : cb.isNull(raiz.get("inativadoEm")));
			if (!vazio(busca)) {
				String termo = "%" + escaparLike(busca.strip().toLowerCase(Locale.ROOT)) + "%";
				var respondente = raiz.get("respondente");
				condicoes.add(cb.or(cb.like(cb.lower(respondente.get("nome")), termo, '\\'),
						cb.like(cb.lower(respondente.get("email")), termo, '\\'),
						cb.like(cb.lower(respondente.get("empresaInformada")), termo, '\\')));
			}
			if (status != null) {
				condicoes.add(cb.equal(raiz.get("status"), status));
			}
			if (!vazio(faixa)) {
				condicoes.add(cb.equal(raiz.join("faixa", JoinType.LEFT).get("codigo"), faixa));
			}
			if (!vazio(campanha)) {
				condicoes.add(cb.equal(raiz.get("campanha").get("codigo"), campanha));
			}
			if (cargo != null) {
				condicoes.add(cargo.outro() ? cb.isTrue(raiz.get("cargoOutro"))
						: cb.equal(raiz.get("cargo").get("id"), cargo.id()));
			}
			return cb.and(condicoes.toArray(Predicate[]::new));
		};
	}

	private static String escaparLike(String texto) {
		return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static boolean vazio(String valor) {
		return valor == null || valor.isBlank();
	}

}
