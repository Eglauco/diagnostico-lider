import { Component, computed, effect, inject, input, linkedSignal, signal, untracked } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { form, maxLength, required, validate } from '@angular/forms/signals';

import {
  CampanhaPublica,
  CARGO_OUTRO,
  ConvitePublico,
  DadosRespondente,
  Opcao,
  OpcaoPerfil,
  PerfilLider,
  Pergunta,
} from '../api/modelos';
import { DiagnosticoApi, problemaDe } from '../api/diagnostico-api';
import { armazenamento, esperar, vibrar } from '../compartilhado/util';
import { problemaEmail } from '../compartilhado/email';
import { formatarTelefone, problemaTelefone } from '../compartilhado/entrada-telefone';
import { prazoPorExtenso } from '../compartilhado/formatos';
import { aplicarTema } from '../compartilhado/tema-marca';
import { NaoEncontrado } from '../nao-encontrado';
import { BarraProgresso } from './barra-progresso';
import { BoasVindas, Retomada } from './boas-vindas';
import { Calculando } from './calculando';
import { ConfirmarDados, ErrosConfirmacao } from './confirmar-dados';
import { CampoId, EtapaCampo } from './etapa-campo';
import { EtapaEscolha } from './etapa-escolha';
import { EtapaPergunta } from './etapa-pergunta';

type Etapa =
  | { tipo: 'inicio'; chave: string }
  /** Convite nominal: os dados da planilha do RH, para confirmar (ou corrigir) numa tela só. */
  | { tipo: 'confirmar'; chave: string; ultimo: boolean }
  | { tipo: 'campo'; chave: string; campo: CampoId; ultimo: boolean }
  /** Cargo e tempo no cargo: só no link de empresa com cargos cadastrados. */
  | { tipo: 'perfil'; chave: string; perfil: 'cargo' | 'tempo'; ultimo: boolean }
  | {
      tipo: 'pergunta';
      chave: string;
      pergunta: Pergunta;
      numero: number;
      total: number;
      ultima: boolean;
      dimensao: string | null;
    };

interface ProgressoSalvo {
  id: string | null;
  dados: DadosRespondente;
  perfil?: PerfilLider;
  /** Convite: o líder já confirmou os dados. */
  confirmado?: boolean;
}

const VAZIO: DadosRespondente = { nome: '', email: '', whatsapp: '' };
const PERFIL_VAZIO: PerfilLider = { cargo: null, outroNome: '', tempo: null };

/** Erro do servidor → etapa onde ele aparece. */
const ETAPA_DO_CAMPO: Record<string, string> = { cargo: 'cargo', cargoOutroNome: 'cargo', tempoCargo: 'tempo' };

/**
 * O questionário inteiro numa rota, uma etapa por tela. A etapa atual vive na URL
 * (?passo=3), então o botão/gesto de voltar do celular volta uma pergunta em vez de
 * sair do diagnóstico. O progresso fica salvo no aparelho para o líder retomar depois.
 */
@Component({
  selector: 'app-diagnostico-pagina',
  imports: [BarraProgresso, BoasVindas, Calculando, ConfirmarDados, EtapaCampo, EtapaEscolha, EtapaPergunta, NaoEncontrado],
  templateUrl: './diagnostico-pagina.html',
})
export class DiagnosticoPagina {
  private readonly api = inject(DiagnosticoApi);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly location = inject(Location);

  /** :codigo da rota (ausente no link geral), :token do convite nominal (/i/:token) e ?passo= da URL. */
  readonly codigo = input<string>();
  readonly token = input<string>();
  readonly passo = input<string>();

