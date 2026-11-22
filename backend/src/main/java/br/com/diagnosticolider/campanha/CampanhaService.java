package br.com.diagnosticolider.campanha;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.questionario.QuestionarioDto;
import br.com.diagnosticolider.questionario.QuestionarioVersao;
import br.com.diagnosticolider.questionario.QuestionarioVersaoRepository;

@Service
@Transactional(readOnly = true)
public class CampanhaService {

	private final CampanhaRepository campanhas;

	private final QuestionarioVersaoRepository versoes;

	CampanhaService(CampanhaRepository campanhas, QuestionarioVersaoRepository versoes) {
		this.campanhas = campanhas;
		this.versoes = versoes;
	}

	public Campanha campanhaAtiva(String codigo) {
		return campanhas.findByCodigoAndAtivaTrue(codigo)
			.orElseThrow(() -> new NaoEncontradoException("Este link de diagnóstico não existe ou foi encerrado."));
	}

	public QuestionarioVersao versaoPublicada(Campanha campanha) {
		return versoes
			.findFirstByQuestionarioIdAndStatus(campanha.getQuestionario().getId(), QuestionarioVersao.Status.PUBLICADA)
			.orElseThrow(() -> new NaoEncontradoException("Este diagnóstico ainda não está disponível."));
	}

	public CampanhaPublicaDto buscarPublica(String codigo) {
		Campanha campanha = campanhaAtiva(codigo);
		QuestionarioVersao versao = versaoPublicada(campanha);
		var empresa = campanha.vinculadaAEmpresa() ? new CampanhaPublicaDto.EmpresaDto(campanha.getEmpresa().getNome())
				: null;
		return new CampanhaPublicaDto(campanha.getCodigo(), campanha.getNome(), empresa, QuestionarioDto.de(versao));
	}

}
