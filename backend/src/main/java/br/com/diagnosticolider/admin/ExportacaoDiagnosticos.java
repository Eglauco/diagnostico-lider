package br.com.diagnosticolider.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.admin.ListaExportada.Faixa;
import br.com.diagnosticolider.admin.ListaExportada.Linha;
import br.com.diagnosticolider.admin.ListaExportada.Pagina;
import br.com.diagnosticolider.admin.ListaExportada.Registro;
import br.com.diagnosticolider.admin.ListaExportada.Resumo;
import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.campanha.CampanhaRepository;
import br.com.diagnosticolider.campanha.CodigoCampanha;
import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.compartilhado.Telefones;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;

/**
 * Exporta a lista de diagnósticos do painel com os filtros da tela (link, cargo, situação,
 * faixa e busca): todos os resultados, não só a página aberta. PDF para ler e mandar; Excel com
 * as datas de verdade e a pontuação de cada dimensão, para filtrar e fazer contas.
 */
@Service
@Transactional(readOnly = true)
public class ExportacaoDiagnosticos {

	/** Acima disso o PDF passa de 50 páginas: melhor o Excel. */
	static final int MAXIMO_PDF = 1000;

	// Paginação do PDF, em milímetros: cada peça tem altura fixa no template (diagnosticos.html).
	static final double ALTURA_PAGINA = 244;

	static final double ALTURA_ABERTURA = 48;

	static final double ALTURA_INDICADORES = 24;

	static final double ALTURA_CURVA = 74;

	static final double ALTURA_CABECALHO = 9;

	static final double ALTURA_LINHA = 12.5;

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter
		.ofPattern("d 'de' MMMM 'de' yyyy, 'às' H'h'mm", PT_BR);

	private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter DATA_ARQUIVO = DateTimeFormatter.ISO_LOCAL_DATE;

	/** As faixas na ordem da curva, com o rótulo curto do painel. */
	private static final Map<String, String> FAIXAS = new LinkedHashMap<>();
	static {
		FAIXAS.put("TRAVADA", "Travada");
		FAIXAS.put("PLATO", "Platô");
		FAIXAS.put("ASCENSAO", "Ascensão");
	}

	/** Os filtros da tela, como chegam na URL do painel. */
	public record Filtros(String busca, StatusDiagnostico status, String faixa, String campanha, String cargo,
			boolean inativos) {
	}

	public record Arquivo(String nome, byte[] conteudo) {
	}

	private final AdminService admin;

	private final CampanhaRepository campanhas;

	private final RelatorioPdfService pdf;

	private final PlanilhaDiagnosticos planilha;

	private final Clock clock;

	ExportacaoDiagnosticos(AdminService admin, CampanhaRepository campanhas, RelatorioPdfService pdf,
			PlanilhaDiagnosticos planilha, Clock clock) {
		this.admin = admin;
		this.campanhas = campanhas;
		this.pdf = pdf;
		this.planilha = planilha;
		this.clock = clock;
	}

	public Arquivo planilha(Filtros f) {
		Instant agora = clock.instant();
		List<Registro> registros = registros(f);
		byte[] conteudo = planilha.gerar(escopo(f.campanha()), titulo(registros.size(), f), filtros(f),
				exportadoEm(agora), registros, f.inativos());
		return new Arquivo(nomeArquivo(f, agora) + ".xlsx", conteudo);
	}

	public Arquivo pdf(Filtros f) {
		ListaExportada lista = montar(f);
		return new Arquivo(lista.nomeArquivo() + ".pdf", pdf.gerar("relatorio/diagnosticos", lista));
	}

	public String html(Filtros f) {
		return pdf.html("relatorio/diagnosticos", montar(f));
	}

	// ------------------------------------------------------------------ montagem

