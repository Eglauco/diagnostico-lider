package br.com.diagnosticolider.relatorio;

import java.util.List;

import br.com.diagnosticolider.empresa.PaletaMarca;

/**
 * Relatório consolidado de uma empresa, já calculado para o template. Só números
 * agregados: nenhum campo identifica um líder.
 */
public record RelatorioEmpresaDados(String empresa, String emitidoEm, String periodo, String questionario, int lideres,
		Participacao participacao, Indice indice, Referencia referencia, FaixaResumo faixaDaMedia,
		List<FaixaResumo> faixas, List<Destaque> destaques, CurvaGrupo curva, List<Coluna> histograma,
		Limites limites, List<List<DimensaoResumo>> paginasDimensoes, DimensaoResumo maisFragil,
		List<List<PerguntaResumo>> paginasPerguntas, String notaVersoes, int minimoLideres, Paginacao paginas,
		String nomeArquivo, PaletaMarca.Tema tema, String sigla, String logo, boolean logoPedeNome, PorCargo porCargo) {

	/** Quantos começaram, quantos terminaram e quanto tempo levaram. */
	public record Participacao(int iniciaram, int concluiram, int emAndamento, int taxaConclusao,
			String tempoMediano) {
	}

	public record Indice(int media, int mediana, int menor, int maior) {
	}

	/** Média de todos os líderes que já fizeram o diagnóstico, para comparação. */
	public record Referencia(int media, int lideres, String comparacao) {
	}

	public record FaixaResumo(String codigo, String titulo, String leitura, int minimo, int maximo, int quantidade,
			int percentual, List<String> acoes) {

		public String rotuloCurto() {
			return switch (codigo) {
				case "TRAVADA" -> "Travada";
				case "PLATO" -> "Platô";
				case "ASCENSAO" -> "Ascensão";
				default -> titulo;
			};
		}

	}

	/** Uma frase do resumo executivo; o código colore o marcador (faixa ou "neutro"). */
	public record Destaque(String codigo, String titulo, String texto) {
	}

	public record CurvaGrupo(CurvaCarreira.Desenho desenho, List<PontoLider> pontos, double raio) {
	}

	public record PontoLider(double x, double y, String faixa) {
	}

	/** Uma barra do histograma de índices (ex.: 40–49). */
	public record Coluna(String rotulo, int quantidade, int altura, String faixa) {
	}

	/** Onde a faixa mais baixa termina e onde a mais alta começa (ex.: 40 e 70). */
	public record Limites(int alerta, int forte) {
	}

	public record DimensaoResumo(String codigo, String nome, int media, int menor, int maior, int emAlerta, int fortes,
			Integer referencia, String acao, String destaque) {
	}

	public record PerguntaResumo(int numero, String dimensao, String enunciado, int respondentes, int indice,
			int alerta, List<OpcaoResumo> opcoes) {
	}

	public record OpcaoResumo(String letra, String texto, int pontos, int quantidade, int percentual,
			boolean maisEscolhida) {
	}

	/** Número da primeira página de cada parte (as páginas têm tamanho fixo; cargos = 0: sem a parte). */
	public record Paginacao(int total, int cargos, int dimensoes, int perguntas, int faixas, int metodologia) {
	}

	/**
	 * Por cargo e por tempo no cargo. grupos: os cargos com o mínimo de líderes, na ordem da
	 * hierarquia, e por último "Outros cargos" (se houver). maisTravado: nulo sem comparação possível.
	 */
	public record PorCargo(List<GrupoCargo> grupos, GrupoCargo maisTravado, List<String> dimensoes,
			List<TempoResumo> tempos, String notaAnonimato, String notaSemCargo) {
	}

	/** travados: % na faixa mais baixa. dimensoes: a média de cada dimensão, na ordem de PorCargo.dimensoes. */
	public record GrupoCargo(String nome, boolean outros, int lideres, int media, String faixaDaMedia,
			List<ParteFaixa> faixas, int travados, List<Celula> dimensoes) {
	}

	public record ParteFaixa(String codigo, String rotulo, int quantidade, int percentual) {
	}

	/** Média de uma dimensão num cargo (nula se ninguém do cargo tem a dimensão). */
	public record Celula(Integer media, String faixa) {
	}

	/** oculto: há líderes, mas menos que o mínimo (sem números, para ninguém ser identificado). */
	public record TempoResumo(String rotulo, int lideres, boolean oculto, Integer media, String faixaDaMedia,
			List<ParteFaixa> faixas) {
	}

}
