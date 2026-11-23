import { afterNextRender, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';

import { geometriaCurva, posicaoNaCurva } from './curva-carreira';

/**
 * hero: a curva se desenha e um ponto pulsa no platô, perguntando "e você?".
 * resultado: o ponto percorre a curva junto com o traço e para onde o líder está.
 * calculando: o ponto percorre a curva em loop enquanto o resultado é calculado.
 * grupo: vários líderes de uma vez (painel); índices iguais viram contas empilhadas.
 */
export type ModoCurva = 'hero' | 'resultado' | 'calculando' | 'grupo';

export interface PontoGrupo {
  indice: number;
  cor: string;
}

let sequencia = 0;

const suavizar = (p: number) => (p < 0.5 ? 4 * p ** 3 : 1 - (-2 * p + 2) ** 3 / 2);

@Component({
  selector: 'app-curva',
  host: { class: 'block' },
  template: `
    <div class="relative w-full" [style.aspect-ratio]="g().largura + ' / ' + g().altura">
      <svg
        class="absolute inset-0 size-full overflow-visible"
        [attr.viewBox]="'0 0 ' + g().largura + ' ' + g().altura"
        aria-hidden="true"
        focusable="false"
      >
        <defs>
          <linearGradient [attr.id]="id + '-area'" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" style="stop-color: var(--color-brasa)" stop-opacity="0.2" />
            <stop offset="1" style="stop-color: var(--color-brasa)" stop-opacity="0" />
          </linearGradient>
          <clipPath [attr.id]="id + '-revelar'">
            <rect x="0" y="-40" [attr.width]="larguraRevelada()" [attr.height]="g().altura + 80" />
          </clipPath>
        </defs>

        <line
          [attr.x1]="g().margemX"
          [attr.x2]="g().largura - g().margemX"
          [attr.y1]="g().base"
          [attr.y2]="g().base"
          style="stroke: var(--color-linha)"
          stroke-width="1.5"
        />
        <g [attr.clip-path]="'url(#' + id + '-revelar)'">
          <path [attr.d]="g().area" [attr.fill]="'url(#' + id + '-area)'" />
          <path
            [attr.d]="g().curva"
            fill="none"
            style="stroke: var(--color-brasa)"
            [attr.stroke-width]="modo() === 'hero' ? 5 : 4.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </g>

        @if (modo() === 'resultado' && concluido()) {
          <path
            class="aparecer"
            [attr.d]="g().proximaCurva"
            fill="none"
            style="stroke: var(--color-abeto)"
            stroke-opacity="0.45"
            stroke-width="2.2"
            stroke-dasharray="2 7"
            stroke-linecap="round"
          />
        }

        @if (modo() === 'grupo' && concluido()) {
          @for (c of contas(); track $index) {
            <circle
              class="brotar"
              [style.animation-delay.ms]="$index * 35"
              [attr.cx]="c.x"
              [attr.cy]="c.y"
              r="6"
              [attr.fill]="c.cor"
              stroke="#fff"
              stroke-width="2.5"
            >
              <title>Índice {{ c.indice }}</title>
            </circle>
          }
        }

        @if (ponto(); as p) {
          @if (modo() === 'resultado') {
            <line
              [attr.x1]="p.x"
              [attr.x2]="p.x"
              [attr.y1]="p.y"
              [attr.y2]="g().base"
              style="stroke: var(--color-abeto)"
              stroke-opacity="0.3"
              stroke-dasharray="3 4"
            />
          }
          @if (concluido() || modo() === 'hero') {
            <circle class="pulso" [attr.cx]="p.x" [attr.cy]="p.y" r="7" [style.fill]="cor()" />
          }
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="8.5" [style.fill]="cor()" stroke="#fff" stroke-width="3.5" />
        }
      </svg>

      @for (r of g().rotulos; track r.texto) {
        <span
          class="rotulo absolute -translate-x-1/2 text-[11px] text-abeto-2"
          [style.left.%]="(r.x / g().largura) * 100"
          [style.top.%]="((g().base + 10) / g().altura) * 100"
          >{{ r.texto }}</span
        >
      }

      @if (modo() === 'resultado' && concluido() && ponto(); as p) {
        <span
          class="balao absolute rounded-full bg-abeto px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap text-white shadow-lg"
          [style.left.%]="(p.x / g().largura) * 100"
          [style.top.%]="(p.y / g().altura) * 100"
          >{{ rotulo() }}</span
        >
      }
    </div>
  `,
  styles: `
    .balao {
      transform: translate(-50%, calc(-100% - 18px));
      animation: balao 420ms var(--ease-mola) both;
    }
    .balao::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: -5px;
      width: 10px;
      height: 10px;
      background: inherit;
      transform: translateX(-50%) rotate(45deg);
      border-radius: 2px;
    }
  `,
})
export class Curva {
  readonly modo = input<ModoCurva>('hero');
  /** Índice de movimento (0–100). Só no modo resultado. */
  readonly indice = input<number | null>(null);
  /** Cor do ponto: a tinta do tema por padrão, a cor da faixa no resultado. */
  readonly cor = input('var(--color-abeto)');
  /** Texto do balão no modo resultado. */
  readonly rotulo = input('Você está aqui');
  /** Líderes a posicionar no modo grupo. */
  readonly pontos = input<PontoGrupo[]>([]);

  protected readonly id = `curva-${++sequencia}`;
  protected readonly g = computed(() => geometriaCurva(360, this.modo() === 'hero' ? 190 : 210));

  /** Pontos do modo grupo: quem tem o mesmo índice fica empilhado acima da curva. */
  protected readonly contas = computed(() => {
    const g = this.g();
    const repeticoes = new Map<number, number>();
    return this.pontos().map((p) => {
      const ordem = repeticoes.get(p.indice) ?? 0;
      repeticoes.set(p.indice, ordem + 1);
      const posicao = g.ponto(posicaoNaCurva(p.indice));
      return { x: posicao.x, y: posicao.y - ordem * 13, cor: p.cor, indice: p.indice };
    });
  });

  /** Até onde (0–1) o traço já foi desenhado e onde está o ponto. */
  private readonly revelado = signal(0);
  private readonly tPonto = signal<number | null>(null);
  protected readonly concluido = signal(false);

  protected readonly larguraRevelada = computed(() => this.g().x(this.revelado()) + 6);
  protected readonly ponto = computed(() => {
    const t = this.tPonto();
    return t === null ? null : this.g().ponto(t);
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const semMovimento = matchMedia('(prefers-reduced-motion: reduce)').matches;
      let quadro = 0;
      const inicio = performance.now();
      const destino = posicaoNaCurva(this.indice() ?? 50);

      const passo = (agora: number) => {
        const decorrido = agora - inicio - 200;
        const modo = this.modo();

        if (modo === 'hero' || modo === 'grupo') {
          const p = semMovimento ? 1 : Math.max(0, Math.min(1, decorrido / (modo === 'grupo' ? 900 : 1600)));
          this.revelado.set(suavizar(p));
          if (p >= 1) {
            if (modo === 'hero') this.tPonto.set(0.62);
            this.concluido.set(true);
            return;
          }
        } else if (modo === 'resultado') {
          // 1) o ponto percorre a curva até o líder; 2) o resto do traço aparece.
          const ida = semMovimento ? 1 : Math.max(0, Math.min(1, decorrido / 1500));
          const t = suavizar(ida) * destino;
          this.tPonto.set(t);
          const resto = semMovimento ? 1 : Math.max(0, Math.min(1, (decorrido - 1500) / 500));
          this.revelado.set(t + (1 - destino) * suavizar(resto));
          if (resto >= 1) {
            this.concluido.set(true);
            return;
          }
        } else {
          const ciclo = ((decorrido % 1900) + 1900) % 1900;
          const t = suavizar(ciclo / 1900);
          this.revelado.set(t);
          this.tPonto.set(t);
        }
        quadro = requestAnimationFrame(passo);
      };

      quadro = requestAnimationFrame(passo);
      destroyRef.onDestroy(() => cancelAnimationFrame(quadro));
    });
  }
}
