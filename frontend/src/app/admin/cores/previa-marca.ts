import { Component, computed, input } from '@angular/core';

import { geometriaCurva } from '../../compartilhado/curva-carreira';
import { LogoEmpresa, logoPedeNome, TemaMarca } from '../../compartilhado/tema-marca';

/**
 * A primeira tela do questionário num celular, já com a paleta: o que o líder vai ver.
 * As variáveis do tema valem só dentro do aparelho; o painel continua com as cores dele.
 */
@Component({
  selector: 'app-previa-marca',
  host: { class: 'block' },
  template: `
    <div
      class="aparelho"
      role="img"
      [attr.aria-label]="'Prévia do questionário de ' + (nome() || 'empresa') + ' com as cores escolhidas'"
      [style.--color-acao]="tema().acao"
      [style.--color-sobre-acao]="tema().sobreAcao"
      [style.--color-abeto]="tema().tinta"
      [style.--color-abeto-2]="tema().tinta2"
      [style.--color-nevoa]="tema().fundo"
      [style.--color-linha]="tema().linha"
      [style.--color-brasa]="tema().destaque"
      [style.--color-brasa-escura]="tema().destaqueTexto"
      [style.--color-sobre-brasa]="tema().sobreDestaque"
    >
      <div class="tela bg-nevoa text-abeto">
        <div class="flex h-7 items-center gap-2">
          @if (logo(); as l) {
            <img [src]="l.url" class="block h-7 w-auto max-w-[120px] shrink-0 object-contain" alt="" />
          } @else {
            <span class="grid size-6 shrink-0 place-items-center rounded-[7px] bg-acao text-[9.5px] font-bold text-sobre-acao">{{ sigla() }}</span>
          }
          @if (!logo() || pedeNome()) {
            <span class="truncate text-[11.5px] font-semibold">{{ nome() || 'Nome da empresa' }}</span>
          }
        </div>
        <p class="mt-5 text-[8.5px] font-semibold tracking-[0.12em] text-brasa-escura uppercase">Diagnóstico de carreira para líderes</p>
        <p class="display mt-1.5 text-[21px] leading-[1] font-bold">Em que ponto da curva está a sua carreira?</p>
        <svg class="mt-3 block w-full" [attr.viewBox]="'0 0 ' + curva.largura + ' ' + curva.altura" aria-hidden="true">
          <defs>
            <linearGradient [attr.id]="idGradiente" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" style="stop-color: var(--color-brasa)" stop-opacity="0.22" />
              <stop offset="1" style="stop-color: var(--color-brasa)" stop-opacity="0" />
            </linearGradient>
          </defs>
          <line [attr.x1]="curva.margemX" [attr.x2]="curva.largura - curva.margemX" [attr.y1]="curva.base" [attr.y2]="curva.base" style="stroke: var(--color-linha)" stroke-width="1.5" />
          <path [attr.d]="curva.area" [attr.fill]="'url(#' + idGradiente + ')'" />
          <path [attr.d]="curva.curva" fill="none" style="stroke: var(--color-brasa)" stroke-width="5" stroke-linecap="round" />
          <circle [attr.cx]="ponto.x" [attr.cy]="ponto.y" r="9" style="fill: var(--color-abeto)" stroke="#fff" stroke-width="3.5" />
        </svg>
        <div class="mt-3 flex items-center gap-2 rounded-xl bg-acao px-2.5 py-2 text-[10px] font-medium text-sobre-acao">
          <span class="grid size-4 shrink-0 place-items-center rounded-[5px] bg-brasa text-[8px] font-bold text-sobre-brasa">A</span>
          Tenho um plano claro, com metas e prazos.
        </div>
        <div class="mt-auto flex h-9 items-center justify-center rounded-full bg-acao text-[11px] font-semibold text-sobre-acao">
          Começar diagnóstico →
        </div>
      </div>
    </div>
  `,
  styles: `
    .aparelho {
      width: 100%;
      max-width: 248px;
      margin-inline: auto;
      padding: 9px;
      border-radius: 34px;
      background: #0b1513;
      box-shadow:
        0 30px 60px -30px rgb(15 43 38 / 0.55),
        inset 0 0 0 1.5px rgb(255 255 255 / 0.08);
    }
    .tela {
      display: flex;
      flex-direction: column;
      aspect-ratio: 9 / 17.5;
      padding: 18px 14px 14px;
      border-radius: 26px;
      overflow: hidden;
      transition:
        background-color 250ms,
        color 250ms;
    }
  `,
})
export class PreviaMarca {
  readonly tema = input.required<TemaMarca>();
  readonly nome = input('');
  readonly sigla = input('?');
  readonly logo = input<LogoEmpresa | null>(null);

  protected readonly pedeNome = computed(() => {
    const l = this.logo();
    return !!l && logoPedeNome(l);
  });

  private static sequencia = 0;
  protected readonly idGradiente = `previa-curva-${++PreviaMarca.sequencia}`;
  protected readonly curva = geometriaCurva(360, 200);
  protected readonly ponto = this.curva.ponto(0.62);
}
