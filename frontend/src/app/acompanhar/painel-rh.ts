import { httpResource, HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';

import { problemaDe } from '../api/diagnostico-api';
import { CreditoDiagnostico, MarcaEmpresaCabecalho } from '../compartilhado/marca-empresa';
import { aplicarTema } from '../compartilhado/tema-marca';
import { ParticipacaoRhComponente } from './participacao-rh';
import { ResultadoRhComponente } from './resultado-rh';
import { API_RH, PainelRh, ResultadoRh, SessaoRh } from './rh-api';

/** A cada minuto, enquanto a tela está à vista: a participação muda ao longo do dia. */
const ATUALIZAR_A_CADA = 60_000;

type Aba = 'participacao' | 'resultado';

/**
 * /acompanhar/:empresa — a página do RH de uma empresa: participação ao vivo e o resultado do
 * grupo (anônimo, em lotes). Com a marca da empresa, como o link dos líderes.
 */
@Component({
  selector: 'app-painel-rh',
  imports: [MarcaEmpresaCabecalho, CreditoDiagnostico, ParticipacaoRhComponente, ResultadoRhComponente, RouterLink],
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

  readonly empresa = input.required<string>();
  readonly aba = input<string>();

  protected readonly abaAtual = computed<Aba>(() => (this.aba() === 'resultado' ? 'resultado' : 'participacao'));
  protected readonly empresaId = computed(() => Number(this.empresa()));

  protected readonly painel = httpResource<PainelRh>(() => `${API_RH}/empresas/${this.empresa()}`);
  protected readonly resultado = httpResource<ResultadoRh>(() => `${API_RH}/empresas/${this.empresa()}/resultado`);

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

  private visitaRegistrada: string | null = null;
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

    // A marca da empresa na tela inteira (e na barra do navegador do celular).
    effect((onCleanup) => {
      const tema = this.painel.value()?.empresa.tema ?? null;
      const desfazer = aplicarTema(tema);
      onCleanup(desfazer);
    });

    // Quem entrou direto por aqui (aparelho lembrado): o e-mail para o menu da conta.
    void this.sessao.conferir().catch(() => undefined);

    // O título da aba do navegador acompanha a seção (o roteador repõe o da rota a cada navegação).
    effect(() => {
      const nome = this.painel.value()?.empresa.nome;
      const secao = this.abaAtual() === 'resultado' ? 'Resultado do grupo' : 'Participação';
      if (nome) this.titulo.setTitle(`${secao} · ${nome}`);
    });

    effect(() => {
      const p = this.painel.value();
      if (!p) return;
      untracked(() => {
        this.atualizadoEm.set(Date.now());
        this.atualizando.set(false);
        if (this.visitaRegistrada !== this.empresa()) {
          this.visitaRegistrada = this.empresa();
          this.http.post(`${API_RH}/empresas/${this.empresa()}/visita`, null).subscribe({ error: () => undefined });
        }
      });
    });

    // Acesso vencido ou retirado: volta para a entrada, lembrando onde estava.
    effect(() => {
      const erros = [this.painel.error(), this.resultado.error()];
      if (erros.some((e) => e instanceof HttpErrorResponse && e.status === 401)) {
        untracked(() => {
          this.sessao.encerrada();
          void this.router.navigate(['/acompanhar'], { queryParams: { voltar: this.router.url }, replaceUrl: true });
        });
      }
    });
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
      this.painel.reload();
      this.resultado.reload();
      this.agendar();
    } else {
      clearInterval(this.atualizacao);
    }
  }

  private agendar() {
    clearInterval(this.atualizacao);
    this.atualizacao = setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      this.painel.reload();
      this.resultado.reload();
    }, ATUALIZAR_A_CADA);
  }
}
