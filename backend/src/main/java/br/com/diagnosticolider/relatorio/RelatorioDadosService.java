package br.com.diagnosticolider.relatorio;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.questionario.Opcao;

@Service
@Transactional(readOnly = true)
public class RelatorioDadosService {

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR)
		.withZone(ZoneId.of("America/Sao_Paulo"));

	/** Tamanho do SVG da curva no PDF, em px CSS (A4 útil ≈ 660px de largura). */
	private static final double LARGURA_CURVA = 660;

	private static final double ALTURA_CURVA = 250;

	private final DiagnosticoRepository diagnosticos;

	private final AppProperties props;

	RelatorioDadosService(DiagnosticoRepository diagnosticos, AppProperties props) {
		this.diagnosticos = diagnosticos;
		this.props = props;
	}

	public RelatorioDados montar(UUID id) {
		Diagnostico d = diagnosticos.findById(id)
			.orElseThrow(() -> new NaoEncontradoException("Não encontramos este diagnóstico."));
		if (!d.concluido()) {
			throw RegraNegocioException.conflito("O relatório fica disponível quando o diagnóstico é concluído.");
		}

		var f = d.getFaixa();
		var faixa = new RelatorioDados.Faixa(f.getCodigo(), f.getTitulo(), f.getResumo(), f.getDescricao(),
				List.copyOf(f.getRecomendacoes()));

		var dimensoes = d.dimensoesOrdenadas()
			.stream()
			.map(dd -> new RelatorioDados.Dimensao(dd.getDimensao().getNome(), dd.getDimensao().getDescricao(),
					dd.getPontuacao()))
			.toList();

		Map<Long, List<Opcao>> escolhas = d.respostasPorPergunta();
		var respostas = d.getVersao()
			.getPerguntas()
			.stream()
			.filter(p -> escolhas.containsKey(p.getId()))
			.map(p -> new RelatorioDados.RespostaItem(p.getDimensao() == null ? null : p.getDimensao().getNome(),
					p.getEnunciado(),
					escolhas.get(p.getId()).stream().map(Opcao::getTexto).collect(Collectors.joining(" · "))))
			.toList();

		var r = d.getRespondente();
		String url = props.urlPublica().replaceAll("/+$", "") + "/resultado/" + d.getId();
		return new RelatorioDados(d.getId(), r.getNome(), r.primeiroNome(), r.getEmail(),
				DATA.format(d.getConcluidoEm()), d.getPontuacao(), faixa, dimensoes, respostas,
				CurvaCarreira.desenhar(LARGURA_CURVA, ALTURA_CURVA, d.getPontuacao()), url);
	}

}