  protected readonly codigoCampanha = computed(() => this.codigo() ?? 'geral');
  protected readonly campanha = httpResource<CampanhaPublica>(() =>
    this.token() ? undefined : `/api/public/campanhas/${encodeURIComponent(this.codigoCampanha())}`,
  );
  /** Link pessoal: os dados que o RH mandou e o link da empresa, num pedido só. */
  protected readonly convite = httpResource<ConvitePublico>(() =>
    this.token() ? `/api/public/convites/${encodeURIComponent(this.token()!)}` : undefined,
  );
  protected readonly dadosCampanha = computed(() =>
    this.token() ? (this.convite.hasValue() ? this.convite.value().campanha : undefined) : this.campanha.value(),
  );
  protected readonly carregada = computed(() => (this.token() ? this.convite.hasValue() : this.campanha.hasValue()));
  protected readonly erroCarregar = computed(() => (this.token() ? this.convite.error() : this.campanha.error()));
  private readonly chave = computed(() =>
    this.token() ? `diagnostico-lider:convite:${this.token()}` : `diagnostico-lider:${this.codigoCampanha()}`,
  );

  // Estado do diagnóstico ---------------------------------------------------
  protected readonly dados = signal<DadosRespondente>({ ...VAZIO });
  protected readonly perfil = signal<PerfilLider>({ ...PERFIL_VAZIO });
  protected readonly CARGO_OUTRO = CARGO_OUTRO;
  protected readonly diagnosticoId = signal<string | null>(null);
  protected readonly respostas = signal<Record<number, number[]>>({});
  private readonly dadosSincronizados = signal<string | null>(null);
  private readonly concluido = signal(false);
  protected readonly retomada = signal<Retomada | null>(null);
  protected readonly restaurado = signal(false);

  /** Link de empresa: nome, monograma e paleta (as cores valem só se a empresa tiver cores próprias). */
  protected readonly marca = computed(() => this.dadosCampanha()?.empresa ?? null, {
    equal: (a, b) => JSON.stringify(a) === JSON.stringify(b),
  });

  // Convite nominal ----------------------------------------------------------
  /** O líder tocou em "Está tudo certo" na tela de confirmação dos dados. */
  private readonly confirmado = signal(false);
  protected readonly editandoDados = signal(false);
  /** Avaliação da empresa encerrada: ninguém começa nem responde mais. */
  protected readonly encerrada = computed(() => !!this.dadosCampanha()?.encerrada);
  protected readonly prazo = computed(() => {
    const encerraEm = this.dadosCampanha()?.encerraEm;
    return encerraEm && !this.encerrada() ? prazoPorExtenso(encerraEm) : null;
  });
  protected readonly dataEncerramento = computed(() => {
    const encerraEm = this.dadosCampanha()?.encerraEm;
    return encerraEm ? prazoPorExtenso(encerraEm) : '';
  });
  protected readonly primeiroNome = computed(() => this.dados().nome.trim().split(/\s+/)[0] ?? '');

  protected readonly formulario = form(this.dados, (c) => {
    maxLength(c.nome, 160, { message: 'Use até 160 caracteres.' });
    maxLength(c.email, 254, { message: 'E-mail muito longo.' });
    required(c.nome, { message: 'Escreva o seu nome para continuar.' });
    validate(c.nome, ({ value }) =>
      value().trim().length === 1 ? { kind: 'nome-curto', message: 'Escreva o seu nome completo.' } : null,
    );
    required(c.email, { message: 'Escreva o seu e-mail para continuar.' });
    validate(c.email, ({ value }) => {
      const problema = problemaEmail(value());
      return problema ? { kind: 'email', message: problema } : null;
    });
    required(c.whatsapp, { message: 'Escreva o seu WhatsApp para continuar.' });
    validate(c.whatsapp, ({ value }) => {
      const problema = problemaTelefone(value());
      return problema ? { kind: 'whatsapp', message: problema } : null;
    });
  });

  // Interface ----------------------------------------------------------------
  protected readonly enviando = signal(false);
  protected readonly calculando = signal(false);
  protected readonly erro = signal<string | null>(null);
  /** Nas etapas de texto o erro vai para o próprio campo: no celular, o teclado cobriria o aviso. */
  protected readonly erroNoAviso = computed(() => (this.etapaVisivel()[0]?.tipo === 'campo' ? null : this.erro()));
  private readonly errosServidor = signal<Record<string, string>>({});
  private readonly tentou = signal<Partial<Record<CampoId, boolean>>>({});

