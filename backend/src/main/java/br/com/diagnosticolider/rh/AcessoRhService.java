package br.com.diagnosticolider.rh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import br.com.diagnosticolider.campanha.LinksDaEmpresa;
import br.com.diagnosticolider.compartilhado.Emails;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.convite.ConviteEmail;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.empresa.PaletaMarca;
import br.com.diagnosticolider.notificacao.Correio;
import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.EmailSender;

/**
 * Entrada do RH sem senha. Quem está na lista de gestores de uma empresa pede um código de 6
 * dígitos (chega por e-mail, com um botão que entra sem digitar) e o aparelho fica lembrado por 30
 * dias. O administrador também pode mandar um convite (só o botão, vale 7 dias). As empresas de
 * cada pessoa saem da lista de gestores a cada chamada: tirar o e-mail da lista tira o acesso na hora.
 */
@Service
public class AcessoRhService {

	private static final Logger log = LoggerFactory.getLogger(AcessoRhService.class);

	static final Duration VALIDADE_CODIGO = Duration.ofMinutes(10);

	static final Duration VALIDADE_CONVITE = Duration.ofDays(7);

	static final Duration LEMBRAR = Duration.ofDays(30);

	/** O prazo de 30 dias é estendido no máximo uma vez por hora (evita gravar a cada chamada). */
	static final Duration RENOVAR_A_CADA = Duration.ofHours(1);

	static final int TENTATIVAS = 5;

	/** Abrir a página conta como acesso uma vez a cada 30 minutos (a tela se atualiza sozinha). */
	private static final Duration ABRIU_A_CADA = Duration.ofMinutes(30);

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final CodigoRhRepository codigos;

	private final AcessoRhRepository acessos;

	private final AtividadeRhRepository atividades;

	private final EmpresaRepository empresas;

	private final ProtecaoRh protecao;

	private final Correio correio;

	private final ITemplateEngine templates;

	private final Clock clock;

	private final String urlPublica;

	private final LinksDaEmpresa links;

	AcessoRhService(CodigoRhRepository codigos, AcessoRhRepository acessos, AtividadeRhRepository atividades,
			EmpresaRepository empresas, ProtecaoRh protecao, Correio correio, ITemplateEngine templates, Clock clock,
			AppProperties propriedades, LinksDaEmpresa links) {
		this.links = links;
		this.codigos = codigos;
		this.acessos = acessos;
		this.atividades = atividades;
		this.empresas = empresas;
		this.protecao = protecao;
		this.correio = correio;
		this.templates = templates;
		this.clock = clock;
		this.urlPublica = propriedades.urlPublica().replaceAll("/+$", "");
	}

	/** Quem está na página do RH e as empresas que ele acompanha. */
	public record Gestor(Long acessoId, String email, List<EmpresaDoGestor> empresas, boolean renovado) {

		public boolean acompanha(Long empresaId) {
			return empresas.stream().anyMatch(e -> e.id().equals(empresaId));
		}

	}

	/** codigo: o do link da empresa, que também é o endereço da página do RH (/acompanhar/{codigo}). */
	public record EmpresaDoGestor(Long id, String nome, String codigo) {
	}

	/** Entrou: o token vai no cookie (só o hash fica no banco). */
	public record Entrada(String token, Gestor gestor) {
	}

	// ------------------------------------------------------------------ código por e-mail

