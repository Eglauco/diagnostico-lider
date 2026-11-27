package br.com.diagnosticolider.acompanhamento;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.convite.ParticipacaoService;
import br.com.diagnosticolider.convite.ParticipacaoService.ResumoParticipacao;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService.ReferenciaGeral;

/**
 * O resultado que a empresa vê: a tela do RH, o PDF consolidado (do painel ou do e-mail final).
 * Sai sempre da última foto ({@link RegraDaFoto}); a foto nova é tirada aqui, na hora em que
 * alguém pede o resultado e a regra deixa.
 */
@Service
public class ResultadoDaEmpresa {

	public enum Estado {
		/** Menos de 3 concluídos e nenhuma foto ainda. */
		AGUARDANDO,
		/** A avaliação está aberta: índice, faixas e dimensões, em lotes de 3 respostas. */
		PARCIAL,
		/** Prazo vencido ou todos os convidados responderam: também cargos e perguntas. */
		FINAL
	}

	/**
	 * @param concluidos   concluídos ativos agora (a participação, que o RH já vê)
	 * @param respostas    quantos estão no resultado (a foto)
	 * @param atualizadoEm quando a foto foi tirada
	 * @param novas        concluídos que ainda não entraram no resultado
	 * @param dados        o relatório da foto (null aguardando)
	 */
	public record Resultado(Estado estado, int concluidos, int minimo, int respostas, Instant atualizadoEm, int novas,
			RelatorioEmpresaDados dados) {

		/** Quantas respostas novas ainda faltam para o resultado mudar (só no parcial). */
		public int faltamParaAtualizar() {
			return estado == Estado.PARCIAL ? Math.max(0, minimo - novas) : 0;
		}

		/** Quantas faltam para o resultado abrir (só aguardando). */
		public int faltamParaAbrir() {
			return estado == Estado.AGUARDANDO ? Math.max(0, minimo - concluidos) : 0;
		}

	}

	private final EmpresaRepository empresas;

	private final DiagnosticoRepository diagnosticos;

	private final FotoResultadoRepository fotos;

	private final RelatorioEmpresaService relatorios;

	private final ParticipacaoService participacao;

	private final Clock clock;

	ResultadoDaEmpresa(EmpresaRepository empresas, DiagnosticoRepository diagnosticos, FotoResultadoRepository fotos,
			RelatorioEmpresaService relatorios, ParticipacaoService participacao, Clock clock) {
		this.empresas = empresas;
		this.diagnosticos = diagnosticos;
		this.fotos = fotos;
		this.relatorios = relatorios;
		this.participacao = participacao;
		this.clock = clock;
	}

	/** O resultado de agora (tira uma foto nova se a regra deixar). */
	// Transação própria: a foto é gravada (e a empresa travada) mesmo se quem chama estiver numa
	// transação só de leitura.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Resultado atual(Long empresaId) {
		Instant agora = clock.instant();
		// Duas telas pedindo ao mesmo tempo não tiram duas fotos.
		Empresa empresa = empresas.travar(empresaId)
			.orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
		ResumoParticipacao r = participacao.resumo(empresaId);
		boolean todosResponderam = r.convidados() > 0 && r.todosResponderam();
		boolean fimAgora = empresa.encerrada(agora) || todosResponderam;

		Set<UUID> concluidos = new HashSet<>(diagnosticos.concluidosAtivos(empresaId));
		FotoResultado ultima = fotos.findFirstByEmpresaIdOrderByTiradaEmDescIdDesc(empresaId).orElse(null);

		FotoResultado foto = switch (RegraDaFoto.decidir(concluidos, ultima == null ? null : ultima.getDiagnosticos(),
				ultima != null && ultima.isFim(), fimAgora)) {
			case RegraDaFoto.Aguardar a -> null;
			case RegraDaFoto.Manter m -> ultima;
			case RegraDaFoto.Nova n when n.mesmaBase() -> fotos.save(ultima.comFim(n.fim(), agora));
			case RegraDaFoto.Nova n -> {
				ReferenciaGeral geral = relatorios.referenciaAgora();
				yield fotos.save(new FotoResultado(empresaId, agora, n.fim(), n.diagnosticos(), geral.media(),
						geral.lideres(), geral.dimensoes()));
			}
		};
		if (foto == null) {
			return new Resultado(Estado.AGUARDANDO, concluidos.size(), RegraDaFoto.LOTE, 0, null, 0, null);
		}

		int novas = RegraDaFoto.novas(concluidos, foto.getDiagnosticos());
		List<Diagnostico> base = diagnosticos.findByIdIn(foto.getDiagnosticos());
		RelatorioEmpresaDados.Situacao situacao = foto.isFim() && todosResponderam && !empresa.encerrada(agora)
				? RelatorioEmpresaService.todosResponderam(r.total()) : null;
		var referencia = foto.getReferenciaMedia() == null ? null
				: new ReferenciaGeral(foto.getReferenciaMedia(), foto.getReferenciaLideres(),
						Map.copyOf(foto.getReferenciaDimensoes()));
		RelatorioEmpresaDados dados = relatorios.montar(empresaId, base, referencia, foto.isFim(), situacao,
				nota(foto.isFim(), novas));
		return new Resultado(foto.isFim() ? Estado.FINAL : Estado.PARCIAL, concluidos.size(), RegraDaFoto.LOTE,
				base.size(), foto.getTiradaEm(), novas, dados);
	}

	/** O relatório consolidado (PDF); sem o mínimo de concluídos, explica quantos faltam (409). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public RelatorioEmpresaDados relatorio(Long empresaId) {
		Resultado r = atual(empresaId);
		if (r.dados() == null) {
			int n = r.concluidos();
			throw RegraNegocioException.conflito(("O relatório consolidado precisa de pelo menos %d diagnósticos "
					+ "concluídos, para que ninguém seja identificado. Hoje %s.")
				.formatted(r.minimo(), n == 0 ? "não há nenhum" : n == 1 ? "há 1" : "há " + n));
		}
		return r.dados();
	}

	/** O que o relatório explica sobre o anonimato (a tela do RH diz o mesmo, do jeito dela). */
	static String nota(boolean fim, int novas) {
		if (!fim) {
			return "Enquanto a avaliação está aberta, este resultado só muda quando chegam pelo menos "
					+ RegraDaFoto.LOTE + " respostas novas, para ninguém deduzir a resposta de quem acabou de terminar. "
					+ "A análise por cargo e por pergunta entra no resultado final.";
		}
		if (novas == 0) {
			return null;
		}
		return (novas == 1 ? "1 resposta chegou depois da última atualização e ficou de fora"
				: novas + " respostas chegaram depois da última atualização e ficaram de fora")
				+ ": com menos de " + RegraDaFoto.LOTE + ", daria para saber de quem " + (novas == 1 ? "era." : "eram.");
	}

}
