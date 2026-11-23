import { Component, computed, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { iniciais, ROTA_ENTRAR, SessaoPainel } from './sessao';

/** Avatar no cabeçalho: quem está logado, usuários do painel, trocar senha e sair. */
@Component({
  selector: 'app-menu-conta',
  imports: [RouterLink],
  styleUrl: '../admin.css',
  host: {
    class: 'relative block',
    '(document:click)': 'cliqueFora($event)',
    '(document:keydown.escape)': 'fechar(true)',
  },
  template: `
    <button
      #botao
      type="button"
      class="avatar size-10 text-[14px] ring-2 ring-white transition-transform hover:scale-105"
      aria-controls="menu-conta"
      [attr.aria-expanded]="aberto()"
      [attr.aria-label]="'Sua conta: ' + (usuario()?.nome ?? '')"
      (click)="aberto.update((a) => !a)"
    >
      {{ sigla() }}
    </button>

    @if (aberto() && usuario(); as u) {
      <div id="menu-conta" class="aparecer absolute top-12 right-0 z-40 w-72 overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-linha">
        <div class="flex items-center gap-3 px-4 pt-4 pb-3.5">
          <span class="avatar size-10 text-[14px]" aria-hidden="true">{{ sigla() }}</span>
          <div class="min-w-0">
            <p class="truncate text-[15px] font-semibold">{{ u.nome }}</p>
            <p class="truncate text-[13px] text-abeto-2">{{ u.email }}</p>
          </div>
        </div>
        <ul class="border-t border-linha py-1.5">
          <li>
            <a routerLink="/administrador/usuarios" class="flex min-h-11 items-center gap-3 px-4 text-[15px] font-medium hover:bg-nevoa-2" (click)="fechar()">
              <svg class="text-abeto-2" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="9" cy="8" r="3.5" /><path d="M3 19.5c.8-3 3.2-4.5 6-4.5s5.2 1.5 6 4.5M16 4.8a3.5 3.5 0 0 1 0 6.4M18 15.3c1.4.7 2.5 2.1 3 4.2" stroke-linecap="round" /></svg>
              Usuários do painel
            </a>
          </li>
          <li>
            <button type="button" class="flex min-h-11 w-full items-center gap-3 px-4 text-left text-[15px] font-medium hover:bg-nevoa-2" (click)="pedirTrocaDeSenha()">
              <svg class="text-abeto-2" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
              Trocar minha senha
            </button>
          </li>
          <li>
            <a href="/" target="_blank" rel="noopener" class="flex min-h-11 items-center gap-3 px-4 text-[15px] font-medium hover:bg-nevoa-2" (click)="fechar()">
              <svg class="text-abeto-2" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M7 17L17 7M9 7h8v8" stroke-linecap="round" stroke-linejoin="round" /></svg>
              Abrir o diagnóstico<span class="sr-only"> (abre em nova aba)</span>
            </a>
          </li>
        </ul>
        <div class="border-t border-linha py-1.5">
          <button type="button" class="flex min-h-11 w-full items-center gap-3 px-4 text-left text-[15px] font-semibold text-[#A52B40] hover:bg-travada-suave" [disabled]="saindo()" (click)="sair()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10" stroke-linecap="round" stroke-linejoin="round" /></svg>
            {{ saindo() ? 'Saindo…' : 'Sair' }}
          </button>
        </div>
      </div>
    }
  `,
})
export class MenuConta {
  private readonly sessao = inject(SessaoPainel);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly botao = viewChild.required<ElementRef<HTMLButtonElement>>('botao');

  readonly trocarSenha = output();

  protected readonly usuario = this.sessao.usuario;
  protected readonly sigla = computed(() => iniciais(this.usuario()?.nome ?? ''));
  protected readonly aberto = signal(false);
  protected readonly saindo = signal(false);

  protected cliqueFora(evento: MouseEvent) {
    if (this.aberto() && !this.host.nativeElement.contains(evento.target as Node)) this.aberto.set(false);
  }

  protected fechar(devolverFoco = false) {
    if (!this.aberto()) return;
    this.aberto.set(false);
    if (devolverFoco) this.botao().nativeElement.focus();
  }

  protected pedirTrocaDeSenha() {
    this.fechar();
    this.trocarSenha.emit();
  }

  protected async sair() {
    this.saindo.set(true);
    try {
      await this.sessao.sair();
    } finally {
      this.saindo.set(false);
      this.aberto.set(false);
      await this.router.navigateByUrl(ROTA_ENTRAR, { replaceUrl: true });
    }
  }
}
