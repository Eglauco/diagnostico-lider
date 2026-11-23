package br.com.diagnosticolider.diagnostico;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.campanha.CampanhaService;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.DiagnosticoDto;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.IniciarRequest;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.ResultadoDto;
import br.com.diagnosticolider.empresa.Cargo;
import br.com.diagnosticolider.empresa.CargoService;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.questionario.Dimensao;
import br.com.diagnosticolider.questionario.Opcao;
import br.com.diagnosticolider.questionario.Pergunta;
import br.com.diagnosticolider.questionario.QuestionarioVersao;

@Service
@Transactional
public class DiagnosticoService {

	private final DiagnosticoRepository diagnosticos;

	private final CampanhaService campanhas;

	private final CalculadoraResultado calculadora;

	private final ApplicationEventPublisher eventos;

	private final Clock clock;

	private final LogomarcaService logomarcas;

	DiagnosticoService(DiagnosticoRepository diagnosticos, CampanhaService campanhas, CalculadoraResultado calculadora,
			ApplicationEventPublisher eventos, Clock clock, LogomarcaService logomarcas) {
		this.diagnosticos = diagnosticos;
		this.campanhas = campanhas;
		this.calculadora = calculadora;
		this.eventos = eventos;
		this.clock = clock;
		this.logomarcas = logomarcas;
	}

	public DiagnosticoDto iniciar(String codigoCampanha, IniciarRequest req) {
		Campanha campanha = campanhas.campanhaAtiva(codigoCampanha);
		QuestionarioVersao versao = campanhas.versaoPublicada(campanha);
		PerfilCargo perfil = perfil(campanha, req);
		Diagnostico diagnostico = Diagnostico.iniciar(campanha, versao, respondente(campanha, req), agora());
		diagnostico.definirPerfil(perfil);
		return DiagnosticoDto.de(diagnosticos.save(diagnostico));
	}

	public DiagnosticoDto atualizarRespondente(UUID id, IniciarRequest req) {
		Diagnostico diagnostico = carregar(id);
		diagnostico.atualizarRespondente(respondente(diagnostico.getCampanha(), req));
		diagnostico.definirPerfil(perfil(diagnostico.getCampanha(), req));
		return DiagnosticoDto.de(diagnostico);
	}

	/**
	 * Empresa com cargos cadastrados: cargo (ou "Outro cargo") e tempo no cargo são obrigatórios.
	 * Link geral ou empresa sem cargos: nada é perguntado, e o que vier é ignorado.
	 */
	private PerfilCargo perfil(Campanha campanha, IniciarRequest req) {
		List<Cargo> cargos = campanhas.cargosDoLink(campanha);
		if (cargos.isEmpty()) {
			return PerfilCargo.NENHUM;
		}
		boolean outro = Boolean.TRUE.equals(req.cargoOutro());
		Cargo cargo = null;
		if (!outro) {
			if (req.cargoId() == null) {
				throw new RegraNegocioException("Escolha o seu cargo para continuar.").noCampo("cargo");
			}
			cargo = cargos.stream()
				.filter(c -> c.getId().equals(req.cargoId()))
				.findFirst()
				.orElseThrow(() -> new RegraNegocioException("Esse cargo saiu da lista. Escolha de novo.").noCampo("cargo"));
		}
		if (req.tempoCargo() == null) {
			throw new RegraNegocioException("Escolha há quanto tempo você está no cargo.").noCampo("tempoCargo");
		}
		String outroNome = outro ? CargoService.normalizar(req.cargoOutroNome()) : "";
		return new PerfilCargo(cargo, outro, outroNome.isEmpty() ? null : outroNome, req.tempoCargo());
	}

	/** Link de empresa: a empresa vem da campanha. Link geral: não pedimos empresa ao líder. */
	private static Respondente respondente(Campanha campanha, IniciarRequest req) {
		String empresa = campanha.vinculadaAEmpresa() ? campanha.getEmpresa().getNome() : null;
		return Respondente.de(req.nome(), req.email(), req.whatsapp(), empresa);
	}