  // Etapas -------------------------------------------------------------------
  protected readonly etapas = computed<Etapa[]>(() => {
    const campanha = this.dadosCampanha();
    if (!campanha) return [];
    const campos: CampoId[] = ['nome', 'email', 'whatsapp'];
    const comPerfil = !!campanha.perfil;
    const dimensoes = new Map(campanha.questionario.dimensoes.map((d) => [d.codigo, d.nome]));
    const perguntas = campanha.questionario.perguntas;
    // Convite nominal: os dados (e o cargo) vêm da planilha do RH e são confirmados numa tela só.
    const identificacao: Etapa[] = this.token()
      ? [
          { tipo: 'confirmar', chave: 'confirmar', ultimo: !comPerfil },
          ...(comPerfil ? [{ tipo: 'perfil', chave: 'tempo', perfil: 'tempo', ultimo: true } satisfies Etapa] : []),
        ]
      : [
          { tipo: 'inicio', chave: 'inicio' },
          ...campos.map(
            (campo, i): Etapa => ({ tipo: 'campo', chave: campo, campo, ultimo: !comPerfil && i === campos.length - 1 }),
          ),
          ...(comPerfil
            ? [
                { tipo: 'perfil', chave: 'cargo', perfil: 'cargo', ultimo: false } satisfies Etapa,
                { tipo: 'perfil', chave: 'tempo', perfil: 'tempo', ultimo: true } satisfies Etapa,
              ]
            : []),
        ];
    return [
      ...identificacao,
      ...perguntas.map(
        (pergunta, i): Etapa => ({
          tipo: 'pergunta',
          chave: `pergunta-${pergunta.id}`,
          pergunta,
          numero: i + 1,
          total: perguntas.length,
          ultima: i === perguntas.length - 1,
          dimensao: pergunta.dimensao ? (dimensoes.get(pergunta.dimensao) ?? null) : null,
        }),
      ),
    ];
  });

  protected readonly grupos = computed(() => {
    const etapas = this.etapas();
    return [
      etapas.filter((e) => e.tipo === 'campo' || e.tipo === 'perfil').length,
      etapas.filter((e) => e.tipo === 'pergunta').length,
    ];
  });

  /** O que vai para o servidor: dados do líder e, se o link pede, cargo e tempo no cargo. */
  private readonly envio = computed(() => ({
    dados: this.dados(),
    perfil: this.dadosCampanha()?.perfil ? this.perfil() : null,
  }));
  private readonly sincronizado = computed(
    () => this.diagnosticoId() !== null && this.dadosSincronizados() === JSON.stringify(this.envio()),
  );

  /** Opções das telas de cargo (com "não está na lista" no fim) e de tempo no cargo. */
  protected readonly opcoesCargo = computed<OpcaoPerfil[]>(() => [
    ...(this.dadosCampanha()?.perfil?.cargos ?? []),
    { valor: CARGO_OUTRO, texto: 'Meu cargo não está na lista' },
  ]);
  protected readonly opcoesTempo = computed(() => this.dadosCampanha()?.perfil?.tempos ?? []);

  /** Etapa mais avançada que o líder pode abrir: todas as anteriores precisam estar completas. */
  private readonly limite = computed(() => {
    const etapas = this.etapas();
    if (this.concluido()) return 0;
    let i = 0;
    while (i < etapas.length - 1 && this.completa(etapas[i])) i++;
    return i;
  });

  private readonly pedido = computed(() => {
    const n = Number.parseInt(this.passo() ?? '0', 10);
    return Number.isFinite(n) && n > 0 ? n : 0;
  });
  protected readonly indice = computed(() => Math.min(this.pedido(), this.limite()));
  protected readonly etapaVisivel = computed(() => {
    const etapa = this.etapas()[this.indice()];
    return etapa ? [etapa] : [];
  });
  protected readonly direcao = linkedSignal<number, 'frente' | 'tras'>({
    source: this.indice,
    computation: (atual, anterior) => (anterior && atual < anterior.source ? 'tras' : 'frente'),
  });
  protected readonly pronto = computed(() => this.carregada() && this.restaurado());

