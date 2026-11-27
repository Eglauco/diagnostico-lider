package br.com.diagnosticolider.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.admin.EmpresaDtos.Cores;
import br.com.diagnosticolider.admin.EmpresaDtos.Disponibilidade;
import br.com.diagnosticolider.admin.EmpresaDtos.EditarEmpresa;
import br.com.diagnosticolider.admin.EmpresaDtos.EmpresaDetalhe;
import br.com.diagnosticolider.admin.EmpresaDtos.EmpresaResumo;
import br.com.diagnosticolider.admin.EmpresaDtos.Encerramento;
import br.com.diagnosticolider.admin.EmpresaDtos.Indicadores;
import br.com.diagnosticolider.admin.EmpresaDtos.Link;
import br.com.diagnosticolider.admin.EmpresaDtos.NovaEmpresa;
import br.com.diagnosticolider.admin.EmpresaDtos.PreviaTema;
import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.campanha.CampanhaRepository;
import br.com.diagnosticolider.campanha.CodigoCampanha;
import br.com.diagnosticolider.compartilhado.Emails;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.diagnostico.ContagemPorEmpresa;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.convite.ConviteService;
import br.com.diagnosticolider.empresa.CargoService;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.empresa.PaletaMarca;
import br.com.diagnosticolider.questionario.Questionario;
import br.com.diagnosticolider.questionario.QuestionarioRepository;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService;

/** Cadastro de empresas: cada empresa nasce com o seu link (/c/{codigo}) já vinculado. */
@Service
@Transactional(readOnly = true)
public class AdminEmpresaService {

	/** Sufixos tentados (-2, -3…) antes de desistir de sugerir um endereço livre. */
	private static final int MAXIMO_SUFIXOS = 50;

	static final int MAXIMO_GESTORES = 20;

	/** A mesma regra dos convites: algo@dominio.algo, sem espaços. */
	private final EmpresaRepository empresas;

	private final CampanhaRepository campanhas;

	private final QuestionarioRepository questionarios;

	private final DiagnosticoRepository diagnosticos;

	private final String urlPublica;

	private final LogomarcaService logomarcas;

	private final CargoService cargos;

	private final Clock clock;

	private final ConviteService convites;

	AdminEmpresaService(EmpresaRepository empresas, CampanhaRepository campanhas, QuestionarioRepository questionarios,
			DiagnosticoRepository diagnosticos, AppProperties propriedades, LogomarcaService logomarcas,
			CargoService cargos, Clock clock, ConviteService convites) {
		this.clock = clock;
		this.convites = convites;
		this.empresas = empresas;
		this.campanhas = campanhas;
		this.questionarios = questionarios;
		this.diagnosticos = diagnosticos;
		this.urlPublica = propriedades.urlPublica().replaceAll("/+$", "");
		this.logomarcas = logomarcas;
		this.cargos = cargos;
	}

	public List<EmpresaResumo> listar() {
		List<Empresa> todas = empresas.findAllByOrderByCriadoEmDesc();
		Map<Long, Campanha> links = linksPrincipais(todas.stream().map(Empresa::getId).toList());
		Map<Long, ContagemPorEmpresa> contagens = diagnosticos.contagemPorEmpresa()
			.stream()
			.collect(Collectors.toMap(ContagemPorEmpresa::empresa, Function.identity()));
		return todas.stream().map(e -> {
			var contagem = contagens.get(e.getId());
			return new EmpresaResumo(e.getId(), e.getNome(), e.getCriadoEm(), link(links.get(e.getId())),
					contagem == null ? 0 : contagem.total(), contagem == null ? 0 : contagem.concluidos(),
					contagem == null ? null : contagem.ultimo(), cores(e), logomarcas.logo(e), encerramento(e));
		}).toList();
	}

