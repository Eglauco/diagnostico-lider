import { Component, computed, DestroyRef, effect, inject, untracked, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta } from '@angular/platform-browser';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { Marca } from '../compartilhado/marca';
import { DialogoMinhaSenha } from './acesso/dialogo-minha-senha';
import { MenuConta } from './acesso/menu-conta';
import { SessaoPainel } from './acesso/sessao';

type Secao = 'empresas' | 'geral' | 'usuarios' | null;

@Component({
  selector: 'app-admin-layout',
  imports: [DialogoMinhaSenha, Marca, MenuConta, RouterLink, RouterOutlet],
  host: { class: 'block min-h-dvh' },
  styles: `
    .aba {
      align-items: center;
      min-height: 36px;
      padding: 0 14px;
      border-radius: 999px;
      color: var(--color-abeto-2);
      font-size: 14px;
      font-weight: 600;
      transition: background-color 180ms, color 180ms;
    }
    .aba:hover { color: var(--color-abeto); }
    .aba[aria-current='page'] { background: var(--color-abeto); color: #fff; }
  `,
  template: `
    <header class="sticky top-0 z-30 border-b border-linha/80 bg-nevoa/85 backdrop-blur-md">
      <div class="mx-auto flex h-16 max-w-6xl items-center justify-between gap-2 px-4 sm:px-6">
        <a routerLink="/administrador/empresas" class="flex shrink-0 items-center gap-3 rounded-xl" aria-label="Painel do administrador, início (empresas)">
          <app-marca [compacta]="true" />
          <span class="hidden rounded-full bg-abeto px-2.5 py-1 text-[11px] font-semibold tracking-[0.08em] text-white uppercase md:inline">Admin</span>
        </a>

        <nav class="flex items-center gap-0.5 rounded-full bg-white/70 p-1 ring-1 ring-linha" aria-label="Seções do painel">
          <a routerLink="/administrador/empresas" class="aba inline-flex" [attr.aria-current]="secao() === 'empresas' ? 'page' : null">Empresas</a>
          <!-- No celular, "Geral" para caber ao lado da marca e da conta. -->
          <a routerLink="/administrador/geral" class="aba inline-flex" aria-label="Diagnóstico Geral" [attr.aria-current]="secao() === 'geral' ? 'page' : null">
            <span class="sm:hidden">Geral</span><span class="hidden sm:inline">Diagnóstico Geral</span>
          </a>
          <a routerLink="/administrador/usuarios" class="aba hidden sm:inline-flex" [attr.aria-current]="secao() === 'usuarios' ? 'page' : null">Usuários</a>
        </nav>

        <app-menu-conta (trocarSenha)="dialogoSenha().abrir()" />
      </div>
    </header>
    <router-outlet />
    <app-dialogo-minha-senha />
  `,
})
export class AdminLayout {
  private readonly router = inject(Router);
  private readonly sessao = inject(SessaoPainel);
  protected readonly dialogoSenha = viewChild.required(DialogoMinhaSenha);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly secao = computed<Secao>(() => {
    const url = this.url();
    if (url.startsWith('/administrador/geral')) return 'geral';
    if (url.startsWith('/administrador/usuarios')) return 'usuarios';
    // O diagnóstico de um líder pode ser de uma empresa ou do link geral: nenhuma aba acesa.
    if (url.startsWith('/administrador/diagnosticos')) return null;
    return 'empresas';
  });

  constructor() {
    // Painel interno: fora dos buscadores.
    const meta = inject(Meta);
    meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
    inject(DestroyRef).onDestroy(() => meta.removeTag('name="robots"'));

    // Outras telas (ex.: Usuários) pedem para abrir "Trocar minha senha".
    let inicial = true;
    effect(() => {
      this.sessao.pedidoTrocaDeSenha();
      if (inicial) {
        inicial = false;
        return;
      }
      untracked(() => this.dialogoSenha().abrir());
    });
  }
}
