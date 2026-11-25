package br.com.diagnosticolider.convite;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.diagnosticolider.campanha.CampanhaPublicaDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Contratos da API de convites (painel e link pessoal do líder). */
public final class ConviteDtos {

	private ConviteDtos() {
	}

	public record CargoOpcao(Long id, String nome) {
	}

	/**
	 * Uma linha lida da planilha, já com o que o servidor sabe: o cargo que bateu (ou a sugestão
	 * mais parecida), se o e-mail já tem convite e se é a linha de exemplo do modelo. A revisão no
	 * painel valida o resto enquanto a pessoa edita.
	 */
	public record LinhaImportada(int linha, String nome, String email, String whatsapp, String cargoTexto,
			Long cargoId, boolean cargoOutro, Long sugestaoCargoId, boolean jaConvidado,
			boolean exemplo) {
	}

	public record Importacao(String arquivo, List<LinhaImportada> linhas, List<CargoOpcao> cargos) {
	}

	/** Um líder a convidar (ou os dados corrigidos de um convite). */
	public record NovoConvite(String nome, String email, String whatsapp, Long cargoId, boolean cargoOutro) {
	}

	public record CriarConvites(
			@NotEmpty(message = "Inclua pelo menos um líder.") @Size(max = PlanilhaConvites.MAXIMO_LINHAS,
					message = "Use até 2000 líderes por vez.") List<@Valid NovoConvite> convites,
			String origem) {

		/** De onde vieram: "planilha" (importação) ou "painel" (um líder adicionado à mão). */
		public boolean daPlanilha() {
			return !"painel".equals(origem);
		}

	}

	public record Criados(int criados) {
	}

	public record ConviteItem(Long id, String nome, String email, String whatsapp, Long cargoId, boolean cargoOutro,
			String cargo, StatusConvite status, Instant criadoEm, Instant enviadoEm, int envios,
			Instant ultimoAcessoEm, int acessos, Instant concluidoEm, String link, UUID diagnosticoId, String falhaEnvio) {
	}

	/** O funil: cada número conta quem chegou pelo menos até ali (inativos à parte, fora dos cálculos). */
	public record Resumo(int total, int aguardandoEnvio, int enviados, int abriram, int comecaram, int concluiram,
			int inativos) {
	}

	/**
	 * emailAtivo: há provedor de e-mail configurado (sem ele, o disparo fica desligado).
	 * encerraEm/encerrada: o encerramento da avaliação da empresa.
	 */
	public record Convites(boolean emailAtivo, Instant encerraEm, boolean encerrada, Resumo resumo,
			List<ConviteItem> convites, List<CargoOpcao> cargos) {
	}

	/**
	 * Uma linha da linha do tempo. autor: quem fez no painel (null: o líder ou o sistema).
	 * email: a entrega do e-mail deste evento (convite, lembrete, resultado), se houver.
	 */
	public record Evento(TipoEvento tipo, Instant quando, String detalhe, String autor, Entrega email) {
	}

	/** situacao: ENVIADO, ENTREGUE, ATRASADO, DEVOLVIDO, SPAM, BLOQUEADO ou FALHOU (avisos do Resend). */
	public record Entrega(String situacao, String para, Instant entregueEm, Instant atualizadoEm, String detalhe) {
	}

	/** ids vazio: todos os convites que ainda não foram enviados. */
	public record Envio(List<Long> ids) {
	}

	public record EnvioIniciado(int enviando) {
	}

	/** O convite de um líder saiu agora, para este endereço. */
	public record EnvioFeito(String para) {
	}

	/**
	 * O que o link pessoal mostra: os dados para o líder confirmar, o link da empresa (marca,
	 * cargos, questionário e encerramento) e o diagnóstico, se ele já começou.
	 * cargo: id do cargo, "outro" ou null.
	 */
	public record ConvitePublico(String nome, String email, String whatsapp, String cargo,
			CampanhaPublicaDto campanha, UUID diagnosticoId) {
	}

	public record Acesso(String dispositivo) {
	}

}
