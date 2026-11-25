package br.com.diagnosticolider.notificacao;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Um e-mail que o sistema mandou e o que aconteceu com ele depois, pelos avisos do Resend:
 * entregue, atrasado, devolvido, marcado como spam ou bloqueado. Sem pixel de abertura.
 */
@Entity
@Table(name = "email_enviado")
public class EmailEnviado {

	private static final int MAXIMO_PARA = 2000;

	private static final int MAXIMO_ASSUNTO = 300;

	private static final int MAXIMO_DETALHE = 500;

	public enum Tipo {
		CONVITE, LEMBRETE, RESULTADO, GESTOR_DIARIO, GESTOR_FINAL
	}

	/** ENVIADO: o Resend aceitou; os outros vêm dos avisos dele. */
	public enum Situacao {
		ENVIADO, ENTREGUE, ATRASADO, DEVOLVIDO, SPAM, BLOQUEADO, FALHOU;

		/** Não chegou e não vai chegar: o administrador precisa agir (corrigir o endereço, avisar o RH). */
		public boolean naoChegou() {
			return this == DEVOLVIDO || this == SPAM || this == BLOQUEADO || this == FALHOU;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "provedor_id")
	private String provedorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(name = "empresa_id")
	private Long empresaId;

	@Column(name = "convite_id")
	private Long conviteId;

	@Column(name = "diagnostico_id")
	private UUID diagnosticoId;

	@Column(nullable = false)
	private String para;

	@Column(nullable = false)
	private String assunto;

	@Column(name = "enviado_em", nullable = false)
	private Instant enviadoEm;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Situacao situacao = Situacao.ENVIADO;

	@Column(name = "entregue_em")
	private Instant entregueEm;

	@Column(name = "atualizado_em")
	private Instant atualizadoEm;

	private String detalhe;

	protected EmailEnviado() {
	}

	EmailEnviado(String provedorId, Tipo tipo, Long empresaId, Long conviteId, UUID diagnosticoId, String para,
			String assunto, Instant enviadoEm) {
		this.provedorId = provedorId;
		this.tipo = tipo;
		this.empresaId = empresaId;
		this.conviteId = conviteId;
		this.diagnosticoId = diagnosticoId;
		this.para = cortar(para, MAXIMO_PARA);
		this.assunto = cortar(assunto, MAXIMO_ASSUNTO);
		this.enviadoEm = enviadoEm;
	}

	/**
	 * Um aviso do Resend. A ordem dos avisos não é garantida: "entregue" não apaga uma devolução ou
	 * um spam, e "atrasado" só vale enquanto nada melhor chegou. Devolve se a situação mudou.
	 */
	boolean atualizar(Situacao nova, Instant quando, String detalhe) {
		boolean muda = switch (nova) {
			case ENTREGUE -> situacao == Situacao.ENVIADO || situacao == Situacao.ATRASADO;
			case ATRASADO -> situacao == Situacao.ENVIADO;
			case SPAM -> situacao != Situacao.SPAM;
			case DEVOLVIDO, BLOQUEADO, FALHOU -> !situacao.naoChegou();
			case ENVIADO -> false;
		};
		if (nova == Situacao.ENTREGUE && entregueEm == null) {
			entregueEm = quando;
		}
		if (muda) {
			situacao = nova;
			atualizadoEm = quando;
			this.detalhe = detalhe == null ? null : cortar(detalhe, MAXIMO_DETALHE);
		}
		return muda;
	}

	private static String cortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
	}

	public Long getId() {
		return id;
	}

	public String getProvedorId() {
		return provedorId;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public Long getEmpresaId() {
		return empresaId;
	}

	public Long getConviteId() {
		return conviteId;
	}

	public UUID getDiagnosticoId() {
		return diagnosticoId;
	}

	public String getPara() {
		return para;
	}

	public String getAssunto() {
		return assunto;
	}

	public Instant getEnviadoEm() {
		return enviadoEm;
	}

	public Situacao getSituacao() {
		return situacao;
	}

	public Instant getEntregueEm() {
		return entregueEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public String getDetalhe() {
		return detalhe;
	}

}