  constructor() {
    // Cores da empresa na página inteira enquanto o líder estiver aqui.
    effect((onCleanup) => {
      const marca = this.marca();
      if (marca?.personalizada) onCleanup(aplicarTema(marca.tema));
    });

    // Restaura o progresso salvo no aparelho assim que a campanha carrega.
    effect(() => {
      if (!this.carregada() || untracked(this.restaurado)) return;
      untracked(() => void this.restaurar());
    });

    // Salva o progresso a cada mudança.
    effect(() => {
      if (!this.restaurado()) return;
      armazenamento.gravar(this.chave(), {
        id: this.diagnosticoId(),
        dados: this.dados(),
        perfil: this.perfil(),
        confirmado: this.confirmado(),
      } satisfies ProgressoSalvo);
    });

    // Convite: o link abriu neste aparelho (auditoria). Só aqui, na página que carregou: a
    // prévia do WhatsApp também abre o link, mas não roda isto.
    effect(() => {
      const token = this.token();
      if (!token || !this.carregada() || untracked(() => this.acessoRegistrado)) return;
      this.acessoRegistrado = true;
      const celular = matchMedia('(pointer: coarse)').matches;
      void this.api.registrarAcesso(token, celular ? 'celular' : 'computador').catch(() => undefined);
    });

    // URL pedindo uma etapa ainda bloqueada (ex.: recarregou sem dados): corrige a URL.
    effect(() => {
      if (this.pronto() && !this.calculando() && this.pedido() > this.limite()) {
        untracked(() => this.irPara(this.limite(), true));
      }
    });
  }

  private async restaurar() {
    const salvo = armazenamento.ler<ProgressoSalvo>(this.chave());
    const convite = this.token() && this.convite.hasValue() ? this.convite.value() : null;
    if (salvo?.dados) {
      // Só os campos atuais: progresso salvo por versões antigas podia trazer "empresa".
      const { nome = '', email = '', whatsapp = '' } = salvo.dados;
      this.dados.set({ nome, email, whatsapp: formatarTelefone(whatsapp) });
    } else if (convite) {
      // Convite: os dados que o RH mandou, para o líder só confirmar.
      this.dados.set({ nome: convite.nome, email: convite.email, whatsapp: formatarTelefone(convite.whatsapp ?? '') });
    }
    if (salvo?.perfil) this.perfil.set(this.perfilValido(salvo.perfil));
    else if (convite?.cargo) this.perfil.set(this.perfilValido({ cargo: convite.cargo }));
    // O diagnóstico do convite vale mais que o do aparelho (ele pode ter começado em outro).
    const id = convite?.diagnosticoId ?? salvo?.id ?? null;
    this.confirmado.set(!!convite?.diagnosticoId || !!salvo?.confirmado);
    if (id) {
      try {
        const d = await this.api.buscar(id);
        this.diagnosticoId.set(d.id);
        this.dadosSincronizados.set(JSON.stringify(this.envio()));
        const respostas: Record<number, number[]> = {};
        Object.entries(d.respostas).forEach(([pergunta, opcoes]) => (respostas[Number(pergunta)] = opcoes));
        this.respostas.set(respostas);
        this.concluido.set(d.status === 'CONCLUIDO');
        const total = this.dadosCampanha()?.questionario.perguntas.length ?? 0;
        this.retomada.set(
          d.status === 'CONCLUIDO'
            ? { tipo: 'concluido', primeiroNome: d.primeiroNome }
            : { tipo: 'em-andamento', primeiroNome: d.primeiroNome, respondidas: Object.keys(respostas).length, total },
        );
      } catch {
        // Diagnóstico apagado ou de outro ambiente: segue com os dados digitados.
        this.diagnosticoId.set(null);
      }
    }
    this.restaurado.set(true);
  }