	public EmpresaDetalhe detalhe(Long id) {
		Empresa empresa = empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
		var resumo = Resumo.de(diagnosticos.resumoDaEmpresa(id));
		var lideres = convites.lideresPorOrigem(id);
		var indicadores = new Indicadores(resumo.total(), resumo.concluidos(), resumo.emAndamento(),
				resumo.indiceMedio(), resumo.porFaixa(), lideres.total(), lideres.convidados(), lideres.peloLink());
		return new EmpresaDetalhe(empresa.getId(), empresa.getNome(), empresa.getCriadoEm(),
				link(linksPrincipais(List.of(id)).get(id)), indicadores, RelatorioEmpresaService.MINIMO_LIDERES,
				cores(empresa), empresa.tema(), logomarcas.logo(empresa), cargos.itens(id), encerramento(empresa),
				empresa.getGestores());
	}

	private Encerramento encerramento(Empresa empresa) {
		return new Encerramento(empresa.getEncerraEm(), empresa.encerrada(Instant.now(clock)));
	}

	/** Edita nome, cores e cargos. O link (código) não muda: pode já estar impresso nos materiais. */
	@Transactional
	public EmpresaDetalhe editar(Long id, EditarEmpresa pedido) {
		Empresa empresa = empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
		String nome = pedido.nome().strip().replaceAll("\\s+", " ");
		if (empresas.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
			throw RegraNegocioException.conflito("Já existe uma empresa com esse nome.").noCampo("nome");
		}
		String[] cores = validarCores(pedido.corPrimaria(), pedido.corSecundaria());
		String nomeAnterior = empresa.getNome();
		empresa.renomear(nome);
		empresa.definirCores(cores[0], cores[1]);
		empresa.definirEncerramento(pedido.encerraEm());
		empresa.definirGestores(gestores(pedido.gestores()));
		this.cargos.definir(empresa, pedido.cargos());
		// O link da empresa leva o nome dela (aparece nos filtros do painel): acompanha a troca.
		campanhas.findByEmpresaIdInOrderByIdAsc(List.of(id))
			.stream()
			.filter(c -> c.getNome().equals(nomeAnterior))
			.forEach(c -> c.renomear(nome));
		return detalhe(id);
	}

	/** Prévia das cores enquanto o administrador escolhe (não grava nada). */
	public PreviaTema previa(String primaria, String secundaria) {
		String[] cores = validarCores(primaria, secundaria);
		var resultado = PaletaMarca.gerar(cores[0], cores[1]);
		return new PreviaTema(resultado.tema(), resultado.ajustes());
	}

	private static Cores cores(Empresa e) {
		return new Cores(e.getCorPrimaria(), e.getCorSecundaria());
	}

	/** Cor vazia vale "sem cor"; cor inválida vira erro no campo. Secundária pede a principal. */
	private static String[] validarCores(String primaria, String secundaria) {
		String p = vazio(primaria) ? null : PaletaMarca.normalizar(primaria);
		if (!vazio(primaria) && p == null) {
			throw new RegraNegocioException("Use um código de cor como #0057B8.").noCampo("corPrimaria");
		}
		String s = vazio(secundaria) ? null : PaletaMarca.normalizar(secundaria);
		if (!vazio(secundaria) && s == null) {
			throw new RegraNegocioException("Use um código de cor como #FF6F00.").noCampo("corSecundaria");
		}
		if (p == null && s != null) {
			throw new RegraNegocioException("Escolha a cor principal antes da secundária.").noCampo("corPrimaria");
		}
		return new String[] { p, s };
	}

	/**
	 * Os e-mails dos gestores como serão usados: sem espaços, em minúsculas, sem repetir e sem as
	 * linhas vazias. Um inválido vira erro na linha dele (campo "gestores.N").
	 */
	static List<String> gestores(List<String> emails) {
		if (emails == null) {
			return List.of();
		}
		if (emails.size() > MAXIMO_GESTORES) {
			throw new RegraNegocioException("Cadastre até %d gestores.".formatted(MAXIMO_GESTORES)).noCampo("gestores");
		}
		var validos = new LinkedHashSet<String>();
		for (int i = 0; i < emails.size(); i++) {
			String email = emails.get(i) == null ? "" : emails.get(i).strip().toLowerCase(Locale.ROOT);
			if (email.isEmpty()) {
				continue;
			}
			var problema = Emails.problema(email);
			if (problema.isPresent()) {
				throw new RegraNegocioException(problema.get()).noCampo("gestores." + i);
			}
			validos.add(email);
		}
		return List.copyOf(validos);
	}

