package br.com.diagnosticolider.relatorio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.campanha.CodigoCampanha;
import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.DiagnosticoDimensao;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.MediaDimensao;
import br.com.diagnosticolider.diagnostico.MediaGeral;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.questionario.Dimensao;
import br.com.diagnosticolider.questionario.FaixaResultado;
import br.com.diagnosticolider.questionario.Opcao;
import br.com.diagnosticolider.questionario.Pergunta;
import br.com.diagnosticolider.questionario.QuestionarioVersao;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Coluna;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.CurvaGrupo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.EtiquetaMedia;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Destaque;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.DimensaoResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.FaixaResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.GrupoCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Indice;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Limites;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.OpcaoResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Paginacao;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Participacao;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PerguntaResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PontoLider;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PorCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Referencia;

/**
 * Relatório consolidado de uma empresa: junta os diagnósticos concluídos pelo link dela e
 * devolve só números agregados. Nada aqui identifica um líder.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioEmpresaService {

	/** Abaixo disso, o "consolidado" seria praticamente o resultado de uma pessoa. */
	public static final int MINIMO_LIDERES = 3;

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);

	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", PT_BR);

	/** Tamanho da curva da capa, em px CSS (largura útil da página A4). */
	private static final double LARGURA_CURVA = 660;

	private static final double ALTURA_CURVA = 212;

	/** A etiqueta "Média do grupo" na curva da capa (o template desenha com essas medidas). */
	static final double ETIQUETA_LARGURA = 140;

	static final double ETIQUETA_ALTURA = 22;

	/** Quantas dimensões e perguntas cabem numa página. */
	private static final int DIMENSOES_POR_PAGINA = 4;

	private static final int PERGUNTAS_POR_PAGINA = 3;

	private final EmpresaRepository empresas;

	private final DiagnosticoRepository diagnosticos;

	private final LogomarcaService logomarcas;

	private final Clock clock;

	RelatorioEmpresaService(EmpresaRepository empresas, DiagnosticoRepository diagnosticos, LogomarcaService logomarcas,
			Clock clock) {
		this.empresas = empresas;
		this.diagnosticos = diagnosticos;
		this.logomarcas = logomarcas;
		this.clock = clock;
	}

	/**
	 * Com data de encerramento, o relatório diz se é o resultado final (a avaliação encerrou) ou
	 * parcial (ainda aberta, os números podem mudar). Sem data, não diz nada.
	 */
	private RelatorioEmpresaDados.Situacao situacao(Empresa empresa) {
		if (empresa.getEncerraEm() == null) {
			return null;
		}
		Instant agora = Instant.now(clock);
		String data = Datas.porExtenso(empresa.getEncerraEm(), agora);
		return empresa.encerrada(agora)
				? new RelatorioEmpresaDados.Situacao("resultado final", "A avaliação foi encerrada em " + data + ": este é o resultado final.")
				: new RelatorioEmpresaDados.Situacao("resultado parcial",
						"A avaliação vai até " + data + ": este é um resultado parcial, e os números ainda podem mudar.");
	}

	public RelatorioEmpresaDados montar(Long empresaId) {
		return montar(empresaId, null);
	}

	/**
	 * Todos os líderes convidados responderam antes do prazo: o resultado não vai mudar por falta
	 * de resposta, então o relatório diz isso em vez de "resultado parcial".
	 */
	public RelatorioEmpresaDados montarComTodosRespondidos(Long empresaId, int convidados) {
		return montar(empresaId, new RelatorioEmpresaDados.Situacao("todos responderam",
				(convidados == 1 ? "O líder convidado respondeu" : "Todos os %d líderes convidados responderam".formatted(convidados))
						+ " antes do prazo: a participação está completa."));
	}

	private RelatorioEmpresaDados montar(Long empresaId, RelatorioEmpresaDados.Situacao situacao) {
		Empresa empresa = empresas.findById(empresaId)
			.orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
		List<Diagnostico> concluidos = diagnosticos.findByEmpresaIdAndStatusAndInativadoEmIsNull(empresaId, StatusDiagnostico.CONCLUIDO);
		int n = concluidos.size();
		if (n < MINIMO_LIDERES) {
			throw RegraNegocioException.conflito(("O relatório consolidado precisa de pelo menos %d diagnósticos "
					+ "concluídos, para que ninguém seja identificado. Hoje %s.")
				.formatted(MINIMO_LIDERES, n == 0 ? "não há nenhum" : n == 1 ? "há 1" : "há " + n));
		}

		// A versão mais respondida define perguntas, faixas e dimensões do relatório.
		Map<QuestionarioVersao, List<Diagnostico>> porVersao = concluidos.stream()
			.collect(Collectors.groupingBy(Diagnostico::getVersao, LinkedHashMap::new, Collectors.toList()));
		QuestionarioVersao versao = porVersao.entrySet()
			.stream()
			.max(Comparator.<Map.Entry<QuestionarioVersao, List<Diagnostico>>>comparingInt(e -> e.getValue().size())
				.thenComparingInt(e -> e.getKey().getNumero()))
			.orElseThrow()
			.getKey();
		List<Diagnostico> daVersao = porVersao.get(versao);

		List<Integer> indices = concluidos.stream().map(Diagnostico::getPontuacao).sorted().toList();
		var indice = new Indice(media(indices), mediana(indices), indices.getFirst(), indices.getLast());

		List<FaixaResultado> faixasDaVersao = versao.getFaixas()
			.stream()
			.sorted(Comparator.comparingInt(FaixaResultado::getPontuacaoMinima))
			.toList();
		List<FaixaResumo> faixas = faixas(faixasDaVersao, concluidos);
		FaixaResumo faixaDaMedia = faixas.stream()
			.filter(f -> indice.media() >= f.minimo() && indice.media() <= f.maximo())
			.findFirst()
			.orElse(faixas.getFirst());
		var limites = new Limites(faixasDaVersao.size() > 1 ? faixasDaVersao.get(1).getPontuacaoMinima() : 40,
				faixasDaVersao.getLast().getPontuacaoMinima());

		MediaGeral geral = diagnosticos.mediaGeral();
		Referencia referencia = geral.lideres() > n && geral.media() != null ? referencia(indice.media(), geral) : null;
		Map<String, Double> geralPorDimensao = referencia == null ? Map.of()
				: diagnosticos.mediaGeralPorDimensao()
					.stream()
					.filter(m -> m.media() != null)
					.collect(Collectors.toMap(MediaDimensao::codigo, MediaDimensao::media));

		List<DimensaoResumo> dimensoes = dimensoes(versao, concluidos, limites, geralPorDimensao);
		DimensaoResumo maisFragil = dimensoes.stream().filter(d -> "fragil".equals(d.destaque())).findFirst().orElse(null);
		DimensaoResumo maisForte = dimensoes.stream().filter(d -> "forte".equals(d.destaque())).findFirst().orElse(null);

		int iniciaram = (int) diagnosticos.countByEmpresaIdAndInativadoEmIsNull(empresaId);
		var participacao = new Participacao(iniciaram, n, iniciaram - n, Math.round(100f * n / iniciaram),
				tempoMediano(concluidos));

		PorCargo porCargo = AnaliseCargos.montar(linhas(concluidos), faixas,
				dimensoes.stream().map(d -> new AnaliseCargos.DimensaoInfo(d.codigo(), d.nome())).toList(),
				MINIMO_LIDERES);

		List<PerguntaResumo> perguntas = perguntas(versao, daVersao);
		List<List<DimensaoResumo>> paginasDimensoes = paginar(dimensoes, DIMENSOES_POR_PAGINA);
		List<List<PerguntaResumo>> paginasPerguntas = paginar(perguntas, PERGUNTAS_POR_PAGINA);
		// Por cargo: 2 páginas logo depois do resumo executivo (escada; mapa e tempo no cargo).
		int pCargos = porCargo == null ? 0 : 3;
		int pDimensoes = porCargo == null ? 3 : 5;
		int pPerguntas = pDimensoes + paginasDimensoes.size();
		int pFaixas = pPerguntas + paginasPerguntas.size();
		var paginas = new Paginacao(pFaixas + 1, pCargos, pDimensoes, pPerguntas, pFaixas, pFaixas + 1);

		String notaVersoes = daVersao.size() == n ? null
				: ("%d dos %d diagnósticos foram respondidos em outra versão do questionário: entram no índice, nas "
						+ "faixas e nas dimensões, mas não na análise por pergunta.")
					.formatted(n - daVersao.size(), n);

		return new RelatorioEmpresaDados(empresa.getNome(), DATA.format(LocalDate.now(FUSO)), periodo(concluidos),
				"%s, versão %d".formatted(versao.getQuestionario().getTitulo(), versao.getNumero()), n, participacao,
				indice, referencia, faixaDaMedia, faixas,
				destaques(n, indice, referencia, faixas, faixaDaMedia, maisForte, maisFragil, limites, participacao,
						porCargo, pCargos),
				curva(concluidos.stream().map(d -> new Lider(d.getPontuacao(), d.getFaixa().getCodigo())).toList(), indice.media()), histograma(indices, faixasDaVersao), limites, paginasDimensoes,
				maisFragil, paginasPerguntas, notaVersoes, MINIMO_LIDERES, paginas,
				"relatorio-consolidado-" + CodigoCampanha.normalizar(empresa.getNome()) + ".pdf", empresa.tema(),
				MarcaEmpresa.siglaDe(empresa.getNome()), logomarcas.dataUri(empresa),
				logomarcas.logo(empresa) != null && logomarcas.logo(empresa).pedeNome(), porCargo,
				situacao != null && !empresa.encerrada(Instant.now(clock)) ? situacao
						: situacao(empresa));
	}

	/** O que a análise por cargo usa de cada diagnóstico concluído. */
	private static List<AnaliseCargos.Linha> linhas(List<Diagnostico> concluidos) {
		return concluidos.stream().map(d -> {
			Map<String, Integer> dims = new LinkedHashMap<>();
			d.dimensoesOrdenadas().forEach(dd -> dims.put(dd.getDimensao().getCodigo(), dd.getPontuacao()));
			var cargo = d.getCargo();
			return new AnaliseCargos.Linha(cargo == null ? null : cargo.getId(), cargo == null ? null : cargo.getNome(),
					cargo == null ? 0 : cargo.getOrdem(), d.isCargoOutro(), d.getTempoCargo(), d.getPontuacao(),
					d.getFaixa().getCodigo(), dims);
		}).toList();
	}

	// Faixas ---------------------------------------------------------------

	private static List<FaixaResumo> faixas(List<FaixaResultado> faixas, List<Diagnostico> concluidos) {
		Map<String, Long> contagem = concluidos.stream()
			.collect(Collectors.groupingBy(d -> d.getFaixa().getCodigo(), Collectors.counting()));
		List<Integer> quantidades = faixas.stream().map(f -> contagem.getOrDefault(f.getCodigo(), 0L).intValue()).toList();
		List<Integer> percentuais = percentuais(quantidades);
		return IntStream.range(0, faixas.size()).mapToObj(i -> {
			FaixaResultado f = faixas.get(i);
			return new FaixaResumo(f.getCodigo(), f.getTitulo(), f.getLeituraEmpresa(), f.getPontuacaoMinima(),
					f.getPontuacaoMaxima(), quantidades.get(i), percentuais.get(i), List.copyOf(f.getAcoesEmpresa()));
		}).toList();
	}

	// Dimensões ------------------------------------------------------------

	private static List<DimensaoResumo> dimensoes(QuestionarioVersao versao, List<Diagnostico> concluidos,
			Limites limites, Map<String, Double> geral) {
		Map<String, List<Integer>> notas = new LinkedHashMap<>();
		for (Diagnostico d : concluidos) {
			for (DiagnosticoDimensao dd : d.dimensoesOrdenadas()) {
				notas.computeIfAbsent(dd.getDimensao().getCodigo(), c -> new ArrayList<>()).add(dd.getPontuacao());
			}
		}
		List<Dimensao> daVersao = versao.getDimensoes()
			.stream()
			.sorted(Comparator.comparingInt(Dimensao::getOrdem))
			.filter(dim -> notas.containsKey(dim.getCodigo()))
			.toList();
		List<DimensaoResumo> resumos = new ArrayList<>();
		for (Dimensao dim : daVersao) {
			List<Integer> valores = notas.get(dim.getCodigo());
			int total = valores.size();
			int emAlerta = Math.round(100f * valores.stream().filter(v -> v < limites.alerta()).count() / total);
			int fortes = Math.round(100f * valores.stream().filter(v -> v >= limites.forte()).count() / total);
			Double ref = geral.get(dim.getCodigo());
			resumos.add(new DimensaoResumo(dim.getCodigo(), dim.getNome(), media(valores),
					valores.stream().mapToInt(Integer::intValue).min().orElse(0),
					valores.stream().mapToInt(Integer::intValue).max().orElse(0), emAlerta, fortes,
					ref == null ? null : (int) Math.round(ref), dim.getAcaoEmpresa(), null));
		}
		if (resumos.size() < 2) {
			return resumos;
		}
		var forte = resumos.stream().max(Comparator.comparingInt(DimensaoResumo::media)).orElseThrow();
		var fragil = resumos.stream().min(Comparator.comparingInt(DimensaoResumo::media)).orElseThrow();
		if (forte.media() == fragil.media()) {
			return resumos;
		}
		return resumos.stream()
			.map(d -> d == forte ? comDestaque(d, "forte") : d == fragil ? comDestaque(d, "fragil") : d)
			.toList();
	}

	private static DimensaoResumo comDestaque(DimensaoResumo d, String destaque) {
		return new DimensaoResumo(d.codigo(), d.nome(), d.media(), d.menor(), d.maior(), d.emAlerta(), d.fortes(),
				d.referencia(), d.acao(), destaque);
	}

	// Perguntas ------------------------------------------------------------

	private static List<PerguntaResumo> perguntas(QuestionarioVersao versao, List<Diagnostico> daVersao) {
		List<Map<Long, List<Opcao>>> respostas = daVersao.stream().map(Diagnostico::respostasPorPergunta).toList();
		List<Pergunta> perguntas = versao.getPerguntas()
			.stream()
			.sorted(Comparator.comparingInt(Pergunta::getOrdem))
			.toList();
		List<PerguntaResumo> resumos = new ArrayList<>();
		for (int i = 0; i < perguntas.size(); i++) {
			Pergunta p = perguntas.get(i);
			List<List<Opcao>> escolhas = respostas.stream()
				.map(r -> r.get(p.getId()))
				.filter(e -> e != null && !e.isEmpty())
				.toList();
			int respondentes = escolhas.size();
			List<Opcao> opcoes = p.getOpcoes().stream().sorted(Comparator.comparingInt(Opcao::getOrdem)).toList();
			Map<Long, Long> contagem = escolhas.stream()
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Opcao::getId, Collectors.counting()));
			List<Integer> quantidades = opcoes.stream().map(o -> contagem.getOrDefault(o.getId(), 0L).intValue()).toList();
			List<Integer> percentuais = p.getTipo() == Pergunta.Tipo.ESCOLHA_UNICA ? percentuais(quantidades)
					: quantidades.stream().map(q -> respondentes == 0 ? 0 : Math.round(100f * q / respondentes)).toList();
			int maximo = quantidades.stream().mapToInt(Integer::intValue).max().orElse(0);

			// Índice da pergunta (0–100) e quantos ficaram na metade de baixo da escala.
			int min = p.pontuacaoMinima();
			int max = p.pontuacaoMaxima();
			List<Double> normalizados = escolhas.stream()
				.map(e -> max == min ? 1.0 : (e.stream().mapToInt(Opcao::getPontos).sum() - min) / (double) (max - min))
				.toList();
			int indice = (int) Math.round(normalizados.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100);
			int alerta = respondentes == 0 ? 0
					: Math.round(100f * normalizados.stream().filter(v -> v < 0.5).count() / respondentes);

			List<OpcaoResumo> itens = new ArrayList<>();
			for (int j = 0; j < opcoes.size(); j++) {
				Opcao o = opcoes.get(j);
				itens.add(new OpcaoResumo(String.valueOf((char) ('A' + j)), o.getTexto(), o.getPontos(), quantidades.get(j),
						percentuais.get(j), maximo > 0 && quantidades.get(j) == maximo));
			}
			resumos.add(new PerguntaResumo(i + 1, p.getDimensao() == null ? null : p.getDimensao().getNome(),
					p.getEnunciado(), respondentes, indice, alerta, itens));
		}
		return resumos;
	}

	// Curva e histograma ---------------------------------------------------

	/** Todos os líderes na curva; índices iguais ficam empilhados, como no painel. */
	/** Um líder na curva da capa: só o índice e a faixa, sem identificação. */
	record Lider(int indice, String faixa) {
	}

	static CurvaGrupo curva(List<Lider> concluidos, int mediaDoGrupo) {
		var desenho = CurvaCarreira.desenhar(LARGURA_CURVA, ALTURA_CURVA, mediaDoGrupo);
		int n = concluidos.size();
		double raio = n <= 25 ? 6.5 : n <= 80 ? 5 : 3.5;
		Map<Integer, List<Lider>> porIndice = concluidos.stream()
			.collect(Collectors.groupingBy(Lider::indice, TreeMap::new, Collectors.toList()));

		// A média cai onde as bolinhas se concentram: a etiqueta vai para a área livre embaixo da
		// curva (as pilhas sempre sobem a partir dela). Só se a curva passar baixa demais ali
		// (média muito alta), a etiqueta volta para o alto e as pilhas desviam dela.
		double meia = ETIQUETA_LARGURA / 2;
		double centro = Math.clamp(desenho.voce().x(), 10 + meia, LARGURA_CURVA - 10 - meia);
		double curvaMaisBaixa = 0;
		for (double x = centro - meia; x <= centro + meia; x += 4) {
			curvaMaisBaixa = Math.max(curvaMaisBaixa, CurvaCarreira.yNoX(LARGURA_CURVA, ALTURA_CURVA, x));
		}
		double yEmbaixo = Math.max(curvaMaisBaixa + raio + 6, desenho.base() - ETIQUETA_ALTURA - 16);
		boolean embaixo = yEmbaixo + ETIQUETA_ALTURA <= desenho.base() - 4;
		EtiquetaMedia media = embaixo
				? new EtiquetaMedia(arredondar(centro), arredondar(yEmbaixo), arredondar(desenho.voce().y()), desenho.base())
				: new EtiquetaMedia(arredondar(centro), 0, ETIQUETA_ALTURA, desenho.base());

		// Pilhas de quem tem o mesmo índice: o passo encolhe para nenhuma sair do desenho (nem
		// cobrir a etiqueta, quando ela está no alto).
		double passo = raio * 2 - 1;
		for (var pilha : porIndice.entrySet()) {
			if (pilha.getValue().size() > 1) {
				var base = CurvaCarreira.ponto(LARGURA_CURVA, ALTURA_CURVA, pilha.getKey());
				boolean sobEtiqueta = !embaixo && Math.abs(base.x() - centro) < meia + raio;
				double topo = sobEtiqueta ? ETIQUETA_ALTURA + 4 + raio : raio + 2;
				passo = Math.min(passo, Math.max(0, (base.y() - topo) / (pilha.getValue().size() - 1)));
			}
		}
		double passoFinal = passo;
		List<PontoLider> pontos = new ArrayList<>();
		porIndice.forEach((indice, grupo) -> {
			var ponto = CurvaCarreira.ponto(LARGURA_CURVA, ALTURA_CURVA, indice);
			for (int k = 0; k < grupo.size(); k++) {
				pontos.add(new PontoLider(arredondar(ponto.x()), arredondar(ponto.y() - k * passoFinal),
						grupo.get(k).faixa()));
			}
		});
		return new CurvaGrupo(desenho, pontos, raio, media);
	}

	private static List<Coluna> histograma(List<Integer> indices, List<FaixaResultado> faixas) {
		int[] contagem = new int[10];
		indices.forEach(i -> contagem[Math.min(i / 10, 9)]++);
		int maior = IntStream.of(contagem).max().orElse(1);
		List<Coluna> colunas = new ArrayList<>();
		for (int c = 0; c < 10; c++) {
			int inicio = c * 10;
			String faixa = faixas.stream()
				.filter(f -> f.contem(inicio))
				.map(FaixaResultado::getCodigo)
				.findFirst()
				.orElse("");
			colunas.add(new Coluna(c == 9 ? "90–100" : inicio + "–" + (inicio + 9), contagem[c],
					maior == 0 ? 0 : Math.round(100f * contagem[c] / maior), faixa));
		}
		return colunas;
	}

	// Textos ---------------------------------------------------------------

	private static List<Destaque> destaques(int n, Indice indice, Referencia referencia, List<FaixaResumo> faixas,
			FaixaResumo faixaDaMedia, DimensaoResumo forte, DimensaoResumo fragil, Limites limites,
			Participacao participacao, PorCargo porCargo, int paginaCargos) {
		List<Destaque> lista = new ArrayList<>();

		FaixaResumo maior = faixas.stream().max(Comparator.comparingInt(FaixaResumo::quantidade)).orElseThrow();
		boolean empate = faixas.stream().filter(f -> f.quantidade() == maior.quantidade()).count() > 1;
		if (!empate) {
			lista.add(new Destaque(maior.codigo(), "%d%% dos líderes estão com %s".formatted(maior.percentual(),
					maior.titulo().toLowerCase(PT_BR)), maior.leitura()));
		}
		else {
			lista.add(new Destaque("neutro", "O grupo está dividido entre as faixas",
					faixas.stream()
						.filter(f -> f.quantidade() > 0)
						.map(f -> "%s: %d%%".formatted(f.rotuloCurto(), f.percentual()))
						.collect(Collectors.joining(" · ")) + "."));
		}

		String comparacao = referencia == null ? ""
				: " A média de todos os líderes que já fizeram o diagnóstico é %d: %s.".formatted(referencia.media(),
						referencia.comparacao());
		lista.add(new Destaque(faixaDaMedia.codigo(),
				"Índice médio de %d (%s)".formatted(indice.media(), faixaDaMedia.titulo().toLowerCase(PT_BR)),
				"A mediana é %d: metade do grupo fica abaixo e metade acima. O menor índice foi %d e o maior, %d.%s"
					.formatted(indice.mediana(), indice.menor(), indice.maior(), comparacao)));

		if (forte != null) {
			lista.add(new Destaque("ASCENSAO", "%s é a dimensão mais forte, com %d".formatted(forte.nome(), forte.media()),
					"%d%% dos líderes têm %d ou mais nesta dimensão.".formatted(forte.fortes(), limites.forte())));
		}
		if (fragil != null) {
			lista.add(new Destaque("TRAVADA",
					"%s é a dimensão que mais pede atenção, com %d".formatted(fragil.nome(), fragil.media()),
					"%d%% dos líderes estão abaixo de %d nesta dimensão.%s".formatted(fragil.emAlerta(), limites.alerta(),
							fragil.acao() == null ? "" : " " + fragil.acao())));
		}

		// Com cargos, o cargo mais travado ocupa o lugar da participação (que já tem bloco próprio na página).
		GrupoCargo travado = porCargo == null ? null : porCargo.maisTravado();
		if (travado != null) {
			lista.add(new Destaque("TRAVADA", "%s é o cargo com mais carreiras travadas".formatted(travado.nome()),
					"%d%% dos líderes neste cargo estão na faixa travada, e o índice médio do cargo é %d, contra %d do grupo. Veja a página %d."
						.formatted(travado.travados(), travado.media(), indice.media(), paginaCargos)));
			return lista;
		}

		String adesao = participacao.emAndamento() == 0 ? "Todos os que começaram foram até o fim."
				: "%d %s e não %s: um lembrete costuma aumentar a participação.".formatted(participacao.emAndamento(),
						participacao.emAndamento() == 1 ? "líder começou" : "líderes começaram",
						participacao.emAndamento() == 1 ? "terminou" : "terminaram");
		lista.add(new Destaque("neutro",
				"%d de %d líderes concluíram o diagnóstico (%d%%)".formatted(n, participacao.iniciaram(),
						participacao.taxaConclusao()),
				"%s Tempo mediano de resposta: %s.".formatted(adesao, participacao.tempoMediano())));
		return lista;
	}

	private static Referencia referencia(int mediaEmpresa, MediaGeral geral) {
		int mediaGeral = (int) Math.round(geral.media());
		int diferenca = mediaEmpresa - mediaGeral;
		String comparacao = diferenca == 0 ? "a empresa está na média"
				: "a empresa está %d %s %s".formatted(Math.abs(diferenca), Math.abs(diferenca) == 1 ? "ponto" : "pontos",
						diferenca > 0 ? "acima" : "abaixo");
		return new Referencia(mediaGeral, (int) geral.lideres(), comparacao);
	}

	private static String periodo(List<Diagnostico> concluidos) {
		LocalDate inicio = concluidos.stream().map(Diagnostico::getConcluidoEm).min(Instant::compareTo).orElseThrow()
			.atZone(FUSO).toLocalDate();
		LocalDate fim = concluidos.stream().map(Diagnostico::getConcluidoEm).max(Instant::compareTo).orElseThrow()
			.atZone(FUSO).toLocalDate();
		if (inicio.equals(fim)) {
			return "em " + DATA.format(fim);
		}
		if (inicio.getYear() == fim.getYear() && inicio.getMonth() == fim.getMonth()) {
			return "de %d a %s".formatted(inicio.getDayOfMonth(), DATA.format(fim));
		}
		return inicio.getYear() == fim.getYear() ? "de %s a %s".formatted(DIA_MES.format(inicio), DATA.format(fim))
				: "de %s a %s".formatted(DATA.format(inicio), DATA.format(fim));
	}

	private static String tempoMediano(List<Diagnostico> concluidos) {
		List<Long> segundos = concluidos.stream()
			.map(d -> Duration.between(d.getIniciadoEm(), d.getConcluidoEm()).toSeconds())
			.sorted()
			.toList();
		long mediana = segundos.get(segundos.size() / 2);
		if (mediana < 60) {
			return "menos de 1 minuto";
		}
		long minutos = Math.round(mediana / 60.0);
		return minutos < 60 ? minutos + (minutos == 1 ? " minuto" : " minutos")
				: "%d h %02d min".formatted(minutos / 60, minutos % 60);
	}

	// Contas ---------------------------------------------------------------

	/** Percentuais inteiros que somam 100 (método do maior resto). */
	static List<Integer> percentuais(List<Integer> quantidades) {
		int total = quantidades.stream().mapToInt(Integer::intValue).sum();
		if (total == 0) {
			return quantidades.stream().map(q -> 0).toList();
		}
		int[] base = new int[quantidades.size()];
		double[] resto = new double[quantidades.size()];
		int soma = 0;
		for (int i = 0; i < base.length; i++) {
			double exato = 100.0 * quantidades.get(i) / total;
			base[i] = (int) Math.floor(exato);
			resto[i] = exato - base[i];
			soma += base[i];
		}
		List<Integer> ordem = IntStream.range(0, base.length)
			.boxed()
			.sorted(Comparator.comparingDouble((Integer i) -> resto[i]).reversed())
			.toList();
		for (int k = 0; k < 100 - soma; k++) {
			base[ordem.get(k)]++;
		}
		return IntStream.of(base).boxed().toList();
	}

	private static int media(List<Integer> valores) {
		return (int) Math.round(valores.stream().mapToInt(Integer::intValue).average().orElse(0));
	}

	private static int mediana(List<Integer> ordenados) {
		int n = ordenados.size();
		return n % 2 == 1 ? ordenados.get(n / 2)
				: (int) Math.round((ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2.0);
	}

	private static <T> List<List<T>> paginar(List<T> itens, int porPagina) {
		if (itens.isEmpty()) {
			return List.of();
		}
		List<List<T>> paginas = new ArrayList<>();
		for (int i = 0; i < itens.size(); i += porPagina) {
			paginas.add(itens.subList(i, Math.min(i + porPagina, itens.size())));
		}
		return paginas;
	}

	private static double arredondar(double valor) {
		return Math.round(valor * 10) / 10.0;
	}

}
