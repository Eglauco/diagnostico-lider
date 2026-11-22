package br.com.diagnosticolider.questionario;

import java.util.List;

/** Questionário como o líder vê: sem pesos nem faixas, que ficam só no servidor. */
public record QuestionarioDto(Long versaoId, String titulo, List<DimensaoDto> dimensoes, List<PerguntaDto> perguntas) {

	public record DimensaoDto(String codigo, String nome) {
	}

	public record PerguntaDto(Long id, String codigo, String dimensao, String enunciado, String ajuda,
			Pergunta.Tipo tipo, boolean obrigatoria, List<OpcaoDto> opcoes) {
	}

	public record OpcaoDto(Long id, String texto) {
	}

	public static QuestionarioDto de(QuestionarioVersao versao) {
		var dimensoes = versao.getDimensoes().stream().map(d -> new DimensaoDto(d.getCodigo(), d.getNome())).toList();
		var perguntas = versao.getPerguntas().stream()
			.map(p -> new PerguntaDto(p.getId(), p.getCodigo(),
					p.getDimensao() == null ? null : p.getDimensao().getCodigo(), p.getEnunciado(), p.getAjuda(),
					p.getTipo(), p.isObrigatoria(),
					p.getOpcoes().stream().map(o -> new OpcaoDto(o.getId(), o.getTexto())).toList()))
			.toList();
		return new QuestionarioDto(versao.getId(), versao.getQuestionario().getTitulo(), dimensoes, perguntas);
	}

}
