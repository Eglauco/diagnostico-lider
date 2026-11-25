package br.com.diagnosticolider.acompanhamento;

import static br.com.diagnosticolider.acompanhamento.Agenda.Acao.DIARIO;
import static br.com.diagnosticolider.acompanhamento.Agenda.Acao.FINAL;
import static br.com.diagnosticolider.acompanhamento.Agenda.Acao.LEMBRETE_1_DIA;
import static br.com.diagnosticolider.acompanhamento.Agenda.Acao.LEMBRETE_3_DIAS;
import static br.com.diagnosticolider.acompanhamento.Agenda.Acao.REABRIR_FINAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.acompanhamento.Agenda.Estado;
import br.com.diagnosticolider.compartilhado.Datas;

/** Quando cada e-mail automático sai. 23/11/2026 é uma segunda-feira. */
class AgendaTest {

	private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);

	private static Instant em(LocalDate dia, int hora, int minuto) {
		return LocalDateTime.of(dia, java.time.LocalTime.of(hora, minuto)).atZone(Datas.FUSO).toInstant();
	}

	/** Em andamento: 30 convidados, 12 responderam, prazo na sexta às 22h, gestor cadastrado. */
	private static Estado andamento() {
		return new Estado(true, true, false, 30, 12, 30, 12, em(SEGUNDA.plusDays(4), 22, 0), null, null, null, null, 0);
	}

	private static Estado com(Estado e, Instant diarioEm, Instant finalEm, Instant l3, Instant l1) {
		return new Estado(e.emailAtivo(), e.temGestores(), e.pausado(), e.total(), e.responderam(), e.convidados(),
				e.concluidos(), e.encerraEm(), diarioEm, finalEm, l3, l1, e.falhasHoje());
	}

	@Test
	void diarioSaiNoDiaUtilDepoisDas8h() {
		assertThat(Agenda.decidir(andamento(), em(SEGUNDA, 8, 0))).containsExactly(DIARIO);
		assertThat(Agenda.decidir(andamento(), em(SEGUNDA, 7, 55))).isEmpty();
		assertThat(Agenda.decidir(andamento(), em(SEGUNDA, 20, 0))).isEmpty();
	}

	@Test
	void diarioNaoSaiNoFimDeSemanaNemDuasVezesNoDia() {
		Estado semPrazo = new Estado(true, true, false, 30, 12, 30, 12, null, null, null, null, null, 0);
		assertThat(Agenda.decidir(semPrazo, em(SEGUNDA.minusDays(1), 9, 0))).isEmpty();
		assertThat(Agenda.decidir(semPrazo, em(SEGUNDA.minusDays(2), 9, 0))).isEmpty();
		assertThat(Agenda.decidir(com(semPrazo, em(SEGUNDA, 8, 0), null, null, null), em(SEGUNDA, 15, 0))).isEmpty();
		assertThat(Agenda.decidir(com(semPrazo, em(SEGUNDA, 8, 0), null, null, null), em(SEGUNDA.plusDays(1), 8, 5)))
			.containsExactly(DIARIO);
	}

	@Test
	void semConviteEnviadoOuSemGestorODiarioNaoSai() {
		Estado semConvite = new Estado(true, true, false, 30, 0, 0, 0, null, null, null, null, null, 0);
		Estado semGestor = new Estado(true, false, false, 30, 12, 30, 12, null, null, null, null, null, 0);
		assertThat(Agenda.decidir(semConvite, em(SEGUNDA, 9, 0))).isEmpty();
		assertThat(Agenda.decidir(semGestor, em(SEGUNDA, 9, 0))).isEmpty();
	}

	@Test
	void pausadoSoSegueODiarioOFinalSaiMesmoAssim() {
		Estado pausado = new Estado(true, true, true, 30, 12, 30, 12, null, null, null, null, null, 0);
		Estado pausadoTodos = new Estado(true, true, true, 30, 30, 30, 30, null, null, null, null, null, 0);
		assertThat(Agenda.decidir(pausado, em(SEGUNDA, 9, 0))).isEmpty();
		assertThat(Agenda.decidir(pausadoTodos, em(SEGUNDA, 9, 0))).containsExactly(FINAL);
	}

	@Test
	void todosResponderamOFinalSaiNaHoraEmQualquerDia() {
		Estado todos = new Estado(true, true, false, 30, 30, 30, 30, em(SEGUNDA.plusDays(4), 22, 0), null, null, null,
				null, 0);
		Instant sabadoDeMadrugada = em(SEGUNDA.minusDays(2), 2, 0);
		assertThat(Agenda.decidir(todos, sabadoDeMadrugada)).containsExactly(FINAL);
		// Uma vez só.
		assertThat(Agenda.decidir(com(todos, null, sabadoDeMadrugada, null, null), em(SEGUNDA, 9, 0))).isEmpty();
	}

	@Test
	void prazoVencidoMandaOFinalComQuemRespondeu() {
		Estado vencido = andamento();
		Instant depois = em(SEGUNDA.plusDays(4), 22, 5);
		assertThat(Agenda.decidir(vencido, depois)).containsExactly(FINAL);
		assertThat(Agenda.decidir(com(vencido, null, depois, null, null), em(SEGUNDA.plusDays(7), 9, 0))).isEmpty();
	}

	@Test
	void soLinkGeralTambemRecebeOConsolidadoNoPrazo() {
		Estado linkGeral = new Estado(true, true, false, 0, 0, 0, 8, em(SEGUNDA, 18, 0), null, null, null, null, 0);
		assertThat(Agenda.decidir(linkGeral, em(SEGUNDA, 12, 0))).isEmpty();
		assertThat(Agenda.decidir(linkGeral, em(SEGUNDA, 18, 5))).containsExactly(FINAL);
	}

	@Test
	void avaliacaoReabertaVoltaAoDiario() {
		// O final saiu na sexta; o prazo foi estendido e ainda faltam líderes.
		Estado reaberta = com(andamento(), em(SEGUNDA.minusDays(3), 8, 0), em(SEGUNDA.minusDays(3), 22, 5), null, null);
		assertThat(Agenda.decidir(reaberta, em(SEGUNDA, 8, 0))).containsExactly(REABRIR_FINAL, DIARIO);
	}

	@Test
	void lembretesAosLideres3DiasE1DiaAntes() {
		Estado e = andamento();
		assertThat(Agenda.decidir(e, em(SEGUNDA, 9, 0))).containsExactly(DIARIO);
		assertThat(Agenda.decidir(e, em(SEGUNDA.plusDays(1), 9, 0))).contains(LEMBRETE_3_DIAS);
		Estado depoisDoPrimeiro = com(e, null, null, em(SEGUNDA.plusDays(1), 9, 0), null);
		assertThat(Agenda.decidir(depoisDoPrimeiro, em(SEGUNDA.plusDays(2), 9, 0))).doesNotContain(LEMBRETE_3_DIAS,
				LEMBRETE_1_DIA);
		assertThat(Agenda.decidir(depoisDoPrimeiro, em(SEGUNDA.plusDays(3), 9, 0))).contains(LEMBRETE_1_DIA);
		Estado depoisDosDois = com(e, null, null, em(SEGUNDA.plusDays(1), 9, 0), em(SEGUNDA.plusDays(3), 9, 0));
		assertThat(Agenda.decidir(depoisDosDois, em(SEGUNDA.plusDays(4), 9, 0))).containsExactly(DIARIO);
	}

	@Test
	void prazoCurtoPulaOLembreteDe3Dias() {
		Estado curto = new Estado(true, false, false, 30, 12, 30, 12, em(SEGUNDA.plusDays(1), 18, 0), null, null, null,
				null, 0);
		assertThat(Agenda.decidir(curto, em(SEGUNDA, 9, 0))).containsExactly(LEMBRETE_1_DIA);
	}

	@Test
	void lembreteSaiSemGestorENoFimDeSemanaMasNaoDeNoite() {
		Estado semGestor = new Estado(true, false, false, 30, 12, 30, 12, em(SEGUNDA.plusDays(1), 22, 0), null, null,
				null, null, 0);
		Instant domingo = em(SEGUNDA.minusDays(1), 10, 0);
		assertThat(Agenda.decidir(semGestor, domingo)).containsExactly(LEMBRETE_3_DIAS);
		assertThat(Agenda.decidir(semGestor, em(SEGUNDA.minusDays(1), 21, 0))).isEmpty();
	}

	@Test
	void tresFalhasNoDiaParamAsTentativasEEmailDesligadoNaoFazNada() {
		Estado falhando = new Estado(true, true, false, 30, 12, 30, 12, null, null, null, null, null, 3);
		Estado desligado = new Estado(false, true, false, 30, 30, 30, 30, null, null, null, null, null, 0);
		assertThat(Agenda.decidir(falhando, em(SEGUNDA, 9, 0))).isEmpty();
		assertThat(Agenda.decidir(desligado, em(SEGUNDA, 9, 0))).isEmpty();
	}

	@Test
	void proximoDiarioPulaOFimDeSemana() {
		Instant sextaDepoisDoEnvio = em(SEGUNDA.plusDays(4), 9, 0);
		assertThat(Agenda.proximoDiario(sextaDepoisDoEnvio, sextaDepoisDoEnvio)).isEqualTo(em(SEGUNDA.plusDays(7), 8, 0));
		assertThat(Agenda.proximoDiario(em(SEGUNDA.minusDays(2), 9, 0), null)).isEqualTo(em(SEGUNDA, 8, 0));
		assertThat(Agenda.proximoDiario(em(SEGUNDA, 7, 0), null)).isEqualTo(em(SEGUNDA, 8, 0));
	}

	@Test
	void chamadaDoLembrete() {
		assertThat(Agenda.chamada(3)).isEqualTo("Faltam 3 dias para responder");
		assertThat(Agenda.chamada(1)).isEqualTo("Amanhã é o último dia para responder");
		assertThat(Agenda.chamada(0)).isEqualTo("Hoje é o último dia para responder");
	}

}