	ListaExportada montar(Filtros f) {
		Instant agora = clock.instant();
		List<Registro> registros = registros(f);
		if (registros.size() > MAXIMO_PDF) {
			throw new RegraNegocioException("O PDF vai até %s diagnósticos, e esta lista tem %s. Filtre mais a lista ou baixe em Excel."
				.formatted(milhar(MAXIMO_PDF), milhar(registros.size())));
		}

		List<Registro> concluidos = registros.stream().filter(r -> r.concluido() && r.indice() != null).toList();
		Integer media = concluidos.isEmpty() ? null
				: (int) Math.round(concluidos.stream().mapToInt(Registro::indice).average().orElse(0));
		Resumo resumo = new Resumo(registros.size(), concluidos.size(), registros.size() - concluidos.size(), media,
				pct(concluidos.size(), registros.size()));

		List<Faixa> faixas = FAIXAS.entrySet().stream().map(e -> {
			int quantidade = (int) concluidos.stream().filter(r -> e.getKey().equals(r.faixa())).count();
			return new Faixa(e.getKey(), e.getValue(), quantidade, pct(quantidade, concluidos.size()));
		}).toList();
		var curva = media == null ? null
				: RelatorioEmpresaService.curva(concluidos.stream()
					.map(r -> new RelatorioEmpresaService.Lider(r.indice(), r.faixa()))
					.toList(), media);

		boolean mostrarOrigem = vazio(f.campanha());
		String colunaData = f.inativos() ? "Inativado em" : "Data";
		List<Linha> linhas = registros.stream().map(r -> linha(r, mostrarOrigem, f.inativos())).toList();
		return new ListaExportada(escopo(f.campanha()), titulo(registros.size(), f), filtros(f), exportadoEm(agora),
				resumo, curva, faixas, mostrarOrigem, colunaData, paginar(linhas, curva != null), nomeArquivo(f, agora));
	}

	private List<Registro> registros(Filtros f) {
		return admin.filtrados(f.busca(), f.status(), f.faixa(), f.campanha(), f.cargo(), f.inativos())
			.stream()
			.map(ExportacaoDiagnosticos::registro)
			.toList();
	}

	private static Registro registro(Diagnostico d) {
		var r = d.getRespondente();
		Campanha c = d.getCampanha();
		var faixa = d.getFaixa();
		Map<String, Integer> dimensoes = new LinkedHashMap<>();
		if (d.concluido()) {
			d.dimensoesOrdenadas().forEach(dd -> dimensoes.put(dd.getDimensao().getNome(), dd.getPontuacao()));
		}
		String empresa = r.getEmpresaInformada() != null && !r.getEmpresaInformada().isBlank() ? r.getEmpresaInformada()
				: d.getEmpresa() == null ? null : d.getEmpresa().getNome();
		return new Registro(r.getNome(), r.getEmail(), whatsapp(r.getWhatsapp()), empresa,
				c.vinculadaAEmpresa() ? c.getNome() : "Link geral", d.cargoDescrito(),
				d.getTempoCargo() == null ? null : d.getTempoCargo().texto(), d.concluido(), d.perguntasRespondidas(),
				d.getVersao().getPerguntas().size(), d.getPontuacao(), faixa == null ? null : faixa.getCodigo(),
				faixa == null ? null : FAIXAS.getOrDefault(faixa.getCodigo(), faixa.getTitulo()), dimensoes,
				d.getIniciadoEm(), d.getConcluidoEm(), d.getInativadoEm());
	}

