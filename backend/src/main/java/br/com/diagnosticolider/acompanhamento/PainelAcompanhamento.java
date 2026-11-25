package br.com.diagnosticolider.acompanhamento;

import java.time.Instant;
import java.util.List;

import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.notificacao.EmailEnviado;

/**
 * O cartão "Relatórios para o gestor" do painel: situação numa frase, o próximo envio, os
 * lembretes aos líderes e o histórico do que saiu.
 *
 * @param situacao DESLIGADO, SEM_GESTOR, AGUARDANDO, ATIVO, PAUSADO, FINAL_PENDENTE ou FINALIZADO
 */
public record PainelAcompanhamento(boolean emailAtivo, List<String> gestores, boolean pausado, String situacao,
		String titulo, String explicacao, Instant proximoEnvio, Lembretes lembretes, List<Item> historico) {

	/** Uma frase para cada lembrete aos líderes (null sem prazo: não há lembrete). */
	public record Lembretes(String tresDias, String umDia) {
	}

	/**
	 * tipo: DIARIO, FINAL ou LEMBRETE. autor: quem clicou em "Enviar agora". entrega: o que o Resend
	 * avisou (ENTREGUE, DEVOLVIDO…), se houver.
	 */
	public record Item(Long id, String tipo, boolean manual, Instant quando, String para, Integer responderam,
			Integer total, boolean enviado, String detalhe, String autor, String entrega, String entregaDetalhe) {

		static Item de(EnvioAcompanhamento e, EmailEnviado email) {
			return new Item(e.getId(), e.getTipo().name(), e.isManual(), e.getQuando(), e.getPara(), e.getResponderam(),
					e.getTotal(), e.isEnviado(), e.getDetalhe(), e.getAutor(),
					email == null ? null : email.getSituacao().name(), email == null ? null : email.getDetalhe());
		}

	}

	static PainelAcompanhamento montar(Empresa e, Agenda.Estado estado, Instant agora, Lembretes lembretes,
			List<Item> historico) {
		List<String> gestores = e.getGestores();
		int n = gestores.size();
		String quem = n == 1 ? "para 1 gestor" : "para " + n + " gestores";
		Instant proximo = null;
		String situacao;
		String titulo;
		String explicacao;
		if (!estado.emailAtivo()) {
			situacao = "DESLIGADO";
			titulo = "Envio por e-mail desligado neste servidor";
			explicacao = "Por enquanto nenhum e-mail chega aos gestores. Os relatórios automáticos começam a sair quando o envio for ligado.";
		}
		else if (n == 0) {
			situacao = "SEM_GESTOR";
			titulo = "Nenhum gestor cadastrado";
			explicacao = "Cadastre o e-mail de quem acompanha a avaliação na empresa. Ele recebe todo dia útil, às 8h, "
					+ "o relatório de participação em PDF e Excel e, no fim, o resultado consolidado.";
		}
		else if (estado.fim(agora) && e.getGestorFinalEm() != null) {
			situacao = "FINALIZADO";
			titulo = "Resultado final enviado";
			explicacao = "O relatório consolidado foi enviado " + quem + " em " + Datas.porExtenso(e.getGestorFinalEm(), agora)
					+ ". O envio diário terminou.";
		}
		else if (estado.fim(agora)) {
			situacao = "FINAL_PENDENTE";
			titulo = "Resultado final a caminho";
			explicacao = (estado.todosResponderam() ? "Todos os convidados responderam" : "O prazo terminou")
					+ ": o relatório consolidado sai " + quem + " em até 5 minutos.";
		}
		else if (!estado.comecou()) {
			situacao = "AGUARDANDO";
			titulo = "Esperando os convites saírem";
			explicacao = "O relatório diário começa no primeiro dia útil depois que os convites forem enviados, às 8h, "
					+ quem + ".";
		}
		else if (e.isGestorPausado()) {
			situacao = "PAUSADO";
			titulo = "Relatório diário pausado";
			explicacao = "Os gestores não recebem o relatório diário até você retomar. O resultado final sai mesmo assim.";
		}
		else {
			situacao = "ATIVO";
			titulo = "Relatório diário ligado";
			proximo = Agenda.proximoDiario(agora, e.getGestorDiarioEm());
			explicacao = "Sai todo dia útil às 8h " + quem + ", com o PDF e o Excel da participação. Próximo: "
					+ Datas.porExtenso(proximo, agora) + ".";
		}
		return new PainelAcompanhamento(estado.emailAtivo(), gestores, e.isGestorPausado(), situacao, titulo, explicacao,
				proximo, lembretes, historico);
	}

}
