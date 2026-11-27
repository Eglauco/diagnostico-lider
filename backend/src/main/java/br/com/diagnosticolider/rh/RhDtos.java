package br.com.diagnosticolider.rh;

import java.time.Instant;
import java.util.List;

import br.com.diagnosticolider.acompanhamento.ResultadoDaEmpresa;
import br.com.diagnosticolider.convite.ParticipacaoAoVivo;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Destaque;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.DimensaoResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.FaixaResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Indice;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Limites;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Participacao;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PerguntaResumo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.PorCargo;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados.Referencia;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O que a página do RH (/acompanhar) troca com o servidor. */
final class RhDtos {

	private RhDtos() {
	}

	record PedirCodigo(@NotBlank(message = "Digite o seu e-mail.") @Size(max = 254) String email) {
	}

	/** Pelo código digitado (email + codigo) ou pelo botão do e-mail (link). */
	record Entrar(@Size(max = 254) String email, @Size(max = 20) String codigo, @Size(max = 100) String link) {
	}

	record Sessao(GestorDto gestor) {
	}

	record GestorDto(String email, List<AcessoRhService.EmpresaDoGestor> empresas) {

		static GestorDto de(AcessoRhService.Gestor g) {
			return new GestorDto(g.email(), g.empresas());
		}

	}

	/**
	 * A tela de uma empresa: a marca, o prazo e a participação ao vivo.
	 *
	 * @param concluidos diagnósticos concluídos (inclui quem respondeu pelo link aberto, sem convite)
	 */
	record PainelRh(MarcaEmpresa empresa, Instant encerraEm, boolean encerrada, ParticipacaoAoVivo participacao,
			int concluidos, Instant agora) {
	}

	/**
	 * O resultado do grupo para a tela. estado: AGUARDANDO (menos de 3), PARCIAL ou FINAL.
	 *
	 * @param novas               concluídos que ainda não entraram no resultado
	 * @param faltamParaAtualizar quantas respostas novas faltam para o resultado mudar (parcial)
	 * @param faltamParaAbrir     quantas faltam para o resultado abrir (aguardando)
	 */
	record ResultadoRh(String estado, int concluidos, int minimo, int respostas, Instant atualizadoEm, int novas,
			int faltamParaAtualizar, int faltamParaAbrir, Dados dados) {

		static ResultadoRh de(ResultadoDaEmpresa.Resultado r) {
			return new ResultadoRh(r.estado().name(), r.concluidos(), r.minimo(), r.respostas(), r.atualizadoEm(),
					r.novas(), r.faltamParaAtualizar(), r.faltamParaAbrir(), r.dados() == null ? null : Dados.de(r.dados()));
		}

	}

	/** Só números do grupo. pontos: um por líder, sem identificação, em ordem de índice. */
	record Dados(String situacao, String explicacao, String periodo, String questionario, String nota,
			Participacao participacao, Indice indice, Referencia referencia, FaixaResumo faixaDaMedia,
			List<FaixaResumo> faixas, List<Destaque> destaques, List<RelatorioEmpresaService.Lider> pontos,
			Limites limites, List<DimensaoResumo> dimensoes, PorCargo porCargo, List<PerguntaResumo> perguntas,
			String notaVersoes) {

		static Dados de(RelatorioEmpresaDados d) {
			return new Dados(d.situacao() == null ? null : d.situacao().rotulo(),
					d.situacao() == null ? null : d.situacao().explicacao(), d.periodo(), d.questionario(),
					d.notaAnonimato(), d.participacao(), d.indice(), d.referencia(), d.faixaDaMedia(), d.faixas(),
					d.destaques().stream().map(Dados::semPagina).toList(), d.pontos(), d.limites(),
					d.paginasDimensoes().stream().flatMap(List::stream).toList(), d.porCargo(),
					d.paginasPerguntas().stream().flatMap(List::stream).toList(), d.notaVersoes());
		}

		/** "Veja a página 3." é do PDF: na tela, a parte está logo abaixo. */
		private static Destaque semPagina(Destaque d) {
			return new Destaque(d.codigo(), d.titulo(), d.texto().replaceAll("\\s*Veja a página \\d+\\.$", ""));
		}

	}

}