  /** Progresso salvo pode citar um cargo que saiu da lista: aí ele volta a ser perguntado. */
  private perfilValido(salvo: Partial<PerfilLider>): PerfilLider {
    const perfil = this.dadosCampanha()?.perfil;
    const cargo = salvo.cargo ?? null;
    const tempo = salvo.tempo ?? null;
    return {
      cargo: cargo === CARGO_OUTRO || perfil?.cargos.some((c) => c.valor === cargo) ? cargo : null,
      outroNome: salvo.outroNome ?? '',
      tempo: perfil?.tempos.some((t) => t.valor === tempo) ? tempo : null,
    };
  }

  private completa(etapa: Etapa): boolean {
    switch (etapa.tipo) {
      case 'inicio':
        return true;
      case 'confirmar':
        return this.confirmado() && this.dadosValidos() && (!etapa.ultimo || this.sincronizado());
      case 'campo':
        return this.formulario[etapa.campo]().valid() && (!etapa.ultimo || this.sincronizado());
      case 'perfil':
        return this.perfil()[etapa.perfil] !== null && (!etapa.ultimo || this.sincronizado());
      case 'pergunta':
        return (this.respostas()[etapa.pergunta.id]?.length ?? 0) > 0;
    }
  }

  // Navegação ----------------------------------------------------------------
  protected irPara(indice: number, substituir = false) {
    this.erro.set(null);
    void this.router.navigate([], {
      relativeTo: this.rota,
      queryParams: { passo: indice > 0 ? indice : null },
      replaceUrl: substituir,
      state: { interno: true },
    });
  }

  protected voltar() {
    const estado = this.location.getState() as { interno?: boolean } | null;
    if (estado?.interno) this.location.back();
    else this.irPara(this.indice() - 1, true);
  }

  protected comecar() {
    this.irPara(1);
  }

  protected continuarDeOndeParou() {
    this.irPara(this.limite());
  }

  protected recomecar() {
    this.diagnosticoId.set(null);
    this.dadosSincronizados.set(null);
    this.respostas.set({});
    this.concluido.set(false);
    this.retomada.set(null);
    this.tentou.set({});
    this.irPara(1);
  }

  protected verResultado() {
    const id = this.diagnosticoId();
    if (id) void this.router.navigate(['/resultado', id]);
  }

  // Identificação ------------------------------------------------------------
  protected mensagemErro(campo: CampoId): string | null {
    const doServidor = this.errosServidor()[campo] ?? null;
    if (doServidor) return doServidor;
    if (!this.tentou()[campo]) return null;
    return this.formulario[campo]().errors()[0]?.message ?? null;
  }

  /** Convite: nome, e-mail, WhatsApp e (se a empresa tem cargos) o cargo, prontos para seguir. */
  private readonly dadosValidos = computed(
    () =>
      this.formulario.nome().valid() &&
      this.formulario.email().valid() &&
      this.formulario.whatsapp().valid() &&
      (!this.dadosCampanha()?.perfil || this.perfil().cargo !== null),
  );
  private readonly tentouConfirmar = signal(false);
  private acessoRegistrado = false;

  protected readonly errosConfirmacao = computed<ErrosConfirmacao>(() => ({
    nome: this.mensagemErro('nome'),
    email: this.mensagemErro('email'),
    whatsapp: this.mensagemErro('whatsapp'),
    cargo:
      this.errosServidor()['cargo'] ??
      (this.tentouConfirmar() && this.dadosCampanha()?.perfil && this.perfil().cargo === null ? 'Escolha o seu cargo.' : null),
  }));

  /** "Está tudo certo": com algo faltando ou errado, abre os campos e mostra o que corrigir. */
  protected async confirmarDados(etapa: Extract<Etapa, { tipo: 'confirmar' }>) {
    this.tentou.set({ nome: true, email: true, whatsapp: true });
    this.tentouConfirmar.set(true);
    if (!this.dadosValidos() || this.errosServidor()['nome'] || this.errosServidor()['email'] || this.errosServidor()['whatsapp']) {
      this.editandoDados.set(true);
      vibrar(30);
      return;
    }
    this.confirmado.set(true);
    if (etapa.ultimo && !this.sincronizado() && !(await this.sincronizar())) return;
    this.editandoDados.set(false);
    this.irPara(this.indice() + 1);
  }

