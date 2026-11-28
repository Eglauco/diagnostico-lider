import { Component, computed, input } from '@angular/core';

import { posicaoDaFoto, veuDaFoto } from '../compartilhado/foto-fundo';
import { MarcaEmpresa } from '../compartilhado/tema-marca';

/**
 * A capa da página do RH: a foto da empresa no topo, com a marca e o nome por cima, para o gestor
 * abrir e se sentir em casa. Só aparece quando a empresa tem foto (o administrador envia em Marca
 * e cargos); sem foto, a página continua como era.
 */
@Component({
  selector: 'app-capa-empresa-rh',
  host: { class: 'block' },
  styles: `
    .capa {
      position: relative;
      isolation: isolate;
      overflow: hidden;
      border-radius: 28px;
      background: var(--color-abeto);
      box-shadow: 0 24px 48px -36px rgb(15 43 38 / 0.7);
    }
    .capa img.foto,
    .capa .veu {
      position: absolute;
      inset: 0;
      z-index: -1;
      width: 100%;
      height: 100%;
    }
    .capa img.foto {
      object-fit: cover;
      animation: chegar 1400ms var(--ease-mola) both;
    }
    @keyframes chegar {
      from { opacity: 0; transform: scale(1.05); }
    }
    .vivo {
      position: relative;
      display: block;
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: #4ade80;
    }
    .vivo::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: inherit;
      background: inherit;
      animation: pulsar 2s ease-out infinite;
    }
    @media (prefers-reduced-motion: reduce) {
      .capa img.foto, .vivo::after { animation: none; }
    }
  `,
  template: `
    @if (fundo(); as f) {
      <section class="capa flex h-[140px] items-end px-5 pb-5 text-white sm:h-[168px] sm:px-7 sm:pb-6 lg:h-[188px] lg:px-9 lg:pb-7" [attr.aria-label]="empresa().nome">
        <img class="foto" [src]="f.url" alt="" [style.object-position]="posicao()" decoding="async" fetchpriority="high" />
        <i class="veu" [style.background]="veu" aria-hidden="true"></i>
        <div class="flex min-w-0 items-center gap-3.5 sm:gap-4">
          <span class="grid h-12 min-w-12 shrink-0 place-items-center rounded-2xl bg-white px-2.5 shadow-lg sm:h-14 sm:min-w-14">
            @if (empresa().logo; as logo) {
              <img [src]="logo.url" class="block h-8 w-auto max-w-[140px] object-contain sm:h-9" alt="" />
            } @else {
              <span class="display text-[18px] font-bold text-acao">{{ empresa().sigla }}</span>
            }
          </span>
          <div class="min-w-0">
            <p class="display line-clamp-2 text-[22px] leading-tight font-bold [text-shadow:0_1px_14px_rgb(0_0_0/0.28)] sm:text-[28px] lg:text-[34px]">{{ empresa().nome }}</p>
            <p class="mt-1 flex items-center gap-2 text-[13.5px] font-medium text-white/85 sm:text-[14.5px]">
              <i class="vivo" aria-hidden="true"></i>
              Avaliação dos líderes, ao vivo
            </p>
          </div>
        </div>
      </section>
    }
  `,
})
export class CapaEmpresaRh {
  readonly empresa = input.required<MarcaEmpresa>();

  protected readonly fundo = computed(() => this.empresa().fundo ?? null);
  protected readonly posicao = computed(() => {
    const f = this.fundo();
    return f ? posicaoDaFoto(f) : '50% 50%';
  });
  /** O véu na tinta da marca (a página já aplica o tema da empresa). */
  protected readonly veu = veuDaFoto('var(--color-abeto)', 'capa');
}