	/**
	 * Manda o código, se o e-mail for de um gestor. Para quem não é, responde igual e não manda
	 * nada: a tela não revela quem acompanha uma empresa. empresa: o código da página de onde a
	 * pessoa pediu (/acompanhar/{codigo}); se ela acompanha essa empresa, o e-mail vem com a marca
	 * dela e o botão entra direto nela.
	 */
	@Transactional
	public void pedirCodigo(String bruto, String ip, String empresa) {
		String email = normalizar(bruto);
		Emails.problema(email).ifPresent(problema -> {
			throw RegraNegocioException.comStatus(HttpStatus.BAD_REQUEST, problema);
		});
		protecao.bloqueioDePedido(email, ip).ifPresent(espera -> {
			throw bloqueado(espera, "Você já pediu alguns códigos: confira a caixa de entrada e o spam.");
		});
		protecao.pediu(email, ip);

		if (!correio.ativo()) {
			throw RegraNegocioException.comStatus(HttpStatus.SERVICE_UNAVAILABLE,
					"O envio de e-mail está desligado neste servidor, então o código não tem como chegar.");
		}
		List<Empresa> dele = empresas.dosGestores(email);
		if (dele.isEmpty()) {
			log.info("Código do RH pedido para um e-mail que não é gestor de nenhuma empresa.");
			return;
		}
		Instant agora = clock.instant();
		String codigo = "%06d".formatted(ALEATORIO.nextInt(1_000_000));
		String link = tokenAleatorio();
		codigos.save(new CodigoRh(email, CodigoRh.Tipo.CODIGO, hash(codigo), hash(link), agora,
				agora.plus(VALIDADE_CODIGO)));
		Map<Long, String> codigosDosLinks = links.codigos(dele.stream().map(Empresa::getId).toList());
		Empresa destino = empresa == null || empresa.isBlank() ? null
				: dele.stream().filter(e -> empresa.strip().equalsIgnoreCase(codigosDosLinks.get(e.getId()))).findFirst()
					.orElse(null);
		if (destino == null && dele.size() == 1) {
			destino = dele.getFirst();
		}
		String codigoDestino = destino == null ? null : codigosDosLinks.get(destino.getId());
		var modelo = new AcessoRhEmail(destino == null ? null : destino.getNome(),
				destino == null ? "DL" : MarcaEmpresa.siglaDe(destino.getNome()),
				destino == null ? null : ConviteEmail.logoDe(destino, urlPublica), MarcaEmpresa.temaDe(destino),
				codigo.substring(0, 3) + " " + codigo.substring(3), urlEntrar(link, codigoDestino),
				links.paginaDoRh(codigoDestino), "10 minutos");
		enviar(email, codigo + " é o seu código de acesso", modelo, destino == null ? null : destino.getId());
	}

	@Transactional(noRollbackFor = RegraNegocioException.class)
	public Entrada entrarComCodigo(String bruto, String codigoDigitado, String ip, String aparelho) {
		String email = normalizar(bruto);
		protecao.bloqueioDeTentativa(ip).ifPresent(espera -> {
			throw bloqueado(espera, "Muitas tentativas erradas.");
		});
		String codigo = codigoDigitado == null ? "" : codigoDigitado.replaceAll("\\D", "");
		Instant agora = clock.instant();
		CodigoRh c = codigos.findFirstByEmailAndTipoOrderByCriadoEmDescIdDesc(email, CodigoRh.Tipo.CODIGO)
			.filter(x -> x.valido(agora, TENTATIVAS))
			.orElseThrow(() -> naoEntrou("Este código venceu ou já foi usado. Peça um novo."));
		if (!MessageDigest.isEqual(hash(codigo).getBytes(StandardCharsets.UTF_8),
				c.getCodigoHash().getBytes(StandardCharsets.UTF_8))) {
			c.errou();
			protecao.errou(ip);
			int restam = TENTATIVAS - c.getTentativas();
			throw naoEntrou(restam <= 0 ? "Código incorreto, e as tentativas deste código acabaram. Peça um novo."
					: "Código incorreto. Confira os 6 números do e-mail mais recente%s.".formatted(
							restam == 1 ? " (resta 1 tentativa)" : ""));
		}
		c.usar(agora);
		return criarAcesso(email, aparelho, agora);
	}

	/** O botão do e-mail (código ou convite do painel). */
	@Transactional
	public Entrada entrarComLink(String link, String ip, String aparelho) {
		protecao.bloqueioDeTentativa(ip).ifPresent(espera -> {
			throw bloqueado(espera, "Muitas tentativas erradas.");
		});
		Instant agora = clock.instant();
		Optional<CodigoRh> achado = link == null || link.isBlank() ? Optional.empty() : codigos.findByLinkHash(hash(link.strip()));
		CodigoRh c = achado.filter(x -> x.valido(agora, TENTATIVAS)).orElse(null);
		if (c == null) {
			protecao.errou(ip);
			throw naoEntrou(achado.isPresent() && achado.get().getUsadoEm() != null
					? "Este botão já foi usado. Digite o seu e-mail para receber um código novo."
					: "Este botão venceu. Digite o seu e-mail para receber um código novo.");
		}
		c.usar(agora);
		return criarAcesso(c.getEmail(), aparelho, agora);
	}