	@Transactional(readOnly = true)
	public DiagnosticoDto buscar(UUID id) {
		return DiagnosticoDto.de(carregar(id));
	}

	public void responder(UUID id, Long perguntaId, List<Long> opcaoIds) {
		Diagnostico diagnostico = carregar(id);
		Pergunta pergunta = diagnostico.getVersao()
			.pergunta(perguntaId)
			.orElseThrow(() -> new NaoEncontradoException("Esta pergunta não faz parte do seu diagnóstico."));

		Map<Long, Opcao> opcoesDaPergunta = pergunta.getOpcoes()
			.stream()
			.collect(Collectors.toMap(Opcao::getId, Function.identity()));
		List<Opcao> escolhidas = opcaoIds.stream().distinct().map(opcoesDaPergunta::get).toList();
		if (escolhidas.contains(null)) {
			throw new RegraNegocioException("Opção inválida para esta pergunta.");
		}
		if (pergunta.getTipo() == Pergunta.Tipo.ESCOLHA_UNICA && escolhidas.size() != 1) {
			throw new RegraNegocioException("Escolha apenas uma opção.");
		}
		diagnostico.responder(pergunta, escolhidas, agora());
	}

	/** Calcula e grava o resultado. Idempotente: concluir de novo devolve o mesmo resultado. */
	public ResultadoDto finalizar(UUID id) {
		Diagnostico diagnostico = carregar(id);
		if (diagnostico.concluido()) {
			return ResultadoDto.de(diagnostico, logomarcas.marca(diagnostico.getEmpresa()));
		}

		QuestionarioVersao versao = diagnostico.getVersao();
		Map<Long, List<Opcao>> respostas = diagnostico.respostasPorPergunta();
		List<Long> pendentes = versao.getPerguntas()
			.stream()
			.filter(p -> p.isObrigatoria() && !respostas.containsKey(p.getId()))
			.map(Pergunta::getId)
			.toList();
		if (!pendentes.isEmpty()) {
			var erro = new RegraNegocioException(pendentes.size() == 1 ? "Ainda falta responder 1 pergunta."
					: "Ainda faltam responder %d perguntas.".formatted(pendentes.size()));
			erro.getBody().setProperty("pendentes", pendentes);
			throw erro;
		}

		List<CalculadoraResultado.Item> itens = versao.getPerguntas()
			.stream()
			.filter(p -> respostas.containsKey(p.getId()))
			.map(p -> new CalculadoraResultado.Item(p.getDimensao() == null ? null : p.getDimensao().getCodigo(),
					p.pontuacaoMinima(), p.pontuacaoMaxima(),
					respostas.get(p.getId()).stream().mapToInt(Opcao::getPontos).sum()))
			.toList();
		CalculadoraResultado.Calculo calculo = calculadora.calcular(itens);

		Map<Dimensao, Integer> porDimensao = new LinkedHashMap<>();
		versao.getDimensoes().forEach(d -> {
			Integer valor = calculo.porDimensao().get(d.getCodigo());
			if (valor != null) {
				porDimensao.put(d, valor);
			}
		});

		diagnostico.concluir(calculo.geral(), versao.faixaPara(calculo.geral()), porDimensao, agora());
		eventos.publishEvent(new DiagnosticoConcluido(diagnostico.getId()));
		return ResultadoDto.de(diagnostico, logomarcas.marca(diagnostico.getEmpresa()));
	}

	@Transactional(readOnly = true)
	public ResultadoDto resultado(UUID id) {
		Diagnostico diagnostico = carregar(id);
		if (!diagnostico.concluido()) {
			throw RegraNegocioException.conflito("Este diagnóstico ainda não foi concluído.");
		}
		return ResultadoDto.de(diagnostico, logomarcas.marca(diagnostico.getEmpresa()));
	}

	private Diagnostico carregar(UUID id) {
		return diagnosticos.findById(id)
			.orElseThrow(() -> new NaoEncontradoException("Não encontramos este diagnóstico."));
	}

	private Instant agora() {
		return Instant.now(clock);
	}

}
