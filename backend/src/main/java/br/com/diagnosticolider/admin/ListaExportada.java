package br.com.diagnosticolider.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.CurvaGrupo;

/**
 * A lista de diagnósticos do painel com os filtros da tela, pronta para o PDF
 * (relatorio/diagnosticos.html). Documento interno: tem o nome e o resultado de cada líder.
 *
 * @param paginas o PDF já paginado (cada linha tem altura fixa; ver {@link ExportacaoDiagnosticos})
 */
public record ListaExportada(String escopo, String titulo, String filtros, String exportadoEm, Resumo resumo,
		CurvaGrupo curva, List<Faixa> faixas, boolean mostrarOrigem, String colunaData, List<Pagina> paginas,
		String nomeArquivo) {

	public record Resumo(int total, int concluidos, int emAndamento, Integer indiceMedio, int taxaConclusao) {
	}

	/** Quantos concluídos caíram em cada faixa (a legenda da curva). */
	public record Faixa(String codigo, String rotulo, int quantidade, int percentual) {
	}

	public record Pagina(int numero, boolean primeira, List<Linha> linhas) {
	}

	/**
	 * Uma linha do PDF. Concluído: faixa e índice; em andamento: quantas perguntas respondeu.
	 * data: a data que a coluna da direita mostra (conclusão, início ou inativação).
	 */
	public record Linha(String nome, String cargo, String contato, boolean concluido, String faixa, String faixaRotulo,
			Integer indice, String andamento, String data) {
	}

	/** Um diagnóstico com tudo que a planilha leva (datas de verdade, para filtrar e ordenar no Excel). */
	record Registro(String nome, String email, String whatsapp, String empresa, String link, String cargo,
			String tempoNoCargo, boolean concluido, int respondidas, int perguntas, Integer indice, String faixa,
			String faixaRotulo, Map<String, Integer> dimensoes, Instant iniciadoEm, Instant concluidoEm,
			Instant inativadoEm) {
	}

}