	private Entrada criarAcesso(String email, String aparelho, Instant agora) {
		List<Empresa> dele = empresas.dosGestores(email);
		if (dele.isEmpty()) {
			throw RegraNegocioException.comStatus(HttpStatus.FORBIDDEN,
					"Este e-mail não acompanha mais nenhuma avaliação. Fale com quem administra o Diagnóstico do Líder.");
		}
		String token = tokenAleatorio();
		AcessoRh acesso = acessos.save(new AcessoRh(email, hash(token), agora, LEMBRAR, aparelho));
		for (Empresa e : dele) {
			atividades.save(new AtividadeRh(e.getId(), email, AtividadeRh.Tipo.ENTROU, agora, acesso.getAparelho()));
		}
		return new Entrada(token, new Gestor(acesso.getId(), email, empresasDe(dele), true));
	}

	// ------------------------------------------------------------------ sessão

	/** O gestor do cookie, se o acesso ainda vale e ele ainda acompanha alguma empresa. */
	@Transactional
	public Optional<Gestor> gestor(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		Instant agora = clock.instant();
		AcessoRh acesso = acessos.findByTokenHash(hash(token)).filter(a -> a.valido(agora)).orElse(null);
		if (acesso == null) {
			return Optional.empty();
		}
		List<Empresa> dele = empresas.dosGestores(acesso.getEmail());
		if (dele.isEmpty()) {
			return Optional.empty();
		}
		boolean renovado = acesso.usar(agora, LEMBRAR, RENOVAR_A_CADA);
		return Optional.of(new Gestor(acesso.getId(), acesso.getEmail(), empresasDe(dele), renovado));
	}

	@Transactional
	public void sair(String token) {
		if (token != null && !token.isBlank()) {
			acessos.findByTokenHash(hash(token)).ifPresent(a -> a.encerrar(clock.instant()));
		}
	}

	/** Abriu a página de uma empresa (no máximo uma vez a cada 30 minutos) ou baixou um arquivo. */
	@Transactional
	public void registrar(Gestor gestor, Long empresaId, AtividadeRh.Tipo tipo, String aparelho) {
		Instant agora = clock.instant();
		if (tipo == AtividadeRh.Tipo.ABRIU && atividades.existsByEmpresaIdAndEmailAndTipoAndQuandoAfter(empresaId,
				gestor.email(), tipo, agora.minus(ABRIU_A_CADA))) {
			return;
		}
		atividades.save(new AtividadeRh(empresaId, gestor.email(), tipo, agora, recortar(aparelho)));
	}

	// ------------------------------------------------------------------ painel do administrador

	/** Cada gestor da empresa com o último acesso à página do RH e o último convite mandado. */
	public record AcessoDoGestor(String email, Instant ultimoAcesso, String aparelho, int acessos,
			Instant conviteEnviadoEm) {
	}

	@Transactional(readOnly = true)
	public List<AcessoDoGestor> acessosDaEmpresa(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		List<String> gestores = empresa.getGestores().stream().map(AcessoRhService::normalizar).toList();
		if (gestores.isEmpty()) {
			return List.of();
		}
		Map<String, Instant> convites = new LinkedHashMap<>();
		for (Object[] linha : codigos.ultimosConvites(gestores)) {
			convites.put((String) linha[0], (Instant) linha[1]);
		}
		List<AtividadeRh> lista = atividades.findByEmpresaIdOrderByQuandoDesc(empresaId);
		return gestores.stream().map(email -> {
			List<AtividadeRh> dele = lista.stream()
				.filter(a -> a.getEmail().equals(email)
						&& (a.getTipo() == AtividadeRh.Tipo.ENTROU || a.getTipo() == AtividadeRh.Tipo.ABRIU))
				.toList();
			AtividadeRh ultima = dele.isEmpty() ? null : dele.getFirst();
			return new AcessoDoGestor(email, ultima == null ? null : ultima.getQuando(),
					ultima == null ? null : ultima.getAparelho(), dele.size(), convites.get(email));
		}).toList();
	}