  protected escolherCargoConfirmacao(valor: string | null) {
    this.perfil.update((p) => ({ ...p, cargo: valor }));
    this.limparErroPerfil();
  }

  protected editou(campo: CampoId) {
    if (this.errosServidor()[campo]) this.errosServidor.update(({ [campo]: _, ...resto }) => resto);
    this.erro.set(null);
  }

  protected async continuarCampo(etapa: Extract<Etapa, { tipo: 'campo' }>) {
    this.tentou.update((t) => ({ ...t, [etapa.campo]: true }));
    if (this.formulario[etapa.campo]().invalid() || this.errosServidor()[etapa.campo]) {
      vibrar(30);
      return;
    }
    if (etapa.ultimo && !this.sincronizado() && !(await this.sincronizar())) return;
    this.irPara(this.indice() + 1);
  }

  /** Cria o diagnóstico (ou atualiza os dados, se o líder voltou e corrigiu algo). */
  private async sincronizar(): Promise<boolean> {
    this.enviando.set(true);
    this.erro.set(null);
    const envio = this.envio();
    try {
      const id = this.diagnosticoId();
      // Convite: sempre pelo link pessoal, que cria ou atualiza e registra o que mudou na auditoria.
      const token = this.token();
      const diagnostico = token
        ? await this.api.iniciarConvite(token, envio.dados, envio.perfil)
        : id
          ? await this.api.atualizarRespondente(id, envio.dados, envio.perfil)
          : await this.api.iniciar(this.codigoCampanha(), envio.dados, envio.perfil);
      this.diagnosticoId.set(diagnostico.id);
      this.dadosSincronizados.set(JSON.stringify(envio));
      return true;
    } catch (e) {
      const problema = problemaDe(e);
      const campos = problema.campos ?? {};
      const primeiro = Object.keys(campos)[0];
      if (primeiro) {
        this.errosServidor.set(campos);
        // No convite, os dados e o cargo ficam na tela de confirmação (que abre para corrigir).
        const chave = this.token() && primeiro !== 'tempoCargo' ? 'confirmar' : (ETAPA_DO_CAMPO[primeiro] ?? primeiro);
        if (chave === 'confirmar') this.editandoDados.set(true);
        const indice = this.etapas().findIndex((et) => et.chave === chave);
        if (indice >= 0 && indice !== this.indice()) this.irPara(indice);
      } else {
        this.erro.set(problema.detail ?? 'Não foi possível continuar. Tente de novo.');
      }
      return false;
    } finally {
      this.enviando.set(false);
    }
  }

  // Cargo e tempo no cargo ---------------------------------------------------
  protected tituloPerfil(perfil: 'cargo' | 'tempo'): string {
    const nome = this.primeiroNome();
    if (perfil === 'tempo') return 'Há quanto tempo você está nesse cargo?';
    return nome ? `${nome}, qual é o seu cargo hoje?` : 'Qual é o seu cargo hoje?';
  }

  protected ajudaPerfil(perfil: 'cargo' | 'tempo'): string {
    if (perfil === 'tempo') return 'Conta só o cargo atual, não o tempo de empresa.';
    const empresa = this.marca()?.nome ?? 'A empresa';
    return `Assim a ${empresa} vê em que nível a carreira dos líderes trava. Ela recebe só números do grupo, nunca o seu resultado.`;
  }

  protected erroPerfil(perfil: 'cargo' | 'tempo'): string | null {
    const erros = this.errosServidor();
    return perfil === 'tempo' ? (erros['tempoCargo'] ?? null) : (erros['cargo'] ?? erros['cargoOutroNome'] ?? null);
  }

  private limparErroPerfil() {
    this.errosServidor.update(({ cargo: _c, cargoOutroNome: _n, tempoCargo: _t, ...resto }) => resto);
    this.erro.set(null);
  }

