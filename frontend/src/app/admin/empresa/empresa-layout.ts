import { Component, computed, DestroyRef, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { EmpresaAtual } from './empresa-atual';
import { prazoDaAvaliacao } from './prazo';

type Aba = 'visao' | 'diagnostico' | 'lideres' | 'editar';

const TITULO_ABA: Record<Aba, string> = {
  visao: 'Visão geral',
  diagnostico: 'Diagnóstico',
  lideres: 'Líderes',
  editar: 'Marca e cargos',
};

/**
 * /administrador/empresas/:id — tudo da empresa num lugar só. O cabeçalho (marca, prazo, quantos
 * responderam, link) fica parado; as abas trocam embaixo: Visão geral, Diagnóstico, Líderes e
 * Marca e cargos. No celular, as abas rolam de lado e grudam no topo ao rolar a página.
 */
@Component({
  selector: 'app-empresa-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  providers: [EmpresaAtual],
  host: { class: 'block' },
  styleUrl: '../admin.css',
  styles: `
    .aba-empresa {
      position: relative;
      display: inline-flex;
      flex: none;
      align-items: center;
      gap: 8px;
      min-height: 52px;
      padding: 0 14px;
      color: var(--color-abeto-2);
      font-size: 15px;
      font-weight: 600;
      white-space: nowrap;
      transition: color 180ms;
    }
    .aba-empresa:hover {
      color: var(--color-abeto);
    }
    .aba-empresa[aria-current='page'] {
      color: var(--color-abeto);
    }
    .aba-empresa[aria-current='page']::after {
      content: '';
      position: absolute;
      right: 12px;
      bottom: -1px;
      left: 12px;
      height: 3px;
      border-radius: 3px 3px 0 0;
      background: var(--color-abeto);
    }
    .aba-empresa .contagem {
      min-width: 24px;
      padding: 1px 7px;
      border-radius: 999px;
      background: rgb(15 43 38 / 0.07);
      font-size: 12.5px;
      font-variant-numeric: tabular-nums;
      text-align: center;
    }
    .aba-empresa[aria-current='page'] .contagem {
      background: var(--color-abeto);
      color: #fff;
    }
  `,
  template: `
    <div class="mx-auto max-w-6xl px-4 pt-6 sm:px-6">
      <a
        routerLink="/administrador/empresas"
        class="-ml-2 inline-flex min-h-11 items-center gap-1.5 rounded-full px-2 text-[15px] font-semibold text-abeto-2 transition-colors hover:text-abeto"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        Empresas
      </a>

      @if (atual.empresa.error() && !dados()) {
        <div class="cartao mt-6 mb-20 p-8 text-center" role="alert">
          <p class="text-[18px] font-semibold">Não encontramos esta empresa.</p>
          <a routerLink="/administrador/empresas" class="botao-secundario mt-5">Ver todas as empresas</a>
        </div>
      } @else if (dados(); as e) {
        <header class="mt-3 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <!-- No celular, o prazo e o progresso ganham a largura toda, embaixo da marca e do nome. -->
          <div class="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-center gap-x-4 gap-y-3 sm:gap-y-2">
            @if (e.logo; as logo) {
              <!-- Caixa de tamanho fixo e a logo ocupando-a com object-contain: quadrada ou larga, fica dentro. -->
              <span class="block h-16 w-20 shrink-0 overflow-hidden rounded-2xl bg-white p-2 ring-1 ring-linha sm:row-span-2 sm:w-24">
                <img [src]="logo.url" class="block size-full object-contain" [alt]="'Logomarca de ' + e.nome" />
              </span>
            } @else {
              <span class="display grid size-16 shrink-0 place-items-center rounded-2xl text-[22px] sm:row-span-2 font-bold" [style.background]="e.tema.acao" [style.color]="e.tema.sobreAcao" aria-hidden="true">{{ sigla() }}</span>
            }
            <h1 class="display min-w-0 text-[30px] leading-[1.02] font-bold break-words sm:self-end sm:text-[40px]">{{ e.nome }}</h1>
              <div class="col-span-2 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[14px] text-abeto-2 sm:col-span-1 sm:col-start-2 sm:self-start">
                @if (prazo(); as pz) {
                  <a [routerLink]="['/administrador/empresas', id(), 'editar']" fragment="encerramento-secao" class="inline-flex min-h-7 items-center gap-2 hover:text-abeto" title="Mudar a data de encerramento">
                    <i class="block size-2 shrink-0 rounded-full" [class]="pz.aberta ? (pz.urgente ? 'bg-travada' : 'bg-ascensao') : 'bg-abeto-3'" aria-hidden="true"></i>
                    <span [class.font-semibold]="pz.urgente" [class.text-travada]="pz.urgente">{{ pz.texto }}</span>
                  </a>
                } @else {
                  <a [routerLink]="['/administrador/empresas', id(), 'editar']" fragment="encerramento-secao" class="inline-flex min-h-7 items-center gap-2 hover:text-abeto">
                    <i class="block size-2 shrink-0 rounded-full bg-ascensao" aria-hidden="true"></i>
                    Aberta, sem data para encerrar
                  </a>
                }
                @if (e.indicadores.lideres) {
                  <span class="inline-flex min-h-7 items-center gap-2.5">
                    <span class="block h-1.5 w-16 overflow-hidden rounded-full bg-linha/70" aria-hidden="true">
                      <span class="block h-full rounded-full bg-ascensao transition-[width] duration-700" [style.width.%]="progresso()"></span>
                    </span>
                    <span><strong class="font-semibold text-abeto tabular-nums">{{ e.indicadores.lideresConcluiram }} de {{ e.indicadores.lideres }}</strong> {{ e.indicadores.lideres === 1 ? 'líder respondeu' : 'líderes responderam' }}</span>
                  </span>
                } @else {
                  <span class="inline-flex min-h-7 items-center">Nenhum líder ainda</span>
                }
              </div>
          </div>

          @if (e.link; as l) {
            <div class="flex items-center gap-2 lg:shrink-0">
              <button type="button" class="botao-secundario min-h-12 flex-1 bg-white px-5 text-[15px] sm:flex-none" (click)="copiarLink(l.url)">
                @if (copiado()) {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  <span>Link copiado</span>
                } @else {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1" stroke-linecap="round" /><path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1" stroke-linecap="round" /></svg>
                  <span>Copiar link</span>
                }
              </button>
              <a [href]="l.url" target="_blank" rel="noopener" class="grid size-12 shrink-0 place-items-center rounded-full bg-white text-abeto ring-1 ring-linha transition hover:ring-abeto-3" title="Abrir como o líder vê">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M14 5h5v5M19 5l-8 8M18 14v4a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18V7.5A1.5 1.5 0 0 1 5.5 6H10" stroke-linecap="round" stroke-linejoin="round" /></svg>
                <span class="sr-only">Abrir o link como o líder vê (abre em nova aba)</span>
              </a>
            </div>
          }
        </header>
        @if (falhaCopia(); as url) {
          <p class="aparecer mt-3 rounded-2xl bg-white px-4 py-3 text-[15px] ring-1 ring-linha" role="alert">
            O navegador não deixou copiar. Selecione e copie: <strong class="font-semibold break-all select-all">{{ url }}</strong>
          </p>
        }
      } @else {
        <div class="mt-3 flex items-center gap-4" aria-hidden="true">
          <div class="size-16 animate-pulse rounded-2xl bg-white/70"></div>
          <div class="flex-1 space-y-2.5">
            <div class="h-9 w-2/3 animate-pulse rounded-xl bg-white/70"></div>
            <div class="h-5 w-1/2 animate-pulse rounded-lg bg-white/60"></div>
          </div>
        </div>
      }
    </div>

    @if (!atual.empresa.error() || dados()) {
      <!-- As abas: grudam embaixo do cabeçalho do painel ao rolar. -->
      <nav class="sticky top-16 z-20 mt-5 border-b border-linha/80 bg-nevoa/90 backdrop-blur-md" aria-label="Seções da empresa">
        <div #abas class="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-2 [scrollbar-width:none] sm:px-4">
          <a class="aba-empresa" [routerLink]="['/administrador/empresas', id()]" routerLinkActive [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page">Visão geral</a>
          <a class="aba-empresa" [routerLink]="['/administrador/empresas', id(), 'diagnostico']" routerLinkActive ariaCurrentWhenActive="page">
            Diagnóstico
            @if (dados(); as e) {
              <span class="contagem">{{ e.indicadores.total }}</span>
            }
          </a>
          <a class="aba-empresa" [routerLink]="['/administrador/empresas', id(), 'lideres']" routerLinkActive ariaCurrentWhenActive="page">
            Líderes
            @if (dados(); as e) {
              <span class="contagem">{{ e.indicadores.lideres }}</span>
            }
          </a>
          <a class="aba-empresa" [routerLink]="['/administrador/empresas', id(), 'editar']" routerLinkActive ariaCurrentWhenActive="page">Marca e cargos</a>
        </div>
      </nav>

      <!--
        O router-outlet fica dentro de um div: direto no @if, o Angular criava a aba no namespace do
        bloco, que nasce logo depois do ícone (svg) do link "Empresas", e a aba virava SVG, invisível.
      -->
      <div>
        <router-outlet />
      </div>
    }

    <p class="sr-only" aria-live="polite">{{ copiado() ? 'Link copiado.' : '' }}</p>
  `,
})
export class EmpresaLayout {
  private readonly router = inject(Router);
  private readonly faixaAbas = viewChild<ElementRef<HTMLElement>>('abas');
  protected readonly atual = inject(EmpresaAtual);

  readonly id = input.required<string>();

  protected readonly dados = computed(() => (this.atual.empresa.hasValue() ? this.atual.empresa.value() : undefined));
  protected readonly copiado = signal(false);
  /** O navegador não deixou copiar: mostra o link para copiar à mão. */
  protected readonly falhaCopia = signal<string | null>(null);

  /** O caminho sem filtros: trocar só os filtros da lista não conta como trocar de aba. */
  private readonly caminho = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects.split(/[?#]/)[0]),
    ),
    { initialValue: this.router.url.split(/[?#]/)[0] },
  );
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly aba = computed<Aba>(() => {
    const final = this.caminho().split('/').pop();
    return final === 'diagnostico' || final === 'lideres' || final === 'editar' ? final : 'visao';
  });

  protected readonly sigla = computed(() =>
    (this.dados()?.nome ?? '')
      .split(/\s+/)
      .filter((p) => p.length > 2 || /^[A-Z0-9]/.test(p))
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join(''),
  );

  protected readonly progresso = computed(() => {
    const i = this.dados()?.indicadores;
    return i?.lideres ? Math.round((i.lideresConcluiram / i.lideres) * 100) : 0;
  });

  /** "Aberta até 30 de nov. · faltam 3 dias", "Encerra hoje, às 22h" ou "Encerrada em 30 de nov.". */
  protected readonly prazo = computed(() => {
    const enc = this.dados()?.encerramento;
    return enc ? prazoDaAvaliacao(enc) : null;
  });

  constructor() {
    effect(() => this.atual.id.set(this.id()));

    // Ao trocar de aba (não de filtro), o cabeçalho pega os números novos: quem acabou de
    // responder, o líder adicionado, a marca editada.
    let anterior: string | null = null;
    effect(() => {
      const caminho = this.caminho();
      if (anterior !== null && anterior !== caminho) untracked(() => this.atual.recarregar());
      anterior = caminho;
    });

    // O título da aba do navegador: "Diagnóstico · Empresa X". O roteador repõe o da rota a cada
    // navegação (até nos filtros), por isso depende da URL inteira.
    const titulo = inject(Title);
    effect(() => {
      this.url();
      const nome = this.dados()?.nome;
      if (!nome) return;
      const aba = this.aba();
      titulo.setTitle(aba === 'visao' ? `${nome} · Empresas · Administrador` : `${TITULO_ABA[aba]} · ${nome} · Administrador`);
    });

    // No celular, a aba aberta fica à vista na faixa que rola de lado (só na horizontal: a página
    // não sai do lugar, nem quando o endereço leva a uma seção, como #encerramento-secao).
    effect(() => {
      this.aba();
      const faixa = this.faixaAbas()?.nativeElement;
      if (!faixa) return;
      queueMicrotask(() => {
        const ativa = faixa.querySelector<HTMLElement>('[aria-current="page"]');
        if (!ativa) return;
        const fora =
          ativa.offsetLeft < faixa.scrollLeft || ativa.offsetLeft + ativa.offsetWidth > faixa.scrollLeft + faixa.clientWidth;
        if (fora) faixa.scrollTo({ left: ativa.offsetLeft - 16 });
      });
    });

    inject(DestroyRef).onDestroy(() => clearTimeout(this.esperaCopia));
  }

  private esperaCopia: ReturnType<typeof setTimeout> | undefined;

  protected async copiarLink(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      this.falhaCopia.set(null);
      this.copiado.set(true);
      clearTimeout(this.esperaCopia);
      this.esperaCopia = setTimeout(() => this.copiado.set(false), 2000);
    } catch {
      this.falhaCopia.set(url);
    }
  }
}
