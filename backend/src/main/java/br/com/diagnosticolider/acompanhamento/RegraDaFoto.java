package br.com.diagnosticolider.acompanhamento;

import java.util.Set;
import java.util.UUID;

import br.com.diagnosticolider.relatorio.RelatorioEmpresaService;

/**
 * Quando o resultado da empresa muda. O RH vê quem acabou de responder (é a lista de
 * participação, para cobrar quem falta). Se o resultado mudasse a cada resposta, bastaria uma
 * conta para saber o índice de quem terminou: antes 10 líderes com média 50, depois 11 com média
 * 52, então quem entrou tem 72 (52 × 11 − 50 × 10). Por isso o resultado é uma foto, e uma foto
 * nova só sai com pelo menos {@link #LOTE} respostas novas: a diferença entre duas fotos nunca é
 * uma pessoa só.
 * <ul>
 * <li>Sem foto, abre com {@link #LOTE} concluídos (o mesmo mínimo do relatório consolidado).</li>
 * <li>No fim da avaliação, a mesma regra: se só 1 ou 2 responderam depois da última foto, eles
 * ficam de fora do resultado final (com aviso), em vez de entregar quem são.</li>
 * <li>Quem foi inativado depois de entrar numa foto sai na foto seguinte (sair sozinho também
 * mostraria o resultado dele, na conta ao contrário).</li>
 * <li>Cargos e perguntas só aparecem no resultado final: um cargo pode ganhar uma pessoa só entre
 * duas fotos. Chegar ao fim (ou reabrir) com a mesma base só muda o que aparece, não os números.</li>
 * </ul>
 */
final class RegraDaFoto {

	static final int LOTE = RelatorioEmpresaService.MINIMO_LIDERES;

	private RegraDaFoto() {
	}

	sealed interface Decisao {
	}

	/** Ainda não há foto e há menos que o mínimo de concluídos. */
	record Aguardar(int concluidos) implements Decisao {
	}

	/** A última foto continua valendo. */
	record Manter() implements Decisao {
	}

	/**
	 * Foto nova. mesmaBase: os mesmos diagnósticos da anterior (só passou a ser, ou deixou de ser,
	 * o resultado final), então a média geral de referência também é a mesma.
	 */
	record Nova(Set<UUID> diagnosticos, boolean fim, boolean mesmaBase) implements Decisao {
	}

	/**
	 * @param concluidos os diagnósticos concluídos e ativos da empresa agora
	 * @param ultima     os diagnósticos da última foto (null: ainda não há foto)
	 * @param ultimaFim  se a última foto era o resultado final
	 * @param fimAgora   se a avaliação chegou ao fim (prazo vencido ou todos os convidados responderam)
	 */
	static Decisao decidir(Set<UUID> concluidos, Set<UUID> ultima, boolean ultimaFim, boolean fimAgora) {
		if (ultima == null) {
			return concluidos.size() < LOTE ? new Aguardar(concluidos.size()) : new Nova(Set.copyOf(concluidos), fimAgora, false);
		}
		if (novas(concluidos, ultima) >= LOTE) {
			return new Nova(Set.copyOf(concluidos), fimAgora, false);
		}
		return ultimaFim == fimAgora ? new Manter() : new Nova(Set.copyOf(ultima), fimAgora, true);
	}

	/** Quantos concluídos ainda não estão na foto. */
	static int novas(Set<UUID> concluidos, Set<UUID> foto) {
		return (int) concluidos.stream().filter(id -> !foto.contains(id)).count();
	}

}