	private static boolean vazio(String valor) {
		return valor == null || valor.isBlank();
	}

	@Transactional
	public EmpresaDetalhe cadastrar(NovaEmpresa pedido) {
		String nome = pedido.nome().strip().replaceAll("\\s+", " ");
		if (empresas.existsByNomeIgnoreCase(nome)) {
			throw RegraNegocioException.conflito("Já existe uma empresa com esse nome.").noCampo("nome");
		}
		boolean escolhido = pedido.codigo() != null && !pedido.codigo().isBlank();
		String codigo = CodigoCampanha.normalizar(escolhido ? pedido.codigo() : nome);
		if (!CodigoCampanha.valido(codigo)) {
			throw escolhido ? new RegraNegocioException("Use pelo menos 3 letras ou números no endereço.").noCampo("codigo")
					: new RegraNegocioException("Use pelo menos 3 letras ou números no nome.").noCampo("nome");
		}
		if (campanhas.existsByCodigo(codigo)) {
			if (escolhido) {
				throw RegraNegocioException.conflito("Esse endereço já está em uso.").noCampo("codigo");
			}
			codigo = sugestao(codigo);
		}
		String[] cores = validarCores(pedido.corPrimaria(), pedido.corSecundaria());
		Questionario questionario = questionarios.findByCodigo(Questionario.PADRAO)
			.orElseThrow(() -> new NaoEncontradoException("Nenhum questionário disponível para o link."));

		try {
			Empresa nova = new Empresa(nome);
			nova.definirCores(cores[0], cores[1]);
			nova.definirEncerramento(pedido.encerraEm());
			nova.definirGestores(gestores(pedido.gestores()));
			Empresa empresa = empresas.saveAndFlush(nova);
			campanhas.saveAndFlush(Campanha.daEmpresa(codigo, empresa, questionario));
			cargos.definir(empresa, pedido.cargos());
			return detalhe(empresa.getId());
		}
		catch (DataIntegrityViolationException e) {
			// Dois cadastros iguais ao mesmo tempo: o índice único do banco segura o segundo.
			throw RegraNegocioException.conflito("Essa empresa ou esse endereço acabaram de ser cadastrados.");
		}
	}

	public Disponibilidade disponibilidade(String texto) {
		String codigo = CodigoCampanha.normalizar(texto);
		if (!CodigoCampanha.valido(codigo)) {
			return new Disponibilidade(codigo, url(codigo), false, false, null);
		}
		boolean livre = !campanhas.existsByCodigo(codigo);
		return new Disponibilidade(codigo, url(codigo), true, livre, livre ? null : sugestao(codigo));
	}

	/** O primeiro "codigo-N" livre. */
	private String sugestao(String codigo) {
		for (int n = 2; n <= MAXIMO_SUFIXOS; n++) {
			String candidato = CodigoCampanha.comSufixo(codigo, n);
			if (!campanhas.existsByCodigo(candidato)) {
				return candidato;
			}
		}
		throw RegraNegocioException.conflito("Esse endereço já está em uso. Escolha outro.").noCampo("codigo");
	}

	private Map<Long, Campanha> linksPrincipais(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, Campanha> links = new LinkedHashMap<>();
		campanhas.findByEmpresaIdInOrderByIdAsc(ids).forEach(c -> links.putIfAbsent(c.getEmpresa().getId(), c));
		return links;
	}

	private Link link(Campanha campanha) {
		return campanha == null ? null : new Link(campanha.getCodigo(), url(campanha.getCodigo()), campanha.isAtiva());
	}

	private String url(String codigo) {
		return urlPublica + "/c/" + codigo;
	}

}
