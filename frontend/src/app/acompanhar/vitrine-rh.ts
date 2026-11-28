import { afterNextRender, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';

import { Curva, PontoGrupo } from '../compartilhado/curva';
import { FAIXAS } from '../compartilhado/faixas';
import { posicaoDaFoto, veuDaFoto } from '../compartilhado/foto-fundo';
import { semMovimento } from '../compartilhado/util';
import { fotoDaEntrada, MarcaEmpresa } from '../compartilhado/tema-marca';

type Aba = 'participacao' | 'resultado';
type Situacao = 'RESPONDEU' | 'COMECOU' | 'ABRIU' | 'NAO_ABRIU';

/** Um grupo ilustrativo na curva (não são dados de ninguém: a legenda avisa). */
const PONTOS: PontoGrupo[] = [24, 33, 39, 44, 48, 52, 55, 57, 58, 61, 64, 71, 77, 83].map((indice) => ({
  indice,
  cor: indice < 40 ? FAIXAS['TRAVADA'].cor : indice < 70 ? FAIXAS['PLATO'].cor : FAIXAS['ASCENSAO'].cor,
}));

const COR: Record<Situacao, string> = {
  RESPONDEU: 'var(--color-ascensao)',
  COMECOU: 'var(--color-plato)',
  ABRIU: '#D9A441',
  NAO_ABRIU: 'var(--color-abeto-3)',
};

const TOTAL = 25;
/** De quanto em quanto tempo as abas trocam sozinhas (até a pessoa escolher uma). */
const TROCA_A_CADA = 5200;
/** Quando "chega" uma resposta nova na prévia, para mostrar que a página é ao vivo. */
const CHEGADA_EM = 7600;

/**
 * A vitrine da entrada do RH no computador: a marca da empresa e uma janela com a página que o
 * gestor vai ver, com as duas abas de verdade (Participação e Resultado do grupo). As abas trocam
 * sozinhas, respondem ao clique, e a janela inclina um pouco seguindo o mouse; num momento, um
 * líder "responde" e o placar anda. Tudo ilustrativo (a legenda diz) e decorativo para leitores de
 * tela, que ouvem o título e a legenda. Sem animação para quem pede menos movimento.
 */
@Component({
  selector: 'app-vitrine-rh',
  imports: [Curva],
  host: {
    class: 'vitrine relative isolate flex-col overflow-hidden px-10 pt-9 pb-8 text-white xl:px-14 xl:pt-11',
    'aria-labelledby': 'vitrine-titulo',
    role: 'complementary',
  },
  styles: `
    :host {
      background:
        radial-gradient(110% 70% at 0% 0%, color-mix(in srgb, #fff 12%, transparent) 0%, transparent 55%),
        radial-gradient(80% 60% at 100% 100%, color-mix(in srgb, var(--color-brasa) 24%, transparent) 0%, transparent 62%),
        var(--color-abeto);
    }

    /*
     * A foto da empresa (quando há): atrás de tudo (z-index -1 dentro do isolate do host), cortada
     * no ponto de foco, com o véu na cor da marca por cima. Entra com um zoom lento, de boas-vindas.
     */
    .foto-fundo,
    .veu-fundo {
      position: absolute;
      inset: 0;
      z-index: -1;
      width: 100%;
      height: 100%;
    }
    .foto-fundo {
      object-fit: cover;
      animation: chegar-foto 1600ms var(--ease-mola) both;
    }
    @keyframes chegar-foto {
      from { opacity: 0; transform: scale(1.06); }
    }

    /* A janela: inclina até 4 graus seguindo o mouse e volta devagar. */
    .palco {
      perspective: 1400px;
    }
    .janela {
      border-radius: 22px;
      background: #fff;
      box-shadow:
        0 0 0 1px rgb(255 255 255 / 0.08),
        0 40px 80px -40px rgb(0 0 0 / 0.7),
        0 16px 32px -20px rgb(0 0 0 / 0.45);
      transform: rotateX(var(--rx, 0deg)) rotateY(var(--ry, 0deg));
      transform-style: preserve-3d;
      transition: transform 600ms var(--ease-mola);
    }
    .janela.seguindo {
      transition: transform 120ms linear;
    }
    /* O brilho que acompanha o mouse por cima da janela. */
    .janela::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: inherit;
      background: radial-gradient(420px circle at var(--mx, 50%) var(--my, 0%), rgb(255 255 255 / 0.35), transparent 45%);
      opacity: 0;
      pointer-events: none;
      transition: opacity 300ms;
      mix-blend-mode: soft-light;
    }
    .janela.seguindo::after {
      opacity: 1;
    }

    .aba {
      position: relative;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: 34px;
      padding: 0 14px;
      border-radius: 999px;
      color: var(--color-abeto-2);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 220ms, color 220ms;
    }
    .aba:hover {
      color: var(--color-abeto);
    }
    .aba-ativa {
      background: var(--color-acao);
      color: var(--color-sobre-acao);
    }
    .aba-ativa:hover {
      color: var(--color-sobre-acao);
    }
    /* O tempo até a próxima troca, como um fio que enche embaixo da aba ativa. */
    .aba-ativa.contando::after {
      content: '';
      position: absolute;
      right: 14px;
      bottom: 5px;
      left: 14px;
      height: 2px;
      border-radius: 2px;
      background: currentColor;
      opacity: 0.45;
      transform-origin: left;
      animation: contar var(--troca) linear both;
    }
    @keyframes contar {
      from { transform: scaleX(0); }
    }

    /* As duas telas ocupam o mesmo lugar: a da aba ativa aparece por cima. */
    .tela {
      position: absolute;
      inset: 0;
      opacity: 0;
      transform: translate3d(0, 10px, 0);
      pointer-events: none;
      transition: opacity 420ms var(--ease-mola), transform 420ms var(--ease-mola);
    }
    .tela-ativa {
      opacity: 1;
      transform: none;
      pointer-events: auto;
    }

    .parte {
      height: 100%;
      transition: width 900ms var(--ease-mola), opacity 200ms, transform 200ms;
      transform-origin: center;
    }
    .parte-apagada {
      opacity: 0.3;
    }
    .legenda {
      transition: opacity 200ms;
    }
    .legenda-apagada {
      opacity: 0.35;
    }

    .linha {
      transition: opacity 500ms, transform 500ms var(--ease-mola), max-height 500ms var(--ease-mola), background-color 400ms;
      max-height: 60px;
      overflow: hidden;
    }
    .linha-respondeu {
      background: var(--color-ascensao-suave, #e2f2ea);
    }
    .linha-saindo {
      max-height: 0;
      opacity: 0;
      transform: translate3d(24px, 0, 0);
    }
    /* Em tela baixa, a janela é menor: uma linha de quem falta basta. */
    @media (max-height: 50rem) {
      .linha-extra {
        display: none;
      }
    }
    .linha-entrando {
      animation: entrar-linha 520ms var(--ease-mola) both;
    }
    @keyframes entrar-linha {
      from { opacity: 0; transform: translate3d(0, 10px, 0); }
    }

    .vivo {
      position: relative;
      display: block;
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: var(--color-ascensao);
    }
    .vivo::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: inherit;
      background: inherit;
      animation: pulsar 2s ease-out infinite;
    }
    .mais-um {
      animation: mais-um 900ms var(--ease-mola) both;
    }
    @keyframes mais-um {
      0% { opacity: 0; transform: translate3d(0, 8px, 0) scale(0.8); }
      60% { opacity: 1; transform: translate3d(0, -2px, 0) scale(1.08); }
      100% { opacity: 1; transform: none; }
    }

    @media (prefers-reduced-motion: reduce) {
      .janela, .janela.seguindo, .tela, .parte, .linha { transition: none; }
      .vivo::after, .aba-ativa.contando::after, .mais-um, .linha-entrando, .foto-fundo { animation: none; }
    }
  `,
  template: `
    @if (fotoEntrada(); as f) {
      <img class="foto-fundo" [src]="f.url" alt="" [style.object-position]="posicao(f)" decoding="async" fetchpriority="high" />
      <i class="veu-fundo" [style.background]="veu" aria-hidden="true"></i>
    }

    <div class="flex min-w-0 items-center gap-3">
      @if (marca(); as m) {
        <span class="grid h-12 min-w-12 shrink-0 place-items-center rounded-2xl bg-white px-2.5">
          @if (m.logo; as logo) {
            <img [src]="logo.url" class="block h-8 w-auto max-w-[150px] object-contain" alt="" />
          } @else {
            <span class="display text-[17px] font-bold text-acao">{{ m.sigla }}</span>
          }
        </span>
        <span class="truncate text-[16px] font-semibold">{{ m.nome }}</span>
      } @else {
        <svg width="40" height="40" viewBox="0 0 26 26" aria-hidden="true">
          <rect width="26" height="26" rx="7" fill="rgb(255 255 255 / 0.12)" />
          <path d="M5 19 C 9 19, 9 8, 13 8 S 17 8, 21 12" fill="none" stroke="var(--color-brasa)" stroke-width="2.6" stroke-linecap="round" />
        </svg>
        <span class="text-[16px] font-semibold">Diagnóstico do Líder</span>
      }
    </div>

    <!-- O conteúdo fica centrado e com largura limitada: em monitor largo, nada se espalha. -->
    <div class="mx-auto my-auto w-full max-w-[640px] pt-8">
      <p class="rotulo text-white/75">{{ marca() ? 'A página da ' + marca()!.nome : 'A sua página de acompanhamento' }}</p>
      <p id="vitrine-titulo" class="display mt-3 max-w-[22ch] text-[clamp(28px,2.3vw,38px)] leading-[1.05] font-bold [text-shadow:0_2px_18px_rgb(0_0_0/0.3)]">
        Quem respondeu, quem falta e o resultado do grupo. Ao vivo.
      </p>

      <div class="palco mt-6" aria-hidden="true">
        <!-- text-abeto: dentro da janela (branca), a tinta da página; fora dela, o texto é branco. -->
        <div
          class="janela relative text-abeto"
          [class.seguindo]="seguindo()"
          [style.--rx]="inclinacao().x + 'deg'"
          [style.--ry]="inclinacao().y + 'deg'"
          [style.--mx]="brilho().x + '%'"
          [style.--my]="brilho().y + '%'"
          (pointermove)="mover($event)"
          (pointerleave)="soltar()"
          (pointerenter)="pausada.set(true)"
        >
          <!-- A barra da janela: o endereço da página da empresa. -->
          <div class="flex items-center gap-3 border-b border-linha/70 px-4 py-2.5">
            <span class="flex gap-1.5">
              <i class="block size-2.5 rounded-full bg-linha"></i><i class="block size-2.5 rounded-full bg-linha"></i><i class="block size-2.5 rounded-full bg-linha"></i>
            </span>
            <span class="min-w-0 flex-1 truncate rounded-full bg-nevoa px-3 py-1 text-center text-[11.5px] text-abeto-2">
              {{ endereco() }}
            </span>
            <span class="inline-flex items-center gap-1.5 text-[11.5px] font-semibold text-abeto-2"><i class="vivo"></i>Ao vivo</span>
          </div>

          <!-- As abas, como na página de verdade. -->
          <div class="flex items-center justify-between gap-3 px-4 pt-3">
            <div class="inline-flex rounded-full bg-nevoa p-1">
              @for (a of abas; track a.valor) {
                <span
                  class="aba"
                  [class.aba-ativa]="aba() === a.valor"
                  [class.contando]="aba() === a.valor && girando()"
                  [style.--troca]="trocaMs + 'ms'"
                  (click)="escolher(a.valor)"
                  (mouseenter)="escolher(a.valor)"
                >{{ a.texto }}</span>
              }
            </div>
            <span class="rounded-full bg-nevoa px-2.5 py-1 text-[11.5px] font-semibold text-abeto">Faltam 5 dias</span>
          </div>

          <div class="relative h-[272px] overflow-hidden rounded-b-[22px] [@media(max-height:50rem)]:h-[248px]">
            <!-- Participação -->
            <div class="tela px-5 pt-3.5" [class.tela-ativa]="aba() === 'participacao'">
              <div class="flex items-end justify-between gap-4">
                <p class="display text-[46px] leading-[0.82] font-bold tabular-nums">{{ placar() }}<span class="ml-0.5 text-[20px] font-semibold text-abeto-3">/{{ total }}</span></p>
                <p class="display pb-0.5 text-[24px] leading-none font-bold text-abeto-2 tabular-nums">{{ percentual() }}%</p>
              </div>
              <p class="mt-2 flex items-center gap-2 text-[13.5px] font-semibold text-abeto">
                líderes responderam
                @if (chegou()) {
                  <span class="mais-um rounded-full bg-ascensao-suave px-2 py-0.5 text-[11.5px] text-[#17714A]">+4 hoje</span>
                } @else {
                  <span class="rounded-full bg-ascensao-suave px-2 py-0.5 text-[11.5px] text-[#17714A]">+3 hoje</span>
                }
              </p>
              <div class="mt-3.5 flex h-2.5 gap-[3px] overflow-hidden rounded-full bg-nevoa">
                @for (p of partes(); track p.codigo) {
                  <span
                    class="parte"
                    [class.parte-apagada]="realce() && realce() !== p.codigo"
                    [style.width.%]="(p.quantidade / total) * 100"
                    [style.background]="p.cor"
                    (mouseenter)="realce.set(p.codigo)"
                    (mouseleave)="realce.set(null)"
                  ></span>
                }
              </div>
              <div class="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-[11.5px] text-abeto-2 xl:grid-cols-4 xl:gap-x-2">
                @for (p of partes(); track p.codigo) {
                  <span class="legenda flex items-center gap-1.5" [class.legenda-apagada]="realce() && realce() !== p.codigo" (mouseenter)="realce.set(p.codigo)" (mouseleave)="realce.set(null)">
                    <i class="block size-2 shrink-0 rounded-full" [style.background]="p.cor"></i>
                    <span class="truncate">{{ p.rotulo }}</span>
                    <strong class="ml-auto text-abeto tabular-nums">{{ p.quantidade }}</strong>
                  </span>
                }
              </div>

              <p class="mt-3.5 text-[12px] font-bold tracking-[0.08em] text-abeto-2 uppercase">Faltam {{ faltam() }} líderes</p>
              <ul class="mt-1.5 divide-y divide-linha/70">
                @for (l of visiveis(); track l.nome; let i = $index) {
                  <li class="linha flex items-center gap-3 rounded-xl px-1.5 py-1.5" [class.linha-extra]="i > 0" [class.linha-respondeu]="l.respondeu" [class.linha-saindo]="l.saindo" [class.linha-entrando]="l.entrando">
                    <i class="block size-2 shrink-0 rounded-full" [style.background]="l.respondeu ? cores.RESPONDEU : cores[l.situacao]"></i>
                    <span class="min-w-0 flex-1">
                      <span class="block truncate text-[13px] font-semibold text-abeto">{{ l.nome }}</span>
                      <span class="block truncate text-[11.5px] text-abeto-2">{{ l.respondeu ? 'Acabou de responder' : l.detalhe }}</span>
                    </span>
                    @if (l.respondeu) {
                      <svg class="shrink-0 text-ascensao" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                    } @else {
                      <span class="grid size-7 shrink-0 place-items-center rounded-full bg-[#e7f7ee] text-[#12804a]">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2.2a9.7 9.7 0 0 0-8.4 14.6L2.3 21.7l5-1.3A9.7 9.7 0 1 0 12 2.2z" /></svg>
                      </span>
                    }
                  </li>
                }
              </ul>
            </div>

            <!-- Resultado do grupo -->
            <div class="tela px-5 pt-4" [class.tela-ativa]="aba() === 'resultado'">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-[11px] font-bold tracking-[0.1em] text-abeto-2 uppercase">Índice médio do grupo</p>
                  <p class="display mt-1.5 text-[44px] leading-[0.85] font-bold tabular-nums">58<span class="ml-0.5 text-[17px] font-medium text-abeto-3">/100</span></p>
                </div>
                <div class="flex flex-col items-end gap-1.5 pt-1">
                  <span class="inline-flex items-center gap-1.5 rounded-full bg-plato-suave px-2.5 py-1 text-[12px] font-bold text-[#855300]">
                    <i class="block size-2 rounded-full bg-plato"></i>Carreira no platô
                  </span>
                  <span class="text-[11.5px] text-abeto-2"><strong class="text-abeto">2 pontos acima</strong> da média geral</span>
                </div>
              </div>
              <app-curva class="-mx-2 mt-2 block" modo="grupo" [pontos]="pontos" [largura]="720" />
              <p class="mt-1 text-center text-[11px] text-abeto-2">Cada ponto é um líder, sem identificação. Abre com 3 respostas.</p>
            </div>
          </div>
        </div>
      </div>

      <p class="mt-4 text-[13px] text-white/75">
        Prévia ilustrativa: passe o mouse ou clique nas abas. {{ marca() ? 'Os números da ' + marca()!.nome + ' aparecem' : 'Os números da sua empresa aparecem' }} depois de entrar.
      </p>
    </div>

    <ul class="mx-auto mt-8 grid w-full max-w-[640px] grid-cols-3 gap-6 border-t border-white/20 pt-6 text-[13.5px] leading-snug text-white/80[@media(max-height:54rem)]:hidden">
      <li>
        <strong class="flex items-center gap-2 text-[14.5px] font-semibold text-white">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v4.5h-4.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <span>Ao vivo</span>
        </strong>
        <span class="mt-1 block">Atualiza sozinho ao longo do dia.</span>
      </li>
      <li>
        <strong class="flex items-center gap-2 text-[14.5px] font-semibold text-white">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
          <span>Anônimo</span>
        </strong>
        <span class="mt-1 block">O resultado do grupo abre com 3 respostas.</span>
      </li>
      <li>
        <strong class="flex items-center gap-2 text-[14.5px] font-semibold text-white">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M7 3.5h7l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19V5a1.5 1.5 0 0 1 1-1.5z" stroke-linejoin="round" /><path d="M14 3.5V8h4" stroke-linejoin="round" /></svg>
          <span>Para a reunião</span>
        </strong>
        <span class="mt-1 block">PDF e Excel em um clique.</span>
      </li>
    </ul>
  `,
})
export class VitrineRh {
  /** A marca da empresa (null: a identidade do Diagnóstico do Líder). */
  readonly marca = input<MarcaEmpresa | null>(null);
  /** O código do link da empresa, para o endereço na barra da janela. */
  readonly empresa = input<string | null>(null);

  /** O véu da foto na cor da marca (a tinta do tema, já aplicada na página). */
  protected readonly veu = veuDaFoto('var(--color-abeto)', 'entrada');
  protected readonly posicao = posicaoDaFoto;
  /** A foto da entrada (ou, sem ela, a do topo). */
  protected readonly fotoEntrada = computed(() => fotoDaEntrada(this.marca()));
  protected readonly total = TOTAL;
  protected readonly trocaMs = TROCA_A_CADA;
  protected readonly pontos = PONTOS;
  protected readonly cores = COR;
  protected readonly abas: { valor: Aba; texto: string }[] = [
    { valor: 'participacao', texto: 'Participação' },
    { valor: 'resultado', texto: 'Resultado do grupo' },
  ];

  protected readonly aba = signal<Aba>('participacao');
  /** O mouse está na janela: as abas param de trocar sozinhas. */
  protected readonly pausada = signal(false);
  /** A pessoa escolheu uma aba: daí em diante, ela manda. */
  private readonly escolheu = signal(false);
  protected readonly girando = computed(() => !this.pausada() && !this.escolheu() && !semMovimento());

  protected readonly placar = signal(semMovimento() ? 18 : 0);
  protected readonly chegou = signal(false);
  protected readonly realce = signal<Situacao | null>(null);
  protected readonly seguindo = signal(false);
  protected readonly inclinacao = signal({ x: 0, y: 0 });
  protected readonly brilho = signal({ x: 50, y: 0 });
  protected readonly pendentes = signal([
    { nome: 'Ana Souza', detalhe: 'Gerente · 12 de 30 perguntas', situacao: 'COMECOU' as Situacao, respondeu: false, saindo: false, saiu: false, entrando: false },
    { nome: 'Bruno Lima', detalhe: 'Coordenador · abriu ontem, às 16h', situacao: 'ABRIU' as Situacao, respondeu: false, saindo: false, saiu: false, entrando: false },
    { nome: 'Carla Dias', detalhe: 'Supervisora · convite enviado segunda', situacao: 'NAO_ABRIU' as Situacao, respondeu: false, saindo: false, saiu: false, entrando: true },
  ]);
  /** Duas linhas de quem falta cabem na janela: quando a Ana sai, a Carla entra. */
  protected readonly visiveis = computed(() => this.pendentes().filter((l) => !l.saiu).slice(0, 2));

  protected readonly endereco = computed(() => {
    const codigo = this.empresa();
    return codigo ? `acompanhar/${codigo}` : 'acompanhar';
  });
  protected readonly percentual = computed(() => Math.round((this.placar() / TOTAL) * 100));
  /** Quem falta não conta junto com o placar: fica parado enquanto o número sobe. */
  protected readonly faltam = computed(() => TOTAL - (this.chegou() ? 19 : 18));
  protected readonly partes = computed(() => {
    return [
      { codigo: 'RESPONDEU' as Situacao, rotulo: 'Responderam', quantidade: this.placar(), cor: COR.RESPONDEU },
      { codigo: 'COMECOU' as Situacao, rotulo: 'Começaram', quantidade: this.chegou() ? 2 : 3, cor: COR.COMECOU },
      { codigo: 'ABRIU' as Situacao, rotulo: 'Só abriram', quantidade: 1, cor: COR.ABRIU },
      { codigo: 'NAO_ABRIU' as Situacao, rotulo: 'Não abriram', quantidade: 3, cor: COR.NAO_ABRIU },
    ];
  });

  constructor() {
    const destroy = inject(DestroyRef);
    const timers: ReturnType<typeof setTimeout>[] = [];
    let quadro = 0;

    afterNextRender(() => {
      if (semMovimento()) return;
      // O placar conta até 18 quando a tela abre.
      const inicio = performance.now();
      const contar = (agora: number) => {
        const t = Math.min(1, (agora - inicio - 450) / 900);
        this.placar.set(Math.round(18 * (t <= 0 ? 0 : 1 - (1 - t) ** 3)));
        if (t < 1) quadro = requestAnimationFrame(contar);
      };
      quadro = requestAnimationFrame(contar);

      // As abas trocam sozinhas, até a pessoa escolher uma (ou enquanto o mouse estiver fora).
      const girar = () => {
        if (this.girando()) this.aba.update((a) => (a === 'participacao' ? 'resultado' : 'participacao'));
        timers.push(setTimeout(girar, TROCA_A_CADA));
      };
      timers.push(setTimeout(girar, TROCA_A_CADA));

      // Uma resposta chega: a Ana termina, o placar anda e ela sai da lista de quem falta.
      timers.push(
        setTimeout(() => {
          this.aba.set('participacao');
          this.pendentes.update((ls) => ls.map((l, i) => (i === 0 ? { ...l, respondeu: true } : l)));
          this.placar.set(19);
          this.chegou.set(true);
          timers.push(setTimeout(() => this.pendentes.update((ls) => ls.map((l, i) => (i === 0 ? { ...l, saindo: true } : l))), 1800));
          timers.push(setTimeout(() => this.pendentes.update((ls) => ls.map((l, i) => (i === 0 ? { ...l, saiu: true } : l))), 2350));
        }, CHEGADA_EM),
      );
    });

    destroy.onDestroy(() => {
      timers.forEach(clearTimeout);
      cancelAnimationFrame(quadro);
    });
  }

  protected escolher(aba: Aba) {
    this.escolheu.set(true);
    this.aba.set(aba);
  }

  /** A janela inclina na direção do mouse (até 4 graus) e o brilho vai junto. */
  protected mover(evento: PointerEvent) {
    if (semMovimento() || evento.pointerType !== 'mouse') return;
    const caixa = (evento.currentTarget as HTMLElement).getBoundingClientRect();
    const x = (evento.clientX - caixa.left) / caixa.width;
    const y = (evento.clientY - caixa.top) / caixa.height;
    this.seguindo.set(true);
    this.inclinacao.set({ x: +((0.5 - y) * 8).toFixed(2), y: +((x - 0.5) * 8).toFixed(2) });
    this.brilho.set({ x: Math.round(x * 100), y: Math.round(y * 100) });
  }

  protected soltar() {
    this.seguindo.set(false);
    this.pausada.set(false);
    this.inclinacao.set({ x: 0, y: 0 });
  }
}
