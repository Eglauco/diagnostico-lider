package br.com.diagnosticolider.diagnostico;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.questionario.Opcao;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da API pública de diagnóstico. */
public final class DiagnosticoDtos {

	private DiagnosticoDtos() {
	}

	/**
	 * Cargo e tempo no cargo só valem no link de uma empresa que tem cargos cadastrados: aí são
	 * obrigatórios. "Outro cargo": cargoOutro = true, sem cargoId, e o nome (opcional) em cargoOutroNome.
	 */
	public record IniciarRequest(
			@NotBlank(message = "Informe seu nome.") @Size(max = 160, message = "Use até 160 caracteres.") String nome,
			@NotBlank(message = "Informe seu e-mail.") @Email(message = "Informe um e-mail válido.")
			@Size(max = 254, message = "E-mail muito longo.") String email,
			@NotBlank(message = "Informe seu WhatsApp.") @Size(max = 20, message = "WhatsApp inválido.") String whatsapp,
			Long cargoId, Boolean cargoOutro,
			@Size(max = 80, message = "Use até 80 caracteres no nome do cargo.") String cargoOutroNome,
			TempoCargo tempoCargo) {
	}

	public record ResponderRequest(@NotEmpty(message = "Escolha uma opção.") List<@NotNull Long> opcoes) {
	}

	/** Estado de um diagnóstico em andamento, para o líder retomar de onde parou. */
	public record DiagnosticoDto(UUID id, String campanha, StatusDiagnostico status, String primeiroNome,
			Map<Long, List<Long>> respostas) {

		static DiagnosticoDto de(Diagnostico d) {
			Map<Long, List<Long>> respostas = new LinkedHashMap<>();
			d.respostasPorPergunta()
				.forEach((pergunta, opcoes) -> respostas.put(pergunta, opcoes.stream().map(Opcao::getId).toList()));
			return new DiagnosticoDto(d.getId(), d.getCampanha().getCodigo(), d.getStatus(),
					d.getRespondente().primeiroNome(), respostas);
		}

	}

	/** "empresa" (com a paleta) vem quando o diagnóstico foi feito pelo link de uma empresa. */
	public record ResultadoDto(UUID id, String nome, String primeiroNome, Instant concluidoEm, int pontuacao,
			FaixaDto faixa, List<DimensaoDto> dimensoes, String emailMascarado, StatusEmail statusEmail,
			MarcaEmpresa empresa) {

		public record FaixaDto(String codigo, String titulo, String resumo, String descricao,
				List<String> recomendacoes) {
		}

		public record DimensaoDto(String codigo, String nome, String descricao, int pontuacao) {
		}

		static ResultadoDto de(Diagnostico d, MarcaEmpresa empresa) {
			var f = d.getFaixa();
			var faixa = new FaixaDto(f.getCodigo(), f.getTitulo(), f.getResumo(), f.getDescricao(),
					List.copyOf(f.getRecomendacoes()));
			var dimensoes = d.dimensoesOrdenadas()
				.stream()
				.map(dd -> new DimensaoDto(dd.getDimensao().getCodigo(), dd.getDimensao().getNome(),
						dd.getDimensao().getDescricao(), dd.getPontuacao()))
				.toList();
			var r = d.getRespondente();
			return new ResultadoDto(d.getId(), r.getNome(), r.primeiroNome(), d.getConcluidoEm(), d.getPontuacao(),
					faixa, dimensoes, r.emailMascarado(), d.getStatusEmail(), empresa);
		}

	}

}
