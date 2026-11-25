package br.com.diagnosticolider.acompanhamento;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import br.com.diagnosticolider.compartilhado.Datas;

/**
 * Quando sai cada e-mail automático de uma empresa. Só regra, sem banco nem envio: o agendador
 * roda a cada 5 minutos, monta o {@link Estado} e faz o que esta classe mandar.
 *
 * <ul>
 * <li>Relatório diário ao gestor: dias úteis, a partir das 8h (horário de Brasília), um por dia,
 * enquanto a avaliação está em andamento (já saiu algum convite e ainda não chegou ao fim).</li>
 * <li>Consolidado final: assim que todos os convidados respondem ou o prazo vence. Uma vez só; se
 * a avaliação reabrir (prazo novo, líder novo), volta a ser esperado.</li>
 * <li>Lembretes aos líderes: 3 dias e 1 dia antes do prazo, a partir das 8h, qualquer dia.</li>
 * </ul>
 * Nada sai depois das 20h (o servidor pode ter ficado fora do ar na hora certa), e um envio ao
 * gestor que falha é tentado até 3 vezes no dia.
 */
final class Agenda {

	static final LocalTime INICIO = LocalTime.of(8, 0);

	static final LocalTime FIM = LocalTime.of(20, 0);

	static final int TENTATIVAS_POR_DIA = 3;

	private Agenda() {
	}

	/**
	 * total, responderam e convidados: dos convites ativos. concluidos: diagnósticos concluídos da
	 * empresa por qualquer link (a empresa que só usa o link geral também recebe o consolidado no
	 * prazo). falhasHoje: envios ao gestor que falharam hoje.
	 */
	record Estado(boolean emailAtivo, boolean temGestores, boolean pausado, int total, int responderam,
			int convidados, int concluidos, Instant encerraEm, Instant diarioEm, Instant finalEm, Instant lembrete3DiasEm,
			Instant lembrete1DiaEm, int falhasHoje) {

		boolean comecou() {
			return convidados > 0;
		}

		boolean todosResponderam() {
			return total > 0 && responderam == total;
		}

		boolean encerrada(Instant agora) {
			return encerraEm != null && !agora.isBefore(encerraEm);
		}

		/** A avaliação chegou ao fim: é hora do consolidado. */
		boolean fim(Instant agora) {
			return (comecou() && todosResponderam()) || (encerrada(agora) && (comecou() || concluidos > 0));
		}

	}

	enum Acao {
		/** O consolidado já tinha saído, mas a avaliação reabriu: ele volta a ser esperado. */
		REABRIR_FINAL,
		FINAL,
		DIARIO,
		LEMBRETE_3_DIAS,
		LEMBRETE_1_DIA
	}

	static List<Acao> decidir(Estado e, Instant agora) {
		List<Acao> acoes = new ArrayList<>();
		if (!e.emailAtivo()) {
			return acoes;
		}
		boolean fim = e.fim(agora);
		boolean finalEnviado = e.finalEm() != null;
		if (!fim && finalEnviado) {
			acoes.add(Acao.REABRIR_FINAL);
			finalEnviado = false;
		}
		ZonedDateTime local = agora.atZone(Datas.FUSO);
		boolean noHorario = !local.toLocalTime().isBefore(INICIO) && local.toLocalTime().isBefore(FIM);
		boolean podeTentar = e.falhasHoje() < TENTATIVAS_POR_DIA;

		if (fim && !finalEnviado && e.temGestores() && podeTentar) {
			acoes.add(Acao.FINAL);
		}
		else if (!fim && e.comecou() && e.temGestores() && !e.pausado() && diaUtil(local.toLocalDate()) && noHorario
				&& podeTentar && !mesmoDia(e.diarioEm(), local.toLocalDate())) {
			acoes.add(Acao.DIARIO);
		}

		if (e.comecou() && e.encerraEm() != null && !e.encerrada(agora) && !e.todosResponderam() && noHorario) {
			long dias = diasAte(e.encerraEm(), agora);
			if (e.lembrete1DiaEm() == null && dias <= 1) {
				acoes.add(Acao.LEMBRETE_1_DIA);
			}
			else if (e.lembrete3DiasEm() == null && dias <= 3 && dias > 1) {
				acoes.add(Acao.LEMBRETE_3_DIAS);
			}
		}
		return acoes;
	}

	/** Dias de calendário até o prazo (0: encerra hoje). */
	static long diasAte(Instant prazo, Instant agora) {
		return ChronoUnit.DAYS.between(agora.atZone(Datas.FUSO).toLocalDate(), prazo.atZone(Datas.FUSO).toLocalDate());
	}

	/** A chamada do lembrete, que também abre o assunto: "Faltam 3 dias para responder". */
	static String chamada(long dias) {
		return dias <= 0 ? "Hoje é o último dia para responder"
				: dias == 1 ? "Amanhã é o último dia para responder" : "Faltam " + dias + " dias para responder";
	}

	static boolean diaUtil(LocalDate dia) {
		return dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY;
	}

	/** O próximo relatório diário: hoje às 8h (se ainda dá) ou o próximo dia útil às 8h. */
	static Instant proximoDiario(Instant agora, Instant diarioEm) {
		ZonedDateTime local = agora.atZone(Datas.FUSO);
		LocalDate dia = local.toLocalDate();
		boolean hojeAinda = diaUtil(dia) && local.toLocalTime().isBefore(FIM) && !mesmoDia(diarioEm, dia);
		if (!hojeAinda) {
			dia = dia.plusDays(1);
			while (!diaUtil(dia)) {
				dia = dia.plusDays(1);
			}
		}
		ZonedDateTime alvo = dia.atTime(INICIO).atZone(Datas.FUSO);
		return alvo.isBefore(local) ? agora : alvo.toInstant();
	}

	private static boolean mesmoDia(Instant quando, LocalDate dia) {
		return quando != null && quando.atZone(Datas.FUSO).toLocalDate().equals(dia);
	}

}
