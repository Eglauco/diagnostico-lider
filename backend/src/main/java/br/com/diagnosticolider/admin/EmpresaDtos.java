package br.com.diagnosticolider.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.com.diagnosticolider.empresa.CargoService;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.empresa.PaletaMarca;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos da API de empresas do painel. */
public final class EmpresaDtos {

	private EmpresaDtos() {
	}

	/**
	 * O código é opcional: sem ele, sai do nome da empresa ("Grupo Ação" → grupo-acao). As cores
	 * também: sem elas, vale a identidade do Diagnóstico do Líder.
	 */
	public record NovaEmpresa(
			@NotBlank(message = "Escreva o nome da empresa.") @Size(max = 160, message = "Use até 160 caracteres.") String nome,
			@Size(max = 60, message = "Use até 60 caracteres.") String codigo,
			@Size(max = 7, message = "Use um código de cor como #0057B8.") String corPrimaria,
			@Size(max = 7, message = "Use um código de cor como #0057B8.") String corSecundaria,
			List<CargoService.Pedido> cargos, Instant encerraEm, List<String> gestores) {
	}

	/**
	 * O link não muda na edição: ele pode já estar impresso nos materiais da empresa. Os cargos vêm
	 * na ordem da hierarquia (o primeiro é o primeiro nível de liderança). encerraEm: quando a
	 * avaliação encerra (null: sem data). gestores: e-mails que recebem os relatórios (pode ser vazio).
	 */
	public record EditarEmpresa(
			@NotBlank(message = "Escreva o nome da empresa.") @Size(max = 160, message = "Use até 160 caracteres.") String nome,
			@Size(max = 7, message = "Use um código de cor como #0057B8.") String corPrimaria,
			@Size(max = 7, message = "Use um código de cor como #0057B8.") String corSecundaria,
			List<CargoService.Pedido> cargos, Instant encerraEm, List<String> gestores) {
	}

	/** O link que vai para os líderes da empresa; paginaRh: onde os gestores acompanham (/acompanhar/{codigo}). */
	public record Link(String codigo, String url, boolean ativo, String paginaRh) {
	}

	/** Cores da marca como foram cadastradas (null: identidade do Diagnóstico). */
	public record Cores(String primaria, String secundaria) {
	}

	/**
	 * Um cartão da carteira de empresas. diagnosticos/concluidos: respostas recebidas (tentativas
	 * ativas). lideres/lideresConcluiram: o cadastro de líderes e quantos dele já concluíram (o
	 * progresso da avaliação). indiceMedio: dos concluídos, sem arredondar (a média da carteira pondera).
	 */
	public record EmpresaResumo(Long id, String nome, Instant criadaEm, Link link, long diagnosticos, long concluidos,
			Instant ultimoDiagnostico, Cores cores, MarcaEmpresa.Logo logo, Encerramento encerramento, int lideres,
			int lideresConcluiram, Double indiceMedio) {
	}

	/**
	 * minimoRelatorio: diagnósticos concluídos necessários para gerar o relatório sem identificar ninguém.
	 * tema: a paleta pronta (usada no cartão do QR Code e na prévia).
	 * cargos: os que o líder escolhe, na ordem da hierarquia, com quantos diagnósticos cada um já tem.
	 * fundo: a foto de fundo da página do RH (null sem foto).
	 */
	public record EmpresaDetalhe(Long id, String nome, Instant criadaEm, Link link, Indicadores indicadores,
			int minimoRelatorio, Cores cores, PaletaMarca.Tema tema, MarcaEmpresa.Logo logo,
			List<CargoService.Item> cargos, Encerramento encerramento, List<String> gestores,
			MarcaEmpresa.Foto fundo, MarcaEmpresa.Foto capa) {
	}

	/** O ponto da foto de fundo que fica sempre à vista (0 a 100, da esquerda e do topo). */
	public record FocoFundo(@Min(0) @Max(100) int x, @Min(0) @Max(100) int y) {
	}

	/** Até quando a avaliação vai (em: null sem data) e se já encerrou. */
	public record Encerramento(Instant em, boolean encerrada) {
	}

	/**
	 * total: diagnósticos começados. lideres: o cadastro de líderes da avaliação (convidados, que vieram
	 * da planilha ou do cadastro manual, e quem começou pelo link aberto), com quem ainda não começou.
	 * lideresConcluiram: quantos desse cadastro já concluíram ("24 de 29 responderam").
	 */
	public record Indicadores(long total, long concluidos, long emAndamento, Integer indiceMedio,
			Map<String, Long> porFaixa, int lideres, int convidados, int peloLink, int lideresConcluiram) {
	}

	/** Resposta da verificação do endereço enquanto o administrador digita. */
	public record Disponibilidade(String codigo, String url, boolean valido, boolean disponivel, String sugestao) {
	}

	/** Prévia das cores no cadastro: a paleta gerada e o que precisou ser ajustado. */
	public record PreviaTema(PaletaMarca.Tema tema, List<String> ajustes) {
	}

}