	/** "5518988172192" (como fica gravado) vira "(18) 98817-2192", como no painel. */
	static String whatsapp(String numero) {
		if (numero == null || numero.isBlank()) {
			return null;
		}
		String d = Telefones.digitos(numero);
		if (numero.strip().startsWith("+") && !d.startsWith("55")) {
			return numero.strip();
		}
		if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) {
			d = d.substring(2);
		}
		return d.length() == 10 || d.length() == 11 ? Telefones.formatar(d) : numero.strip();
	}

	private static Linha linha(Registro r, boolean mostrarOrigem, boolean inativos) {
		StringBuilder contato = new StringBuilder(r.email());
		if (r.whatsapp() != null && !r.whatsapp().isBlank()) {
			contato.append("  ·  ").append(r.whatsapp());
		}
		if (mostrarOrigem) {
			contato.append("  ·  ").append(r.empresa() != null ? r.empresa() : r.link());
		}
		Instant quando = inativos ? r.inativadoEm() : r.concluido() ? r.concluidoEm() : r.iniciadoEm();
		String data = quando == null ? null
				: (inativos ? "" : r.concluido() ? "Concluiu " : "Começou ") + DATA_CURTA.format(quando.atZone(Datas.FUSO));
		String andamento = r.concluido() ? null : r.respondidas() + " de " + r.perguntas();
		return new Linha(r.nome(), r.cargo(), contato.toString(), r.concluido(), r.faixa(), r.faixaRotulo(), r.indice(),
				andamento, data);
	}

	/**
	 * Distribui as linhas nas páginas A4. Cada peça tem altura fixa, então a conta é exata: a
	 * primeira página tem a abertura (e a curva, se houver concluídos); as outras, só a tabela.
	 */
	static List<Pagina> paginar(List<Linha> linhas, boolean comCurva) {
		double primeira = ALTURA_PAGINA - ALTURA_ABERTURA - ALTURA_INDICADORES - (comCurva ? ALTURA_CURVA : 0)
				- ALTURA_CABECALHO;
		int cabemNaPrimeira = (int) Math.floor(primeira / ALTURA_LINHA);
		int cabemNasOutras = (int) Math.floor((ALTURA_PAGINA - ALTURA_CABECALHO) / ALTURA_LINHA);
		List<Pagina> paginas = new ArrayList<>();
		int inicio = 0;
		do {
			int cabem = paginas.isEmpty() ? cabemNaPrimeira : cabemNasOutras;
			int fim = Math.min(linhas.size(), inicio + cabem);
			paginas.add(new Pagina(paginas.size() + 1, paginas.isEmpty(), linhas.subList(inicio, fim)));
			inicio = fim;
		}
		while (inicio < linhas.size());
		return paginas;
	}

	// ------------------------------------------------------------------ textos

	/** Qual recorte é: um link de empresa, o link geral ou todos. */
	private String escopo(String codigo) {
		if (vazio(codigo)) {
			return "Todos os links";
		}
		return campanhas.findByCodigo(codigo)
			.map(c -> c.vinculadaAEmpresa() ? c.getEmpresa().getNome() : "Link geral")
			.orElse("Todos os links");
	}

	static String titulo(int total, Filtros f) {
		// "Nenhum diagnóstico concluído" e "1 diagnóstico concluído": singular nos dois.
		boolean singular = total <= 1;
		String qual = f.inativos() ? singular ? " inativo" : " inativos"
				: f.status() == StatusDiagnostico.CONCLUIDO ? singular ? " concluído" : " concluídos"
				: f.status() == StatusDiagnostico.EM_ANDAMENTO ? " em andamento" : "";
		if (total == 0) {
			return "Nenhum diagnóstico" + qual;
		}
		return milhar(total) + (total == 1 ? " diagnóstico" : " diagnósticos") + qual;
	}

	/** Os demais filtros da tela, em texto (a situação já está no título). Null sem filtros. */
	private String filtros(Filtros f) {
		List<String> partes = new ArrayList<>();
		String cargo = admin.nomeDoCargo(f.campanha(), f.cargo());
		if (cargo != null) {
			partes.add("cargo " + cargo);
		}
		if (!vazio(f.faixa())) {
			partes.add("faixa " + FAIXAS.getOrDefault(f.faixa(), f.faixa()));
		}
		if (!vazio(f.busca())) {
			partes.add("busca “" + f.busca().strip() + "”");
		}
		return partes.isEmpty() ? null : "Filtros: " + String.join(" · ", partes);
	}

	private static String exportadoEm(Instant agora) {
		return "Exportado em " + DATA_HORA.format(agora.atZone(Datas.FUSO)).replace("h00", "h");
	}

	private String nomeArquivo(Filtros f, Instant agora) {
		String recorte = vazio(f.campanha()) ? "todos" : CodigoCampanha.normalizar(escopo(f.campanha()));
		return "diagnosticos-" + recorte + "-" + DATA_ARQUIVO.format(agora.atZone(Datas.FUSO));
	}

	private static String milhar(int numero) {
		return String.format(PT_BR, "%,d", numero);
	}

	private static int pct(int parte, int total) {
		return total == 0 ? 0 : (int) Math.round(parte * 100.0 / total);
	}

	private static boolean vazio(String valor) {
		return valor == null || valor.isBlank();
	}

}
