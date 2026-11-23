package br.com.diagnosticolider.campanha;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.diagnostico.TempoCargo;
import br.com.diagnosticolider.empresa.Cargo;
import br.com.diagnosticolider.empresa.CargoService;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.questionario.QuestionarioDto;
import br.com.diagnosticolider.questionario.QuestionarioVersao;
import br.com.diagnosticolider.questionario.QuestionarioVersaoRepository;

@Service
@Transactional(readOnly = true)
public class CampanhaService {

	private final CampanhaRepository campanhas;

	private final QuestionarioVersaoRepository versoes;

	private final LogomarcaService logomarcas;

	private final CargoService cargos;

	CampanhaService(CampanhaRepository campanhas, QuestionarioVersaoRepository versoes, LogomarcaService logomarcas,
			CargoService cargos) {
		this.campanhas = campanhas;
		this.versoes = versoes;
		this.logomarcas = logomarcas;
		this.cargos = cargos;
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
		return new CampanhaPublicaDto(campanha.getCodigo(), campanha.getNome(), logomarcas.marca(campanha.getEmpresa()),
				perfil(campanha), QuestionarioDto.de(versao));
	}

	/** Cargos ativos da empresa do link (vazio no link geral e em empresas sem cargos). */
	public List<Cargo> cargosDoLink(Campanha campanha) {
		return campanha.vinculadaAEmpresa() ? cargos.ativos(campanha.getEmpresa().getId()) : List.of();
	}

	private CampanhaPublicaDto.Perfil perfil(Campanha campanha) {
		List<Cargo> lista = cargosDoLink(campanha);
		if (lista.isEmpty()) {
			return null;
		}
		return new CampanhaPublicaDto.Perfil(
				lista.stream().map(c -> new CampanhaPublicaDto.Opcao(String.valueOf(c.getId()), c.getNome())).toList(),
				Arrays.stream(TempoCargo.values()).map(t -> new CampanhaPublicaDto.Opcao(t.name(), t.texto())).toList());
	}

}