	/** "Mandar acesso" do painel: um e-mail com o botão que entra direto (vale 7 dias, uma vez). */
	@Transactional
	public void convidar(Long empresaId, String bruto) {
		Empresa empresa = empresa(empresaId);
		String email = normalizar(bruto);
		if (empresa.getGestores().stream().map(AcessoRhService::normalizar).noneMatch(email::equals)) {
			throw RegraNegocioException.comStatus(HttpStatus.BAD_REQUEST,
					"Este e-mail não está na lista de gestores da empresa. Cadastre antes de mandar o acesso.");
		}
		if (!correio.ativo()) {
			throw RegraNegocioException.conflito("O envio de e-mail está desligado neste servidor.");
		}
		Instant agora = clock.instant();
		String link = tokenAleatorio();
		codigos.save(new CodigoRh(email, CodigoRh.Tipo.CONVITE, null, hash(link), agora, agora.plus(VALIDADE_CONVITE)));
		String codigoDoLink = links.codigo(empresaId);
		var modelo = new AcessoRhEmail(empresa.getNome(), MarcaEmpresa.siglaDe(empresa.getNome()),
				ConviteEmail.logoDe(empresa, urlPublica), MarcaEmpresa.temaDe(empresa), null, urlEntrar(link, codigoDoLink),
				links.paginaDoRh(codigoDoLink), "7 dias");
		enviar(email, empresa.getNome() + ": acompanhe a avaliação dos líderes", modelo, empresaId);
	}

	/** A prévia do convite no painel (o botão aponta para a página, sem entrar). */
	@Transactional(readOnly = true)
	public String previaConvite(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		String pagina = links.paginaDoRh(empresaId);
		return html(new AcessoRhEmail(empresa.getNome(), MarcaEmpresa.siglaDe(empresa.getNome()),
				ConviteEmail.logoDe(empresa, urlPublica), MarcaEmpresa.temaDe(empresa), null, pagina, pagina, "7 dias"));
	}

	// ------------------------------------------------------------------ apoio

	private void enviar(String email, String assunto, AcessoRhEmail modelo, Long empresaId) {
		try {
			correio.enviar(new EmailSender.Mensagem(email, assunto, html(modelo), List.of()),
					new Correio.Origem(EmailEnviado.Tipo.ACESSO_RH, empresaId, null, null));
		}
		catch (EmailSender.FalhaEnvio e) {
			throw RegraNegocioException.comStatus(HttpStatus.BAD_GATEWAY,
					"O e-mail não saiu agora (" + e.getMessage() + "). Tente de novo em instantes.");
		}
	}

	private String html(AcessoRhEmail modelo) {
		var contexto = new Context(PT_BR);
		contexto.setVariable("a", modelo);
		return templates.process("email/acesso-rh", contexto);
	}

	/**
	 * O token vai depois do #: não passa pelo servidor nem fica no histórico de quem pré-visualiza
	 * links. ?empresa= diz para onde ir depois de entrar (a empresa do e-mail).
	 */
	private String urlEntrar(String link, String empresa) {
		return urlPublica + "/acompanhar/entrar" + (empresa == null ? "" : "?empresa=" + empresa) + "#" + link;
	}

	private Empresa empresa(Long id) {
		return empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

	private List<EmpresaDoGestor> empresasDe(List<Empresa> lista) {
		Map<Long, String> codigosDosLinks = links.codigos(lista.stream().map(Empresa::getId).toList());
		return lista.stream().map(e -> new EmpresaDoGestor(e.getId(), e.getNome(), codigosDosLinks.get(e.getId()))).toList();
	}

	static String normalizar(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

	private static String recortar(String aparelho) {
		return aparelho == null ? null : aparelho.substring(0, Math.min(aparelho.length(), 120));
	}

	private static String tokenAleatorio() {
		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String valor) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static RegraNegocioException naoEntrou(String mensagem) {
		return RegraNegocioException.comStatus(HttpStatus.UNAUTHORIZED, mensagem);
	}

	private static RegraNegocioException bloqueado(Duration espera, String motivo) {
		long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
		var erro = RegraNegocioException.comStatus(HttpStatus.TOO_MANY_REQUESTS,
				"%s Tente de novo em %d %s.".formatted(motivo, minutos, minutos == 1 ? "minuto" : "minutos"));
		erro.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(espera.toSeconds()));
		return erro;
	}

}
