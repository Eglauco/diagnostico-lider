import { httpResource, HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';

import { problemaDe } from '../api/diagnostico-api';
import { CreditoDiagnostico, MarcaEmpresaCabecalho } from '../compartilhado/marca-empresa';
import { MANTER_ROLAGEM } from '../compartilhado/rolagem';
import { aplicarTema, MarcaEmpresa } from '../compartilhado/tema-marca';
import { CapaEmpresaRh } from './capa-empresa-rh';
import { EntrarRh } from './entrar-rh';
import { ParticipacaoRhComponente } from './participacao-rh';
import { ResultadoRhComponente } from './resultado-rh';
import { API_RH, PainelRh, ResultadoRh, SessaoRh } from './rh-api';

/** A cada minuto, enquanto a tela está à vista: a participação muda ao longo do dia. */
const ATUALIZAR_A_CADA = 60_000;

type Aba = 'participacao' | 'resultado';

/**
 * /acompanhar/:empresa — a página do RH de uma empresa (pelo código do link dela, /acompanhar/demo;
 * o número antigo, /acompanhar/1, leva ao código): participação ao vivo e o resultado do grupo
 * (anônimo, em lotes). Quem chega sem ter entrado pede o código aqui mesmo, com a marca da empresa,
 * e fica no mesmo endereço.
 */
@Component({
  selector: 'app-painel-rh',
  imports: [CapaEmpresaRh, MarcaEmpresaCabecalho, CreditoDiagnostico, EntrarRh, ParticipacaoRhComponente, ResultadoRhComponente, RouterLink],
  host: {
    class: 'block',
    '(document:keydown.escape)': 'menuAberto.set(false)',
    '(document:click)': 'fecharMenuFora($event)',
    '(document:visibilitychange)': 'visibilidade()',
  },
  templateUrl: './painel-rh.html',
  styleUrl: './acompanhar.css',
})
export class PainelRhPagina {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly titulo = inject(Title);
  protected readonly sessao = inject(SessaoRh);

  /** O que veio no endereço: o código do link (demo) ou, nos links antigos, o número (1). */
  readonly empresa = input.required<string>();
  readonly aba = input<string>();
  /** ?lider=: a linha do tempo aberta de um líder (o "voltar" do navegador fecha). */
  readonly lider = input<string>();

  protected readonly liderId = computed(() => {
    const id = Number(this.lider());
    return Number.isInteger(id) && id > 0 ? id : null;
  });
  /** Abriu o painel por aqui (e não por um endereço colado): fechar volta no histórico. */
  private abriuAqui = false;

  protected readonly abaAtual = computed<Aba>(() => (this.aba() === 'resultado' ? 'resultado' : 'participacao'));

  /** A empresa do endereço entre as que esta pessoa acompanha (undefined: não acompanha ou não entrou). */
  protected readonly alvo = computed(() => {
    const pedida = this.empresa();
    return this.sessao.gestor()?.empresas.find((e) => e.codigo === pedida || String(e.id) === pedida);
  });
  protected readonly empresaId = computed(() => this.alvo()?.id ?? 0);
  /** O código pedido no endereço (null no número antigo): vai no pedido do código, para o e-mail. */
  protected readonly codigoPedido = computed(() => (/^\d+$/.test(this.empresa()) ? null : this.empresa()));

  /** Não deu para saber quem está neste aparelho (rede): oferece tentar de novo. */
  protected readonly falhaSessao = signal(false);

  protected readonly painel = httpResource<PainelRh>(() => {
    const id = this.alvo()?.id;
    return id ? `${API_RH}/empresas/${id}` : undefined;
  });
  protected readonly resultado = httpResource<ResultadoRh>(() => {
    const id = this.alvo()?.id;
    return id ? `${API_RH}/empresas/${id}/resultado` : undefined;
  });
  /** A marca para a tela de entrada (pública, a mesma do link dos líderes). */
  protected readonly marcaPublica = httpResource<MarcaEmpresa>(() => {
    const pedida = this.empresa();
    return this.sessao.gestor() === null && this.codigoPedido() ? `${API_RH}/marca/${encodeURIComponent(pedida)}` : undefined;
  });

  /**
   * entrar: ninguém neste aparelho. fora: entrou com um e-mail que não acompanha esta empresa (ou
   * ela saiu da lista dele). carregando: conferindo a sessão. ok: a página da empresa.
   */
  protected readonly estado = computed<'carregando' | 'entrar' | 'fora' | 'ok'>(() => {
    const gestor = this.sessao.gestor();
    if (gestor === undefined) return 'carregando';
    if (gestor === null) return 'entrar';
    const erro = this.painel.error();
    if (!this.alvo() || (erro instanceof HttpErrorResponse && erro.status === 404)) return 'fora';
    return 'ok';
  });

  /** Relógio da tela ("há 2 min", "faltam 3 dias"): anda sozinho a cada 30 segundos. */
  protected readonly agora = signal(Date.now());
  protected readonly atualizadoEm = signal<number | null>(null);
  protected readonly menuAberto = signal(false);
  protected readonly atualizando = signal(false);

  protected readonly erro = computed(() => {
    const e = this.painel.error();
    return e ? problemaDe(e) : null;
  });

  protected readonly outrasEmpresas = computed(() => (this.sessao.gestor()?.empresas.length ?? 0) > 1);
  protected readonly inicial = computed(() => (this.sessao.gestor()?.email ?? '?').charAt(0).toUpperCase());

  protected readonly atualizadoTexto = computed(() => {
    const em = this.atualizadoEm();
    if (em === null) return '';
    const segundos = Math.max(0, Math.round((this.agora() - em) / 1000));
    if (segundos < 60) return 'agora';
    const minutos = Math.round(segundos / 60);
    return minutos < 60 ? `há ${minutos} min` : `há ${Math.round(minutos / 60)} h`;
  });

  private visitaRegistrada: number | null = null;
  private relogio: ReturnType<typeof setInterval>;
  private atualizacao: ReturnType<typeof setInterval> | undefined;

  constructor() {
    const destroy = inject(DestroyRef);
    this.relogio = setInterval(() => this.agora.set(Date.now()), 30_000);
    this.agendar();
    destroy.onDestroy(() => {
      clearInterval(this.relogio);
      clearInterval(this.atualizacao);
    });

    void this.conferirSessao();

    // A marca da empresa na tela inteira (e na barra do navegador do celular), também na entrada.
    effect((onCleanup) => {
      const tema =
        this.painel.value()?.empresa.tema ?? (this.estado() === 'entrar' ? (this.marcaPublica.value()?.tema ?? null) : null);
      const desfazer = aplicarTema(tema);
      onCleanup(desfazer);
    });

    // Link antigo pelo número (/acompanhar/1): troca pelo código, sem perder a aba aberta.
    effect(() => {
      const alvo = this.alvo();
      if (alvo?.codigo && this.empresa() !== alvo.codigo) {
        untracked(() =>
          this.router.navigate(['/acompanhar', alvo.codigo], { queryParamsHandling: 'preserve', replaceUrl: true }),
        );
      }
    });

    // O título da aba do navegador acompanha a seção (o roteador repõe o da rota a cada navegação).
    effect(() => {
      const estado = this.estado();
      const nome = this.painel.value()?.empresa.nome ?? this.marcaPublica.value()?.nome;
      if (estado === 'entrar') {
        this.titulo.setTitle(nome ? `Entrar · Acompanhamento · ${nome}` : 'Entrar · Acompanhamento da avaliação');
        return;
      }
      const secao = this.abaAtual() === 'resultado' ? 'Resultado do grupo' : 'Participação';
      // Com a linha do tempo de um líder aberta, o nome dele vem na frente.
      const id = this.liderId();
      const lider = id ? this.painel.value()?.participacao.grupos.flatMap((g) => g.lideres).find((l) => l.id === id) : undefined;
      if (nome && estado === 'ok') this.titulo.setTitle(lider ? `${lider.nome} · ${secao} · ${nome}` : `${secao} · ${nome}`);
    });

    effect(() => {
      const p = this.painel.value();
      if (!p) return;
      untracked(() => {
        this.atualizadoEm.set(Date.now());
        this.atualizando.set(false);
        const id = this.empresaId();
        if (id && this.visitaRegistrada !== id) {
          this.visitaRegistrada = id;
          this.http.post(`${API_RH}/empresas/${id}/visita`, null).subscribe({ error: () => undefined });
        }
      });
    });

    // Acesso vencido ou retirado: a entrada aparece aqui mesmo, no endereço da empresa.
    effect(() => {
      const erros = [this.painel.error(), this.resultado.error()];
      if (erros.some((e) => e instanceof HttpErrorResponse && e.status === 401)) {
        untracked(() => this.sessao.encerrada());
      }
    });
  }

  protected async conferirSessao() {
    this.falhaSessao.set(false);
    try {
      await this.sessao.conferir();
    } catch {
      this.falhaSessao.set(true);
    }
  }

  /** Entrou pelo código: a página da empresa carrega no mesmo endereço. */
  protected entrou() {
    this.menuAberto.set(false);
  }

  /** "Entrar com outro e-mail": sai deste aparelho e mostra a entrada, sem sair da empresa. */
  protected async trocarEmail() {
    this.menuAberto.set(false);
    await this.sessao.sair();
  }

  protected abrirLider(id: number) {
    this.abriuAqui = true;
    void this.router.navigate([], { queryParams: { lider: id }, queryParamsHandling: 'merge', state: MANTER_ROLAGEM });
  }

  protected trocarLider(id: number) {
    void this.router.navigate([], { queryParams: { lider: id }, queryParamsHandling: 'merge', replaceUrl: true, state: MANTER_ROLAGEM });
  }

  protected fecharLider() {
    if (this.abriuAqui) {
      this.abriuAqui = false;
      history.back();
      return;
    }
    void this.router.navigate([], { queryParams: { lider: null }, queryParamsHandling: 'merge', replaceUrl: true, state: MANTER_ROLAGEM });
  }

  protected trocarAba(aba: Aba) {
    if (aba === this.abaAtual()) return;
    void this.router.navigate([], { queryParams: { aba: aba === 'participacao' ? null : aba }, replaceUrl: true });
    window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }

  protected atualizarAgora() {
    this.atualizando.set(true);
    this.painel.reload();
    this.resultado.reload();
  }

  protected async sair() {
    this.menuAberto.set(false);
    await this.sessao.sair();
    void this.router.navigate(['/acompanhar'], { replaceUrl: true });
  }

  protected fecharMenuFora(evento: Event) {
    if (!this.menuAberto()) return;
    const alvo = evento.target as HTMLElement | null;
    if (!alvo?.closest('[data-menu-conta]')) this.menuAberto.set(false);
  }

  protected visibilidade() {
    if (document.visibilityState === 'visible') {
      this.agora.set(Date.now());
      if (this.alvo()) {
        this.painel.reload();
        this.resultado.reload();
      }
      this.agendar();
    } else {
      clearInterval(this.atualizacao);
    }
  }

  private agendar() {
    clearInterval(this.atualizacao);
    this.atualizacao = setInterval(() => {
      if (document.visibilityState !== 'visible' || !this.alvo()) return;
      this.painel.reload();
      this.resultado.reload();
    }, ATUALIZAR_A_CADA);
  }
}
