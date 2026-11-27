package br.com.diagnosticolider.diagnostico;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.annotations.UuidGenerator;

import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.empresa.Cargo;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.questionario.Dimensao;
import br.com.diagnosticolider.questionario.FaixaResultado;
import br.com.diagnosticolider.questionario.Opcao;
import br.com.diagnosticolider.questionario.Pergunta;
import br.com.diagnosticolider.questionario.QuestionarioVersao;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Um líder respondendo (ou tendo respondido) uma versão do questionário.
 *
 * <p>O id é um UUID aleatório (122 bits) e funciona como chave de acesso: quem tem o link
 * do resultado vê o resultado. Por isso nunca usar UUID sequencial aqui.
 */
@Entity
@Table(name = "diagnostico")
public class Diagnostico {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	private UUID id;

	@Version
	private long revisao;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campanha_id")
	private Campanha campanha;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "versao_id")
	private QuestionarioVersao versao;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empresa_id")
	private Empresa empresa;

	@Embedded
	private Respondente respondente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusDiagnostico status;

	private Integer pontuacao;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "faixa_id")
	private FaixaResultado faixa;

	@OneToMany(mappedBy = "diagnostico", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Resposta> respostas = new ArrayList<>();

	@OneToMany(mappedBy = "diagnostico", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DiagnosticoDimensao> dimensoes = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(name = "status_email", nullable = false)
	private StatusEmail statusEmail = StatusEmail.NAO_ENVIADO;

	@Column(name = "email_enviado_em")
	private Instant emailEnviadoEm;

	@Column(name = "consentimento_em", nullable = false)
	private Instant consentimentoEm;

	@Column(name = "iniciado_em", nullable = false)
	private Instant iniciadoEm;

	@Column(name = "concluido_em")
	private Instant concluidoEm;

	/** Inativado pelo administrador: fica guardado, mas fora de todos os cálculos. */
	@Column(name = "inativado_em")
	private Instant inativadoEm;

	/** O mesmo líder respondeu de novo: esta resposta saiu dos cálculos, e vale a apontada aqui. */
	@Column(name = "substituido_por")
	private UUID substituidoPor;

	/** Cargo informado no link da empresa (nulo no link geral e em "Outro cargo"). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cargo_id")
	private Cargo cargo;

	@Column(name = "cargo_outro", nullable = false)
	private boolean cargoOutro;

	@Column(name = "cargo_outro_nome", length = 80)
	private String cargoOutroNome;

	@Enumerated(EnumType.STRING)
	@Column(name = "tempo_cargo", length = 20)
	private TempoCargo tempoCargo;

	protected Diagnostico() {
	}

	public static Diagnostico iniciar(Campanha campanha, QuestionarioVersao versao, Respondente respondente,
			Instant agora) {
		var diagnostico = new Diagnostico();
		diagnostico.campanha = campanha;
		diagnostico.versao = versao;
		diagnostico.empresa = campanha.getEmpresa();
		diagnostico.respondente = respondente;
		diagnostico.status = StatusDiagnostico.EM_ANDAMENTO;
		diagnostico.consentimentoEm = agora;
		diagnostico.iniciadoEm = agora;
		return diagnostico;
	}

	/** O líder voltou e corrigiu nome, e-mail, WhatsApp ou empresa antes de concluir. */
	/**
	 * Correção feita no painel (vale também depois de concluído): os dados do líder e o cargo.
	 * As respostas, o resultado e o tempo no cargo não mudam.
	 */
	public void corrigirPeloPainel(Respondente respondente, Cargo cargo, boolean outro) {
		this.respondente = respondente;
		this.cargo = outro ? null : cargo;
		this.cargoOutro = outro;
		if (!outro) {
			this.cargoOutroNome = null;
		}
	}

	public void inativar(Instant agora) {
		if (inativadoEm == null) {
			inativadoEm = agora;
		}
	}

	public void reativar() {
		inativadoEm = null;
	}

	/** Uma resposta mais recente do mesmo líder fica no lugar desta (que sai dos cálculos). */
	public void substituirPor(Diagnostico novo, Instant agora) {
		inativar(agora);
		substituidoPor = novo.getId();
	}

	public UUID getSubstituidoPor() {
		return substituidoPor;
	}

	public Instant getInativadoEm() {
		return inativadoEm;
	}

	public void atualizarRespondente(Respondente respondente) {
		exigirEmAndamento();
		this.respondente = respondente;
	}

	/** Cargo e tempo no cargo (no link geral e em empresas sem cargos, fica tudo vazio). */
	public void definirPerfil(PerfilCargo perfil) {
		exigirEmAndamento();
		this.cargo = perfil.cargo();
		this.cargoOutro = perfil.outro();
		this.cargoOutroNome = perfil.outro() ? perfil.outroNome() : null;
		this.tempoCargo = perfil.tempo();
	}

	/** Como o cargo aparece no painel: o nome, "Outro: Head de vendas", "Outro cargo" ou nulo. */
	public String cargoDescrito() {
		if (cargo != null) {
			return cargo.getNome();
		}
		if (!cargoOutro) {
			return null;
		}
		return cargoOutroNome == null ? "Outro cargo" : "Outro: " + cargoOutroNome;
	}

	/**
	 * Grava a escolha para a pergunta, substituindo a anterior. Aplica só a diferença
	 * (remove o que saiu, insere o que entrou) para não esbarrar na unique
	 * (diagnostico, pergunta, opcao) quando o líder volta e muda de ideia.
	 */
	public void responder(Pergunta pergunta, List<Opcao> opcoes, Instant agora) {
		exigirEmAndamento();
		Set<Long> escolhidas = opcoes.stream().map(Opcao::getId).collect(Collectors.toSet());
		respostas.removeIf(r -> mesmaPergunta(r, pergunta) && !escolhidas.contains(r.getOpcao().getId()));
		Set<Long> jaGravadas = respostas.stream()
			.filter(r -> mesmaPergunta(r, pergunta))
			.map(r -> r.getOpcao().getId())
			.collect(Collectors.toSet());
		opcoes.stream()
			.filter(o -> !jaGravadas.contains(o.getId()))
			.forEach(o -> respostas.add(new Resposta(this, pergunta, o, agora)));
	}

	public void concluir(int pontuacao, FaixaResultado faixa, Map<Dimensao, Integer> porDimensao, Instant agora) {
		exigirEmAndamento();
		this.pontuacao = pontuacao;
		this.faixa = faixa;
		porDimensao.forEach((dimensao, valor) -> dimensoes.add(new DiagnosticoDimensao(this, dimensao, valor)));
		this.status = StatusDiagnostico.CONCLUIDO;
		this.statusEmail = StatusEmail.PENDENTE;
		this.concluidoEm = agora;
	}

	/** Opções escolhidas agrupadas por pergunta, na ordem do questionário. */
	public Map<Long, List<Opcao>> respostasPorPergunta() {
		return respostas.stream()
			.sorted(Comparator.comparingInt((Resposta r) -> r.getPergunta().getOrdem())
				.thenComparingInt(r -> r.getOpcao().getOrdem()))
			.collect(Collectors.groupingBy(r -> r.getPergunta().getId(), LinkedHashMap::new,
					Collectors.mapping(Resposta::getOpcao, Collectors.toList())));
	}

	public List<DiagnosticoDimensao> dimensoesOrdenadas() {
		return dimensoes.stream().sorted(Comparator.comparingInt(d -> d.getDimensao().getOrdem())).toList();
	}

	public boolean concluido() {
		return status == StatusDiagnostico.CONCLUIDO;
	}

	public Cargo getCargo() {
		return cargo;
	}

	public boolean isCargoOutro() {
		return cargoOutro;
	}

	public String getCargoOutroNome() {
		return cargoOutroNome;
	}

	public TempoCargo getTempoCargo() {
		return tempoCargo;
	}

	private void exigirEmAndamento() {
		if (concluido()) {
			throw RegraNegocioException.conflito("Este diagnóstico já foi concluído e não pode mais ser alterado.");
		}
	}

	private static boolean mesmaPergunta(Resposta resposta, Pergunta pergunta) {
		return resposta.getPergunta().getId().equals(pergunta.getId());
	}

	public UUID getId() {
		return id;
	}

	public Campanha getCampanha() {
		return campanha;
	}

	public QuestionarioVersao getVersao() {
		return versao;
	}

	public Respondente getRespondente() {
		return respondente;
	}

	public StatusDiagnostico getStatus() {
		return status;
	}

	public Integer getPontuacao() {
		return pontuacao;
	}

	public FaixaResultado getFaixa() {
		return faixa;
	}

	public StatusEmail getStatusEmail() {
		return statusEmail;
	}

	public Instant getConcluidoEm() {
		return concluidoEm;
	}

	public Empresa getEmpresa() {
		return empresa;
	}

	public Instant getIniciadoEm() {
		return iniciadoEm;
	}

	public Instant getConsentimentoEm() {
		return consentimentoEm;
	}

	public Instant getEmailEnviadoEm() {
		return emailEnviadoEm;
	}

	/** Quantas perguntas diferentes já têm resposta. */
	public int perguntasRespondidas() {
		return (int) respostas.stream().map(r -> r.getPergunta().getId()).distinct().count();
	}

}