  protected async escolherPerfil(etapa: Extract<Etapa, { tipo: 'perfil' }>, valor: string) {
    if (this.enviando()) return;
    const anterior = this.perfil()[etapa.perfil];
    this.perfil.update((p) => ({ ...p, [etapa.perfil]: valor }));
    this.limparErroPerfil();
    vibrar();
    // "Não está na lista": espera o nome do cargo (opcional) e o Continuar, já no campo.
    if (etapa.perfil === 'cargo' && valor === CARGO_OUTRO) {
      setTimeout(() => document.getElementById('cargo-outro')?.focus({ preventScroll: false }), 60);
      return;
    }
    this.enviando.set(true);
    await esperar(anterior === valor ? 220 : 420);
    this.enviando.set(false);
    await this.avancarPerfil(etapa);
  }

  protected editouOutroNome(valor: string) {
    this.perfil.update((p) => ({ ...p, outroNome: valor }));
    this.limparErroPerfil();
  }

  protected async avancarPerfil(etapa: Extract<Etapa, { tipo: 'perfil' }>) {
    if (this.perfil()[etapa.perfil] === null || this.enviando()) return;
    if (etapa.ultimo && !this.sincronizado() && !(await this.sincronizar())) return;
    this.irPara(this.indice() + 1);
  }

  // Perguntas ----------------------------------------------------------------
  protected async escolher(etapa: Extract<Etapa, { tipo: 'pergunta' }>, opcao: Opcao) {
    const id = this.diagnosticoId();
    if (!id || this.enviando()) return;
    const perguntaId = etapa.pergunta.id;
    const anterior = this.respostas()[perguntaId];
    const mesma = anterior?.length === 1 && anterior[0] === opcao.id;

    this.respostas.update((r) => ({ ...r, [perguntaId]: [opcao.id] }));
    this.enviando.set(true);
    this.erro.set(null);
    vibrar();
    try {
      // A pausa deixa o líder ver a escolha marcada antes de a tela andar.
      await Promise.all([mesma ? null : this.api.responder(id, perguntaId, [opcao.id]), esperar(mesma ? 220 : 420)]);
    } catch (e) {
      this.respostas.update((r) => {
        const { [perguntaId]: _, ...resto } = r;
        return anterior ? { ...resto, [perguntaId]: anterior } : resto;
      });
      this.erro.set(problemaDe(e).detail ?? 'Não conseguimos salvar a sua resposta. Toque de novo.');
      return;
    } finally {
      this.enviando.set(false);
    }
    this.avancarPergunta(etapa);
  }

  protected avancarPergunta(etapa: Extract<Etapa, { tipo: 'pergunta' }>) {
    if (etapa.ultima) void this.finalizar();
    else this.irPara(this.indice() + 1);
  }

  private async finalizar() {
    const id = this.diagnosticoId();
    if (!id) return;
    this.calculando.set(true);
    this.erro.set(null);
    try {
      await Promise.all([this.api.finalizar(id), esperar(2300)]);
      // Navega antes de mexer no estado: marcar como concluído aqui faria o efeito de
      // correção de URL disputar a navegação com o resultado.
      await this.router.navigate(['/resultado', id], { replaceUrl: true });
    } catch (e) {
      const problema = problemaDe(e);
      this.calculando.set(false);
      const pendente = problema.pendentes?.[0];
      if (pendente) {
        const indice = this.etapas().findIndex((et) => et.tipo === 'pergunta' && et.pergunta.id === pendente);
        if (indice > 0) this.irPara(indice);
      }
      this.erro.set(problema.detail ?? 'Não conseguimos calcular o seu diagnóstico. Tente de novo.');
    }
  }

  protected rotuloCampo(campo: CampoId): string {
    const nome = this.primeiroNome();
    switch (campo) {
      case 'nome':
        return 'Como podemos te chamar?';
      case 'email':
        return nome ? `${nome}, qual é o seu melhor e-mail?` : 'Qual é o seu melhor e-mail?';
      case 'whatsapp':
        return 'E o seu WhatsApp?';
    }
  }

  protected ajudaCampo(campo: CampoId): string | null {
    switch (campo) {
      case 'nome':
        return 'Ele aparece no seu diagnóstico em PDF.';
      case 'email':
        return 'É para ele que enviamos o seu diagnóstico.';
      case 'whatsapp':
        return 'Com DDD.';
    }
  }
}
