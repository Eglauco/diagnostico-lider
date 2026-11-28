package br.com.diagnosticolider.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.diagnosticolider.convite.OrigemLider;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.diagnostico.StatusEmail;

/** Contratos da API do painel administrativo. Aqui os dados aparecem completos (sem máscara). */
public final class AdminDtos {

	private AdminDtos() {
	}

	/**
	 * Indicadores do topo do painel, opcionalmente de uma campanha (e de um cargo dela). cargos: os
	 * cargos do link escolhido, para filtrar (vazio no link geral e em empresas sem cargos).
	 * referencia: a média geral de todos os líderes, para comparar o link de uma empresa (null fora dele).
	 */
	public record Painel(long total, long concluidos, long emAndamento, Integer indiceMedio, Map<String, Long> porFaixa,
			List<PontoCurva> pontos, List<CampanhaResumo> campanhas, List<CargoFiltro> cargos, Referencia referencia) {
	}

	/** A média geral (todas as empresas e o link geral) e quantos líderes concluídos ela soma. */
	public record Referencia(int media, long lideres) {
	}

	/** Quantos diagnósticos de uma empresa batem com a busca do Diagnóstico Geral. */
	public record EncontradoEmEmpresa(Long empresaId, String empresa, long total) {
	}

	/** valor: id do cargo ou "outro" (líderes que escolheram "Outro cargo"). */
	public record CargoFiltro(String valor, String nome, long total) {
	}

	/** Um líder concluído, para posicionar na curva da carreira do painel. */
	public record PontoCurva(int indice, String faixa) {
	}

	public record CampanhaResumo(String codigo, String nome, String empresa, Long empresaId, long total) {
	}

	public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho, int totalPaginas) {
	}

	public record ItemLista(UUID id, String nome, String email, String empresa, String campanha, String campanhaNome,
			boolean campanhaDeEmpresa, StatusDiagnostico status, Integer pontuacao, String faixa, String faixaTitulo,
			StatusEmail statusEmail, Instant iniciadoEm, Instant concluidoEm, int respondidas, int totalPerguntas,
			String cargo, OrigemLider origem) {
	}

	public record Detalhe(UUID id, StatusDiagnostico status, StatusEmail statusEmail, Instant iniciadoEm,
			Instant concluidoEm, Instant consentimentoEm, Instant emailEnviadoEm, Lider lider, Origem origem,
			Resultado resultado, List<RespostaDetalhe> respostas, Instant inativadoEm, UUID substituidoPor) {

		/** cargo e tempoCargo: só no link de empresa com cargos (nulos nos demais). */
		public record Lider(String nome, String email, String whatsapp, String empresa, String cargo,
				String tempoCargo) {
		}

		/** empresaId: a empresa do link (null no link geral), para voltar à aba Diagnóstico dela. */
		public record Origem(String campanha, String campanhaNome, String empresaVinculada, Long empresaId,
				String questionario, int versao) {
		}

		public record Resultado(int pontuacao, Faixa faixa, List<Dimensao> dimensoes) {
		}

		public record Faixa(String codigo, String titulo, String resumo) {
		}

		public record Dimensao(String codigo, String nome, int pontuacao) {
		}

		public record RespostaDetalhe(Long perguntaId, int numero, String dimensao, String enunciado, boolean respondida,
				List<OpcaoDetalhe> opcoes) {
		}

		public record OpcaoDetalhe(Long id, String texto, int pontos, boolean escolhida) {
		}

	}

}
