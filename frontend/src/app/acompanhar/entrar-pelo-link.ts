import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { problemaDe } from '../api/diagnostico-api';
import { Marca } from '../compartilhado/marca';
import { SessaoRh } from './rh-api';

/**
 * /acompanhar/entrar#token: o botão do e-mail. O token vem depois do # (não passa pelo servidor nem
 * por quem pré-visualiza links) e é trocado pelo acesso com um POST, uma vez só.
 */
@Component({
  selector: 'app-entrar-pelo-link',
  imports: [Marca, RouterLink],
  host: { class: 'block' },
  template: `
    <div class="mx-auto flex min-h-dvh w-full max-w-md flex-col px-5 pt-5 sm:px-8 sm:pt-8">
      <header class="flex min-h-12 items-center"><app-marca /></header>
      <div class="flex flex-1 flex-col justify-center pb-24">
        @if (erro(); as e) {
          <p class="rotulo surgir text-brasa-escura" style="--i: 0">Acompanhamento da avaliação</p>
          <h1 class="display surgir mt-2 text-[34px] leading-[1.05] font-bold" style="--i: 1">Este botão não entra mais</h1>
          <p class="surgir mt-4 text-[17px] leading-snug text-abeto-2" style="--i: 2" role="alert">{{ e }}</p>
          <a routerLink="/acompanhar" class="botao-primario surgir mt-8" style="--i: 3">Receber um código</a>
        } @else {
          <div class="flex flex-col items-center gap-4 text-center" aria-busy="true">
            <span class="size-9 animate-spin rounded-full border-[3px] border-linha border-t-abeto" aria-hidden="true"></span>
            <p class="text-[17px] text-abeto-2" role="status">Entrando…</p>
          </div>
        }
      </div>
    </div>
  `,
})
export class EntrarPeloLink {
  private readonly sessao = inject(SessaoRh);
  private readonly router = inject(Router);

  protected readonly erro = signal<string | null>(null);

  constructor() {
    const link = location.hash.replace(/^#/, '');
    // O token sai da barra de endereço na hora: não fica no histórico nem num print da tela.
    history.replaceState(history.state, '', location.pathname);
    if (!link) {
      void this.router.navigate(['/acompanhar'], { replaceUrl: true });
      return;
    }
    void this.entrar(link);
  }

  private async entrar(link: string) {
    try {
      // A sessão entrega o cookie CSRF que o POST precisa (e diz se já está lembrado aqui).
      await this.sessao.conferir(true);
      await this.sessao.entrar({ link });
      void this.router.navigate(['/acompanhar'], { replaceUrl: true });
    } catch (e) {
      // Quem já está lembrado neste aparelho não precisa do botão.
      const gestor = await this.sessao.conferir(true).catch(() => null);
      if (gestor) {
        void this.router.navigate(['/acompanhar'], { replaceUrl: true });
        return;
      }
      this.erro.set(problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    }
  }
}
