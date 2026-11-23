import { Component, computed, input } from '@angular/core';

import { LogoEmpresa, TemaMarca } from '../../compartilhado/tema-marca';
import { caixaDaLogo, CORES_PADRAO, coresDoQr, geometriaQr, logoCabeNoQr } from './qr-codigo';

/** O QR Code desenhado direto na página (miniaturas e prévia enquanto digita). */
@Component({
  selector: 'app-qr-svg',
  host: { class: 'block' },
  template: `
    <svg
      class="block size-full"
      [attr.viewBox]="'0 0 ' + geo().lado + ' ' + geo().lado"
      [attr.role]="rotulo() ? 'img' : null"
      [attr.aria-label]="rotulo() || null"
      [attr.aria-hidden]="rotulo() ? null : 'true'"
      focusable="false"
    >
      <rect [attr.width]="geo().lado" [attr.height]="geo().lado" fill="#FFFFFF" />
      <path [attr.d]="geo().modulos" [attr.fill]="cores().modulo" shape-rendering="crispEdges" />
      <path [attr.d]="geo().marcadores" [attr.fill]="cores().modulo" fill-rule="evenodd" />
      @if (posicaoLogo(); as p) {
        <rect [attr.x]="p.area.x" [attr.y]="p.area.y" [attr.width]="p.area.lado" [attr.height]="p.area.lado" [attr.rx]="p.area.lado * 0.22" fill="#FFFFFF" />
        <image [attr.href]="logoEmpresa()!.url" [attr.x]="p.caixa.x" [attr.y]="p.caixa.y" [attr.width]="p.caixa.largura" [attr.height]="p.caixa.altura" preserveAspectRatio="xMidYMid meet" />
      } @else if (geo().logo; as logo) {
        <g [attr.transform]="'translate(' + logo.x + ' ' + logo.y + ') scale(' + logo.escala + ')'">
          <rect width="26" height="26" rx="7" [attr.fill]="cores().simbolo" />
          <path d="M5 19 C 9 19, 9 8, 13 8 S 17 8, 21 12" fill="none" [attr.stroke]="cores().curva" stroke-width="2.6" stroke-linecap="round" />
        </g>
      }
    </svg>
  `,
})
export class QrSvg {
  readonly texto = input.required<string>();
  readonly margem = input(2);
  /** Sem rótulo, o desenho é decorativo (o texto ao lado já diz o link). */
  readonly rotulo = input('');
  /** Cores da empresa (sem tema: as do Diagnóstico). */
  readonly tema = input<TemaMarca | null>(null);

  protected readonly cores = computed(() => {
    const tema = this.tema();
    return tema ? coresDoQr(tema) : CORES_PADRAO;
  });
  protected readonly geo = computed(() => geometriaQr(this.texto(), { margem: this.margem() }));

  /** Logomarca quase quadrada vai no centro; a larga fica de fora (sairia minúscula). */
  readonly logoEmpresa = input<LogoEmpresa | null>(null);
  protected readonly posicaoLogo = computed(() => {
    const area = this.geo().logo;
    const logo = this.logoEmpresa();
    return area && logoCabeNoQr(logo) ? { area, caixa: caixaDaLogo(area, logo) } : null;
  });
}
