import { Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { problemaDe } from '../api/diagnostico-api';
import { Marca } from '../compartilhado/marca';
import { EntrarRh } from './entrar-rh';
import { GestorRh, SessaoRh } from './rh-api';

/**
 * /acompanhar: quem não entrou vê a entrada por código; quem acompanha uma empresa vai direto
 * para ela; quem acompanha mais de uma escolhe.
 */
@Component({
  selector: 'app-acompanhar-pagina',
  imports: [EntrarRh, Marca, RouterLink],
  host: { class: 'block' },
  template: `
    @if (falha(); as f) {
      <div class="mx-auto flex min-h-dvh max-w-md flex-col justify-center px-5 text-center">
        <p class="text-[17px] text-abeto-2" role="alert">{{ f }}</p>
        <button type="button" class="botao-secundario mt-6" (click)="conferir()">Tentar de novo</button>
      </div>
    } @else if (sessao.gestor() === null) {
      <app-entrar-rh (entrou)="seguir($event)" />
    } @else if (sessao.gestor(); as g) {
      @if (g.empresas.length > 1) {
        <div class="mx-auto flex min-h-dvh w-full max-w-md flex-col px-5 pt-5 sm:px-8 sm:pt-8">
          <header class="flex min-h-12 items-center justify-between gap-3">
            <app-marca />
            <button type="button" class="inline-flex min-h-12 items-center px-2 text-[15px] font-semibold text-abeto-2 hover:text-abeto" (click)="sair()">Sair</button>
          </header>
          <p class="rotulo surgir mt-10 text-brasa-escura" style="--i: 0">Acompanhamento da avaliação</p>
          <h1 class="display surgir mt-2 text-[36px] leading-[1.04] font-bold" style="--i: 1">Qual avaliação você quer ver?</h1>
          <p class="surgir mt-3 text-[16px] text-abeto-2" style="--i: 2">{{ g.email }} acompanha {{ g.empresas.length }} empresas.</p>
          <ul class="mt-7 space-y-3">
            @for (e of g.empresas; track e.id; let i = $index) {
              <li class="surgir" [style.--i]="i + 3">
                <a
                  [routerLink]="['/acompanhar', e.id]"
                  class="flex min-h-16 items-center justify-between gap-3 rounded-[20px] bg-white px-5 py-4 text-[17px] font-semibold ring-1 ring-linha transition hover:ring-abeto-3"
                >
                  {{ e.nome }}
                  <svg class="shrink-0 text-abeto-3" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
                </a>
              </li>
            }
          </ul>
        </div>
      }
    } @else {
      <div class="grid min-h-dvh place-items-center" aria-busy="true">
        <span class="size-8 animate-spin rounded-full border-[3px] border-linha border-t-abeto" aria-hidden="true"></span>
        <span class="sr-only">Carregando…</span>
      </div>
    }
  `,
})
export class AcompanharPagina {
  protected readonly sessao = inject(SessaoRh);
  private readonly router = inject(Router);

  /** Para onde voltar depois de entrar (ex.: /acompanhar/3?aba=resultado). */
  readonly voltar = input<string>();

  protected readonly falha = signal<string | null>(null);

  constructor() {
    void this.conferir();
  }

  protected async conferir() {
    this.falha.set(null);
    try {
      const gestor = await this.sessao.conferir(true);
      if (gestor) this.seguir(gestor);
    } catch (e) {
      this.falha.set(problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    }
  }

  protected seguir(gestor: GestorRh) {
    const voltar = this.voltar();
    const destino = voltar && /^\/acompanhar\/\d+/.test(voltar) ? voltar : null;
    if (destino && gestor.empresas.some((e) => destino.startsWith(`/acompanhar/${e.id}`))) {
      void this.router.navigateByUrl(destino, { replaceUrl: true });
    } else if (gestor.empresas.length === 1) {
      void this.router.navigate(['/acompanhar', gestor.empresas[0].id], { replaceUrl: true });
    }
  }

  protected async sair() {
    await this.sessao.sair();
  }
}
