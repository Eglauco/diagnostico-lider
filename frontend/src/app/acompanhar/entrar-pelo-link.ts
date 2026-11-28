import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { problemaDe } from '../api/diagnostico-api';
import { Marca } from '../compartilhado/marca';
import { GestorRh, SessaoRh } from './rh-api';

/**
 * /acompanhar/entrar?empresa=demo#token: o botão do e-mail. O token vem depois do # (não passa pelo
 * servidor nem por quem pré-visualiza links) e é trocado pelo acesso com um POST, uma vez só.
 * ?empresa= é a empresa do e-mail: depois de entrar, a página abre nela.
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
          <a [routerLink]="empresa ? ['/acompanhar', empresa] : ['/acompanhar']" class="botao-primario surgir mt-8" style="--i: 3">Receber um código</a>
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
  /** Para onde ir depois de entrar: a empresa do e-mail, se a pessoa a acompanha. */
  protected readonly empresa = new URLSearchParams(location.search).get('empresa');

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
      this.seguir(await this.sessao.entrar({ link }));
    } catch (e) {
      // Quem já está lembrado neste aparelho não precisa do botão.
      const gestor = await this.sessao.conferir(true).catch(() => null);
      if (gestor) {
        this.seguir(gestor);
        return;
      }
      this.erro.set(problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    }
  }

  private seguir(gestor: GestorRh) {
    const destino = this.empresa ? gestor.empresas.find((e) => e.codigo === this.empresa) : undefined;
    void this.router.navigate(destino ? ['/acompanhar', destino.codigo] : ['/acompanhar'], { replaceUrl: true });
  }
}
