import { Component, computed, DestroyRef, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';

import { problemaDe } from '../api/diagnostico-api';
import { Curva, PontoGrupo } from '../compartilhado/curva';
import { problemaEmail } from '../compartilhado/email';
import { FAIXAS } from '../compartilhado/faixas';
import { posicaoDaFoto, veuDaFoto } from '../compartilhado/foto-fundo';
import { Marca } from '../compartilhado/marca';
import { MarcaEmpresaCabecalho } from '../compartilhado/marca-empresa';
import { MarcaEmpresa } from '../compartilhado/tema-marca';
import { armazenamento, vibrar } from '../compartilhado/util';
import { GestorRh, SessaoRh } from './rh-api';
import { VitrineRh } from './vitrine-rh';

const ULTIMO_EMAIL = 'rh:email';
const ESPERA_REENVIO = 45;

/** Um grupo ilustrativo na curva da abertura (não são dados de ninguém). */
const GRUPO_ILUSTRATIVO: PontoGrupo[] = [22, 31, 38, 44, 47, 52, 55, 58, 58, 63, 71, 78, 84].map((indice) => ({
  indice,
  cor: indice < 40 ? FAIXAS['TRAVADA'].cor : indice < 70 ? FAIXAS['PLATO'].cor : FAIXAS['ASCENSAO'].cor,
}));

/**
 * Entrada do RH, sem senha: o e-mail e depois o código de 6 números que chega nele. O código
 * preenche sozinho no iPhone (Mail) e no Android (teclado), e entra assim que o sexto número chega.
 */
@Component({
  selector: 'app-entrar-rh',
  imports: [Marca, MarcaEmpresaCabecalho, Curva, VitrineRh],
  host: { class: 'block' },
  styles: `
    .casa {
      display: grid;
      place-items: center;
      height: 64px;
      border-radius: 16px;
      background: #fff;
      box-shadow: inset 0 0 0 1.5px var(--color-linha);
      font-family: var(--font-display);
      font-size: 30px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--color-abeto);
      transition: box-shadow 180ms, transform 160ms var(--ease-mola);
    }
    .casa-cheia {
      box-shadow: inset 0 0 0 1.5px var(--color-abeto-3);
    }
    .casa-atual {
      box-shadow: inset 0 0 0 2.5px var(--color-acao), 0 8px 20px -14px var(--color-acao);
    }
    .casa-atual::after {
      content: '';
      width: 2px;
      height: 28px;
      border-radius: 2px;
      background: var(--color-brasa);
      animation: piscar 1.1s steps(1) infinite;
    }
    .casa-cheia.casa-atual::after {
      display: none;
    }
    .erro .casa {
      box-shadow: inset 0 0 0 2px var(--color-travada);
      animation: tremer 360ms var(--ease-mola);
    }
    @keyframes piscar {
      50% { opacity: 0; }
    }
  `,
  template: `
    <!--
      Celular: uma coluna. Computador: como a entrada do administrador, a vitrine da empresa ocupa a
      metade esquerda de ponta a ponta (com a foto da empresa, quando há) e a entrada fica à direita.
    -->
    <div class="lg:grid lg:min-h-dvh lg:grid-cols-[minmax(0,1.08fr)_minmax(0,1fr)]">
    <!-- A entrada vem primeiro no código (é o que a pessoa veio fazer) e, no computador, fica parada no meio da tela. -->
    <div class="lg:sticky lg:top-0 lg:flex lg:h-dvh lg:items-center lg:justify-center lg:self-start lg:overflow-y-auto lg:px-10 lg:py-8">
    <div class="mx-auto flex min-h-dvh w-full max-w-md flex-col px-5 pt-5 sm:px-8 sm:pt-8 lg:min-h-0 lg:max-w-[27rem] lg:px-0 lg:pt-0">
      <!-- No celular, a foto da empresa numa faixa no alto, com a marca por cima. -->
      @if (etapa() === 'email' && marca()?.fundo; as f) {
        <div class="capa-celular relative -mx-5 -mt-5 h-[clamp(184px,30dvh,250px)] overflow-hidden rounded-b-[28px] text-white sm:-mx-8 sm:-mt-8 lg:hidden">
          <img class="foto absolute inset-0 size-full object-cover" [src]="f.url" alt="" [style.object-position]="posicao(f)" decoding="async" fetchpriority="high" />
          <i class="absolute inset-0" [style.background]="veuCelular" aria-hidden="true"></i>
          <div class="absolute inset-x-5 bottom-5 flex min-w-0 items-center gap-3 sm:inset-x-8">
            <span class="grid h-12 min-w-12 shrink-0 place-items-center rounded-2xl bg-white px-2.5 shadow-lg">
              @if (marca()!.logo; as logo) {
                <img [src]="logo.url" class="block h-8 w-auto max-w-[140px] object-contain" alt="" />
              } @else {
                <span class="display text-[17px] font-bold text-acao">{{ marca()!.sigla }}</span>
              }
            </span>
            <span class="display min-w-0 text-[20px] leading-tight font-bold [text-shadow:0_1px_12px_rgb(0_0_0/0.25)]">{{ marca()!.nome }}</span>
          </div>
        </div>
      }
      <header class="flex min-h-12 items-center" [class]="etapa() === 'email' ? (marca()?.fundo ? 'hidden' : 'lg:hidden') : 'lg:mb-6'">
        @if (etapa() === 'codigo') {
          <button type="button" class="-ml-3 inline-flex min-h-12 items-center gap-1.5 rounded-full px-3 text-[15px] font-semibold text-abeto-2 hover:text-abeto" (click)="voltar()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
            Usar outro e-mail
          </button>
        } @else if (marca(); as m) {
          <!-- Pelo link da empresa (/acompanhar/demo): a marca dela, como no link dos líderes. -->
          <app-marca-empresa class="min-w-0" [empresa]="m" />
        } @else {
          <app-marca />
        }
      </header>

      @if (etapa() === 'email') {
        <section class="flex-1 lg:flex-none lg:pt-0" [class]="marca()?.fundo ? 'pt-2' : 'pt-8'" aria-labelledby="titulo-rh">
          <!-- Sem foto, a curva ilustra a página no celular (com foto, a faixa no alto já faz isso). -->
          @if (!marca()?.fundo) {
            <figure class="surgir -mx-2 rounded-[28px] bg-white/70 px-2 pt-6 ring-1 ring-linha/70 lg:hidden" style="--i: 0" aria-hidden="true">
              <app-curva modo="grupo" [pontos]="ilustracao" />
            </figure>
          }
          <p class="rotulo surgir mt-8 text-brasa-escura lg:mt-0" style="--i: 1">Acompanhamento da avaliação</p>
          <h1 id="titulo-rh" class="display surgir mt-2 text-[38px] leading-[1.02] font-bold sm:text-[44px] lg:text-[clamp(34px,2.9vw,46px)]" style="--i: 2">
            @if (marca(); as m) {
              Acompanhe os líderes da {{ m.nome }}
            } @else {
              Acompanhe a avaliação dos seus líderes
            }
          </h1>
          <p class="surgir mt-4 text-[17px] leading-snug text-abeto-2" style="--i: 3">
            <span class="lg:hidden">Veja quem já respondeu, mande o link para quem falta e acompanhe o resultado do grupo.</span>
            <span class="hidden lg:inline">Entre com o seu e-mail de trabalho: mandamos um código de 6 números, sem senha e sem cadastro.</span>
          </p>

          <form class="surgir mt-9 lg:mt-10" style="--i: 4" novalidate (submit)="$event.preventDefault(); pedir()">
            <label for="email-rh" class="block text-[15px] font-semibold">Seu e-mail de trabalho</label>
            <div class="mt-1 flex items-end gap-3">
              <input
                #campoEmail
                id="email-rh"
                class="entrada-grande min-w-0 flex-1 text-[22px]!"
                type="email"
                inputmode="email"
                autocomplete="email"
                autocapitalize="off"
                spellcheck="false"
                enterkeyhint="send"
                placeholder="nome@empresa.com.br"
                [value]="email()"
                (input)="email.set(campoEmail.value); erro.set(null)"
                [attr.aria-invalid]="!!erro()"
                [attr.aria-describedby]="erro() ? 'erro-rh' : 'dica-rh'"
              />
              <!-- No celular, o botão redondo ao lado do campo; no computador, o botão com o texto, embaixo. -->
              <button
                type="submit"
                class="botao-enviar lg:hidden"
                [class.botao-enviar-pronto]="email().trim().length > 3"
                [attr.aria-busy]="enviando()"
                [disabled]="enviando()"
                aria-label="Receber o código"
              >
                @if (enviando()) {
                  <span class="size-5 animate-spin rounded-full border-2 border-current/30 border-t-current" aria-hidden="true"></span>
                } @else {
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                }
              </button>
            </div>
            @if (erro(); as e) {
              <p id="erro-rh" class="aparecer mt-3 text-[15px] font-medium text-travada" role="alert">{{ e }}</p>
            } @else {
              <p id="dica-rh" class="mt-3 text-[14px] text-abeto-2">Sem senha: mandamos um código de 6 números para este e-mail.</p>
            }
            <button type="submit" class="botao-primario mt-7 hidden w-full lg:flex" [attr.aria-busy]="enviando()" [disabled]="enviando()">
              @if (enviando()) {
                <span class="size-5 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
                <span>Mandando o código…</span>
              } @else {
                <span>Receber o código</span>
                <svg class="seta" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" stroke-linecap="round" stroke-linejoin="round" /></svg>
              }
            </button>
          </form>
        </section>
      } @else {
        <section class="flex-1 pt-6" aria-labelledby="titulo-codigo">
          <p class="rotulo surgir text-brasa-escura" style="--i: 0">Confira o seu e-mail</p>
          <h1 id="titulo-codigo" class="display surgir mt-2 text-[38px] leading-[1.02] font-bold" style="--i: 1">Digite o código</h1>
          <p class="surgir mt-4 text-[17px] leading-snug text-abeto-2" style="--i: 2">
            Se <strong class="font-semibold break-all text-abeto">{{ email().trim() }}</strong> acompanha
            {{ marca() ? 'a avaliação da ' + marca()!.nome : 'alguma avaliação' }}, o código de 6 números chega em instantes.
            Ele vale por 10 minutos.
          </p>

          <div class="surgir relative mt-8" style="--i: 3" [class.erro]="erroCodigo()" (click)="campoCodigo.focus()">
            <input
              #campoCodigo
              id="codigo-rh"
              class="absolute inset-0 z-10 h-full w-full cursor-text text-[16px] opacity-0"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              pattern="[0-9]*"
              maxlength="6"
              enterkeyhint="go"
              aria-label="Código de 6 números"
              [attr.aria-invalid]="!!erro()"
              [attr.aria-describedby]="erro() ? 'erro-codigo' : null"
              [value]="codigo()"
              (input)="digitou(campoCodigo)"
              (focus)="focado.set(true)"
              (blur)="focado.set(false)"
            />
            <div class="grid grid-cols-6 gap-2" aria-hidden="true">
              @for (i of casas; track i) {
                <span class="casa" [class.casa-cheia]="!!codigo()[i]" [class.casa-atual]="focado() && i === atual()">{{ codigo()[i] ?? '' }}</span>
              }
            </div>
          </div>

          @if (erro(); as e) {
            <p id="erro-codigo" class="aparecer mt-4 text-[15px] font-medium text-travada" role="alert">{{ e }}</p>
          }

          <button
            type="button"
            class="botao-primario surgir mt-8 w-full"
            style="--i: 4"
            [attr.aria-busy]="enviando()"
            [disabled]="enviando()"
            (click)="entrar()"
          >
            @if (enviando()) {
              <span class="size-5 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
              Entrando…
            } @else {
              Entrar
            }
          </button>

          <p class="surgir mt-6 text-center text-[15px] text-abeto-2" style="--i: 5">
            Não chegou? Veja no spam ou
            @if (espera() > 0) {
              <span class="whitespace-nowrap tabular-nums">peça outro em {{ espera() }}s.</span>
            } @else {
              <button type="button" class="inline-flex min-h-12 items-center font-semibold text-abeto underline decoration-linha underline-offset-4 hover:decoration-abeto" (click)="reenviar()">
                mande de novo
              </button>
            }
          </p>
          <p class="sr-only" aria-live="polite">{{ aviso() }}</p>
        </section>
      }

      <footer class="mt-10 mb-[max(20px,env(safe-area-inset-bottom))] lg:mb-0 flex items-start gap-3 rounded-2xl bg-white/60 px-4 py-3.5 text-[14px] leading-snug text-abeto-2 ring-1 ring-linha/60">
        <svg class="mt-0.5 shrink-0 text-abeto" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
        <span>Você vê só números do grupo. O resultado e as respostas de cada líder ficam com ele.</span>
      </footer>
    </div>
    </div>

      <!-- No computador, a vitrine à esquerda: a marca e a página que o gestor vai ver. No celular, nada. -->
      <app-vitrine-rh class="hidden min-h-dvh lg:order-first lg:flex" [marca]="marca()" [empresa]="empresa()" />
    </div>
  `,
})
export class EntrarRh {
  private readonly sessao = inject(SessaoRh);

  readonly entrou = output<GestorRh>();
  /** A marca da empresa quando a pessoa chega pelo link dela (/acompanhar/demo); null: tela neutra. */
  readonly marca = input<MarcaEmpresa | null>(null);
  /** O código dessa empresa: vai junto no pedido, para o botão do e-mail abrir nela. */
  readonly empresa = input<string | null>(null);

  protected readonly ilustracao = GRUPO_ILUSTRATIVO;
  protected readonly veuCelular = veuDaFoto('var(--color-abeto)', 'celular');
  protected readonly posicao = posicaoDaFoto;
  protected readonly casas = [0, 1, 2, 3, 4, 5];

  protected readonly etapa = signal<'email' | 'codigo'>('email');
  protected readonly email = signal(armazenamento.ler<string>(ULTIMO_EMAIL) ?? '');
  protected readonly codigo = signal('');
  protected readonly erro = signal<string | null>(null);
  protected readonly erroCodigo = signal(false);
  protected readonly enviando = signal(false);
  protected readonly focado = signal(false);
  protected readonly espera = signal(0);
  protected readonly aviso = signal('');
  protected readonly atual = computed(() => Math.min(this.codigo().length, 5));

  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');
  private relogio: ReturnType<typeof setInterval> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.relogio));
  }

  protected async pedir() {
    const email = this.email().trim();
    if (!email) {
      this.erro.set('Digite o seu e-mail.');
      return;
    }
    const problema = problemaEmail(email);
    if (problema) {
      this.erro.set(problema);
      return;
    }
    this.enviando.set(true);
    try {
      await this.sessao.pedirCodigo(email, this.empresa());
      armazenamento.gravar(ULTIMO_EMAIL, email);
      this.codigo.set('');
      this.erro.set(null);
      this.etapa.set('codigo');
      this.contarEspera();
      setTimeout(() => this.campoCodigo()?.nativeElement.focus(), 80);
    } catch (e) {
      this.erro.set(problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    } finally {
      this.enviando.set(false);
    }
  }

  protected async reenviar() {
    this.erro.set(null);
    this.enviando.set(true);
    try {
      await this.sessao.pedirCodigo(this.email().trim(), this.empresa());
      this.codigo.set('');
      this.aviso.set('Mandamos um código novo. Use o do e-mail mais recente.');
      this.contarEspera();
      this.campoCodigo()?.nativeElement.focus();
    } catch (e) {
      this.erro.set(problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    } finally {
      this.enviando.set(false);
    }
  }

  protected digitou(campo: HTMLInputElement) {
    const digitos = campo.value.replace(/\D/g, '').slice(0, 6);
    campo.value = digitos;
    this.codigo.set(digitos);
    this.erro.set(null);
    this.erroCodigo.set(false);
    if (digitos.length === 6) void this.entrar();
  }

  protected async entrar() {
    if (this.enviando()) return;
    if (this.codigo().length < 6) {
      this.erro.set('Digite os 6 números do código.');
      this.campoCodigo()?.nativeElement.focus();
      return;
    }
    this.enviando.set(true);
    try {
      const gestor = await this.sessao.entrar({ email: this.email().trim(), codigo: this.codigo() });
      vibrar();
      this.entrou.emit(gestor);
    } catch (e) {
      this.erro.set(problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
      this.erroCodigo.set(true);
      this.codigo.set('');
      const campo = this.campoCodigo()?.nativeElement;
      if (campo) {
        campo.value = '';
        campo.focus();
      }
    } finally {
      this.enviando.set(false);
    }
  }

  protected voltar() {
    this.etapa.set('email');
    this.erro.set(null);
    clearInterval(this.relogio);
    this.espera.set(0);
  }

  private contarEspera() {
    clearInterval(this.relogio);
    this.espera.set(ESPERA_REENVIO);
    this.relogio = setInterval(() => {
      this.espera.update((s) => Math.max(0, s - 1));
      if (this.espera() === 0) clearInterval(this.relogio);
    }, 1000);
  }
}
