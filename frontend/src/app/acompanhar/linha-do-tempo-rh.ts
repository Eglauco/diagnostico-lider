import { HttpClient, httpResource } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { EdicaoLider, NovoConvite } from '../admin/convites/novo-convite';
import { problemaDe } from '../api/diagnostico-api';
import { quando } from '../compartilhado/formatos';
import { API_RH, GestaoRh, LiderEditavel, LiderRh, PassoRh, primeiroNome } from './rh-api';

const HORA = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });
const DIA = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });

const minuscula = (texto: string) => texto.charAt(0).toLowerCase() + texto.slice(1);

/** A situação do líder em uma frase e a cor dela (as mesmas dos grupos da lista). */
const SITUACAO: Record<LiderRh['situacao'], { rotulo: string; cor: string; fundo: string; texto: string }> = {
  RESPONDEU: { rotulo: 'Respondeu', cor: 'var(--color-ascensao)', fundo: '#E2F2EA', texto: '#17714A' },
  COMECOU: { rotulo: 'Começou e não terminou', cor: 'var(--color-plato)', fundo: '#FBF0DC', texto: '#855300' },
  ABRIU: { rotulo: 'Abriu, mas não começou', cor: '#D9A441', fundo: '#FBF3DF', texto: '#7A5500' },
  NAO_ABRIU: { rotulo: 'Ainda não abriu o link', cor: 'var(--color-abeto-3)', fundo: 'var(--color-nevoa)', texto: 'var(--color-abeto-2)' },
};

/** A entrega de um e-mail, dita para gente (sem o detalhe técnico do provedor). */
function entrega(p: PassoRh): { texto: string; tom: 'ok' | 'espera' | 'atencao' | 'erro' } | null {
  const e = p.entrega;
  if (!e) return null;
  switch (e.situacao) {
    case 'ENTREGUE':
      return { texto: e.entregueEm ? `Entregue às ${HORA.format(new Date(e.entregueEm))}` : 'Entregue', tom: 'ok' };
    case 'ATRASADO':
      return { texto: 'Entrega atrasada: o provedor ainda está tentando', tom: 'atencao' };
    case 'DEVOLVIDO':
      return { texto: 'Voltou: o endereço pode estar errado', tom: 'erro' };
    case 'SPAM':
      return { texto: 'Marcado como spam', tom: 'erro' };
    case 'BLOQUEADO':
      return { texto: 'Bloqueado pelo provedor', tom: 'erro' };
    case 'FALHOU':
      return { texto: 'Não saiu', tom: 'erro' };
    default:
      return { texto: 'Saiu, aguardando a confirmação de entrega', tom: 'espera' };
  }
}

/**
 * A linha do tempo de um líder na página do RH: um painel que entra pela direita no computador (a
 * lista continua à vista, e dá para ir ao próximo líder com as setas) e sobe do rodapé no celular.
 * Mostra só a participação: nada das respostas nem do resultado. Esc, o X, o fundo ou o "voltar"
 * do celular fecham.
 */
@Component({
  selector: 'app-linha-do-tempo-rh',
  imports: [NovoConvite],
  host: { '(document:keydown)': 'tecla($event)' },
  styleUrl: './acompanhar.css',
  styles: `
    .folha {
      width: 100%;
      max-width: 100%;
      max-height: 92dvh;
      margin: auto 0 0;
      padding: 0;
      border: 0;
      border-radius: 28px 28px 0 0;
      background: var(--color-nevoa);
      color: var(--color-abeto);
      box-shadow: 0 -24px 60px -30px rgb(15 43 38 / 0.5);
      overscroll-behavior: contain;
    }
    .folha[open] {
      animation: subir 360ms var(--ease-mola);
    }
    .folha::backdrop {
      background: rgb(15 43 38 / 0.42);
      backdrop-filter: blur(2px);
      animation: aparecer 260ms ease-out;
    }
    @keyframes subir {
      from { transform: translate3d(0, 48px, 0); opacity: 0; }
    }
    @keyframes entrar-lado {
      from { transform: translate3d(48px, 0, 0); opacity: 0; }
    }
    @media (width >= 64rem) {
      .folha {
        width: min(480px, 100vw);
        height: 100dvh;
        max-height: 100dvh;
        margin: 0 0 0 auto;
        border-radius: 28px 0 0 28px;
        box-shadow: -30px 0 80px -40px rgb(15 43 38 / 0.55);
      }
      .folha[open] {
        animation: entrar-lado 380ms var(--ease-mola);
      }
    }

    /* O trilho da linha do tempo: uma linha vertical ligando os pontos. */
    .passo {
      position: relative;
      padding-left: 48px;
    }
    .passo::before {
      content: '';
      position: absolute;
      top: 38px;
      bottom: -6px;
      left: 17px;
      width: 2px;
      border-radius: 2px;
      background: var(--color-linha);
    }
    .passo:last-child::before {
      display: none;
    }
    .ponto {
      position: absolute;
      top: 2px;
      left: 0;
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 999px;
      box-shadow: 0 0 0 4px var(--color-nevoa);
    }
    .tom-neutro { background: #fff; color: var(--color-abeto-2); box-shadow: 0 0 0 4px var(--color-nevoa), inset 0 0 0 1.5px var(--color-linha); }
    .tom-envio { background: var(--color-acao); color: var(--color-sobre-acao); }
    .tom-alerta { background: #FDEDEF; color: var(--color-travada); }
    .tom-concluido { background: var(--color-ascensao); color: #fff; }

    .entrega { display: inline-flex; align-items: center; gap: 6px; min-height: 26px; padding: 0 10px; border-radius: 999px; font-size: 12.5px; font-weight: 600; }
    .entrega-ok { background: #E2F2EA; color: #17714A; }
    .entrega-espera { background: var(--color-nevoa-2); color: var(--color-abeto-2); box-shadow: inset 0 0 0 1px var(--color-linha); }
    .entrega-atencao { background: #FBF0DC; color: #855300; }
    .entrega-erro { background: #FDEDEF; color: var(--color-travada); }

    @media (prefers-reduced-motion: reduce) {
      .folha[open], .folha::backdrop { animation: none; }
    }
  `,
  template: `
    <dialog #folha class="folha" [attr.aria-labelledby]="'linha-titulo-' + lider().id" (close)="fechar.emit()" (click)="cliqueFora($event)">
      <div class="flex max-h-[inherit] flex-col lg:h-full">
        <!-- Cabeçalho: navegar entre os líderes e fechar. -->
        <div class="flex items-center justify-between gap-2 px-4 pt-3 lg:px-6 lg:pt-5">
          <span class="mx-auto block h-1.5 w-10 rounded-full bg-linha lg:hidden" aria-hidden="true"></span>
          <div class="hidden items-center gap-1 lg:flex">
            <button type="button" class="grid size-10 place-items-center rounded-full text-abeto-2 hover:bg-white hover:text-abeto disabled:opacity-30" [disabled]="!temAnterior()" (click)="anterior.emit()" aria-label="Líder anterior" title="Líder anterior (←)">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </button>
            <button type="button" class="grid size-10 place-items-center rounded-full text-abeto-2 hover:bg-white hover:text-abeto disabled:opacity-30" [disabled]="!temProximo()" (click)="proximo.emit()" aria-label="Próximo líder" title="Próximo líder (→)">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </button>
            <span class="ml-1 text-[13px] text-abeto-2 tabular-nums">{{ posicao() }}</span>
          </div>
          <button type="button" class="absolute top-2 right-2 grid size-12 place-items-center rounded-full text-abeto-2 hover:bg-white hover:text-abeto lg:static" (click)="folha.close()" aria-label="Fechar">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 pb-[max(24px,env(safe-area-inset-bottom))] lg:px-7">
          <!-- Quem é e em que pé está. -->
          <header class="pt-3 lg:pt-4">
            <div class="flex items-center gap-4">
              <span class="display grid size-14 shrink-0 place-items-center rounded-2xl text-[20px] font-bold" [style.background]="situacao().fundo" [style.color]="situacao().texto" aria-hidden="true">{{ iniciais() }}</span>
              <div class="min-w-0">
                <h2 [id]="'linha-titulo-' + lider().id" class="display text-[26px] leading-[1.05] font-bold break-words">{{ lider().nome }}</h2>
                <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[14px] text-abeto-2">
                  <span>{{ lider().cargo ?? 'Sem cargo' }}</span>
                  @if (lider().origem === 'LINK') {
                    <span class="etiqueta-link">pelo link</span>
                  } @else {
                    <span aria-hidden="true">·</span><span>{{ lider().origem === 'MANUAL' ? 'cadastro manual' : 'da planilha' }}</span>
                  }
                </p>
              </div>
            </div>

            <div class="mt-5 rounded-[20px] bg-white p-4 ring-1 ring-linha/70">
              <p class="inline-flex items-center gap-2 rounded-full px-3 py-1 text-[13.5px] font-bold" [style.background]="situacao().fundo" [style.color]="situacao().texto">
                <i class="block size-2 rounded-full" [style.background]="situacao().cor" aria-hidden="true"></i>
                {{ situacao().rotulo }}
              </p>
              @if (lider().situacao === 'COMECOU' && lider().perguntas) {
                <div class="mt-3">
                  <div class="flex items-baseline justify-between text-[13.5px]">
                    <span class="text-abeto-2">Respondeu</span>
                    <strong class="tabular-nums">{{ lider().respondidas ?? 0 }} de {{ lider().perguntas }} perguntas</strong>
                  </div>
                  <div class="mt-1.5 h-2 overflow-hidden rounded-full bg-nevoa" aria-hidden="true">
                    <span class="block h-full rounded-full bg-plato transition-[width] duration-700" [style.width.%]="progresso()"></span>
                  </div>
                </div>
              }
              <p class="mt-3 text-[15px] leading-snug">{{ proximoPasso() }}</p>
              @if (lider().alerta; as a) {
                <p class="mt-2 flex items-center gap-1.5 text-[14px] font-semibold text-travada">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M12 8v5M12 16.5v.01M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  {{ a }}: o e-mail não está chegando.
                </p>
              }

              @if (lider().link) {
                <div class="mt-4 grid gap-2" [class.grid-cols-2]="!!whatsapp()">
                  @if (whatsapp(); as w) {
                    <a class="col-span-2 inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-[#1f9d5a] px-5 text-[15px] font-semibold text-white transition hover:bg-[#178a4d]" [href]="w" target="_blank" rel="noopener" (click)="porWhatsapp.emit()">
                      <svg width="19" height="19" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.2a9.7 9.7 0 0 0-8.4 14.6L2.3 21.7l5-1.3A9.7 9.7 0 1 0 12 2.2zm0 17.7a8 8 0 0 1-4.1-1.1l-.3-.2-3 .8.8-2.9-.2-.3A8 8 0 1 1 12 19.9z" /></svg>
                      Mandar o link pelo WhatsApp
                    </a>
                  }
                  <button type="button" class="botao-secundario min-h-12 bg-white px-4 text-[14.5px]" aria-haspopup="dialog" (click)="porEmail.emit()">
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M4 7l8 6 8-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                    Por e-mail
                  </button>
                  <button type="button" class="botao-secundario min-h-12 bg-white px-4 text-[14.5px]" (click)="copiar.emit()">
                    @if (copiado()) {
                      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                      <span>Link copiado</span>
                    } @else {
                      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1" stroke-linecap="round" /><path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1" stroke-linecap="round" /></svg>
                      <span>Copiar o link</span>
                    }
                  </button>
                </div>
              }
            </div>
          </header>

          <!-- O cadastro: editar e retirar (quem respondeu fica travado). Mandar e-mail fica no "Por e-mail", acima. -->
          <section class="mt-6" aria-labelledby="titulo-cadastro">
            <h3 id="titulo-cadastro" class="secao-titulo">Cadastro</h3>
            @if (lider().situacao === 'RESPONDEU') {
              <p class="mt-3 flex items-start gap-3 rounded-[20px] bg-white px-4 py-3.5 text-[14px] leading-snug text-abeto-2 ring-1 ring-linha/70">
                <svg class="mt-0.5 shrink-0" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
                <span>{{ primeiro() }} já respondeu: o cadastro fica travado, porque as respostas já estão no resultado do grupo. Para corrigir algo, fale com quem administra o Diagnóstico do Líder.</span>
              </p>
            } @else {
              <div class="mt-3 overflow-hidden rounded-[20px] bg-white ring-1 ring-linha/70">
                <button type="button" class="acao-gestao" [attr.aria-busy]="carregandoEdicao()" [disabled]="!!fazendo()" (click)="editar()">
                  <span class="icone-gestao" aria-hidden="true">
                    @if (carregandoEdicao()) {
                      <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto"></span>
                    } @else {
                      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1"><path d="M4 20h4L19 9l-4-4L4 16v4z" stroke-linejoin="round" /></svg>
                    }
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="block text-[15.5px] font-semibold">{{ lider().alerta ? 'Corrigir o e-mail' : 'Editar dados' }}</span>
                    <span class="block text-[13.5px] leading-snug text-abeto-2">Nome, e-mail, WhatsApp e cargo</span>
                  </span>
                  <svg class="shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
                </button>

                @if (confirmando() === 'retirar') {
                  <div class="confirmar-gestao" role="group" aria-labelledby="confirmar-retirada">
                    <p id="confirmar-retirada" class="text-[15px] leading-snug">
                      <strong class="font-semibold">Retirar {{ primeiro() }} da avaliação?</strong>
                      <span class="block text-[14px] text-abeto-2">O link pessoal para de funcionar e {{ primeiro() }} sai da lista e das contas. Dá para devolver depois.</span>
                    </p>
                    <div class="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                      <button type="button" class="botao-secundario min-h-12 bg-white px-5 text-[15px]" (click)="confirmando.set(null)">Cancelar</button>
                      <button type="button" class="botao-retirar" [attr.aria-busy]="fazendo() === 'retirar'" [disabled]="!!fazendo()" (click)="retirar()">
                        {{ fazendo() === 'retirar' ? 'Retirando…' : 'Retirar da avaliação' }}
                      </button>
                    </div>
                  </div>
                } @else {
                  <button type="button" class="acao-gestao" [disabled]="!!fazendo()" (click)="pedir('retirar')">
                    <span class="icone-gestao text-travada" aria-hidden="true">
                      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1"><circle cx="10" cy="8" r="3.5" /><path d="M3.5 19.5c.7-3.4 3.2-5.5 6.5-5.5 1.4 0 2.6.4 3.6 1M15.5 16.5h6" stroke-linecap="round" /></svg>
                    </span>
                    <span class="min-w-0 flex-1">
                      <span class="block text-[15.5px] font-semibold text-travada">Retirar da avaliação</span>
                      <span class="block text-[13.5px] leading-snug text-abeto-2">Saiu da empresa ou não vai participar</span>
                    </span>
                    <svg class="shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  </button>
                }
              </div>
              @if (feito(); as f) {
                <p class="aparecer mt-3 flex items-center gap-2 text-[14.5px] font-semibold text-[#17714A]" role="status">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  {{ f }}
                </p>
              }
              @if (erroAcao(); as e) {
                <p class="aparecer mt-3 text-[14.5px] leading-snug font-medium text-travada" role="alert">{{ e }}</p>
              }
            }
          </section>

          <!-- A linha do tempo, do mais recente para o mais antigo, por dia. -->
          <section class="mt-7" aria-labelledby="titulo-passos">
            <h3 id="titulo-passos" class="secao-titulo">Linha do tempo</h3>
            @if (passos.isLoading() && !passos.hasValue()) {
              <div class="mt-4 space-y-4" aria-busy="true">
                @for (i of [1, 2, 3]; track i) {
                  <div class="flex gap-3"><span class="size-9 shrink-0 animate-pulse rounded-full bg-white"></span><span class="h-12 flex-1 animate-pulse rounded-xl bg-white/70"></span></div>
                }
              </div>
            } @else if (passos.error()) {
              <p class="mt-4 rounded-2xl bg-white px-4 py-4 text-[15px] text-abeto-2" role="alert">
                Não deu para carregar agora.
                <button type="button" class="font-semibold text-abeto underline underline-offset-4" (click)="passos.reload()">Tentar de novo</button>
              </p>
            } @else {
              @for (dia of dias(); track dia.rotulo; let d = $index) {
                <p class="mt-5 mb-3 text-[13px] font-semibold text-abeto-2 first-letter:uppercase">{{ dia.rotulo }}</p>
                <ol>
                  @for (p of dia.passos; track $index) {
                    <li class="passo surgir pb-5" [style.--i]="d * 2 + $index">
                      <span class="ponto" [class]="'tom-' + p.tom" aria-hidden="true">
                        @switch (p.tipo) {
                          @case ('ENVIADO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M4 7l8 6 8-6" stroke-linecap="round" stroke-linejoin="round" /></svg> }
                          @case ('LEMBRETE') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16zM10 20.5a2 2 0 0 0 4 0" stroke-linecap="round" stroke-linejoin="round" /></svg> }
                          @case ('ABERTO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" /><circle cx="12" cy="12" r="2.8" /></svg> }
                          @case ('COMECOU_RESPONDER') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M4 20h4L19 9l-4-4L4 16v4z" stroke-linejoin="round" /></svg> }
                          @case ('CONCLUIDO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg> }
                          @case ('RESPONDEU_DE_NOVO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v4.5h-4.5" stroke-linecap="round" stroke-linejoin="round" /></svg> }
                          @case ('RESULTADO_ENVIADO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg> }
                          @case ('COMECOU_PELO_LINK') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1" stroke-linecap="round" /><path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1" stroke-linecap="round" /></svg> }
                          @case ('CRIADO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="10" cy="8" r="3.5" /><path d="M3.5 19.5c.7-3.4 3.2-5.5 6.5-5.5 1.4 0 2.6.4 3.6 1M18 13v6M15 16h6" stroke-linecap="round" /></svg> }
                          @case ('DADOS_CONFIRMADOS') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M7 12.5l2.5 2.5L15 9.5" stroke-linecap="round" stroke-linejoin="round" /></svg> }
                          @case ('CONTATO_EMAIL') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1"><path d="M4 12l16-8-6 16-2.5-6.5L4 12z" stroke-linejoin="round" /></svg> }
                          @case ('CONTATO_WHATSAPP') { <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2.2a9.7 9.7 0 0 0-8.4 14.6L2.3 21.7l5-1.3A9.7 9.7 0 1 0 12 2.2zm0 17.7a8 8 0 0 1-4.1-1.1l-.3-.2-3 .8.8-2.9-.2-.3A8 8 0 1 1 12 19.9z" /></svg> }
                          @case ('EDITADO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M4 20h4L19 9l-4-4L4 16v4z" stroke-linejoin="round" /></svg> }
                          @case ('INATIVADO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="10" cy="8" r="3.5" /><path d="M3.5 19.5c.7-3.4 3.2-5.5 6.5-5.5 1.4 0 2.6.4 3.6 1M15.5 16.5h6" stroke-linecap="round" /></svg> }
                          @case ('REATIVADO') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="10" cy="8" r="3.5" /><path d="M3.5 19.5c.7-3.4 3.2-5.5 6.5-5.5 1.4 0 2.6.4 3.6 1M18 13v6M15 16h6" stroke-linecap="round" /></svg> }
                          @case ('DADOS_CORRIGIDOS') { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M8 15l6.5-6.5 2 2L10 17H8v-2z" stroke-linejoin="round" /></svg> }
                          @default { <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 8v5M12 16.5v.01M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" stroke-linecap="round" stroke-linejoin="round" /></svg> }
                        }
                      </span>
                      <div class="min-w-0 pt-1.5">
                        <div class="flex items-baseline justify-between gap-3">
                          <p class="text-[15.5px] leading-snug font-semibold" [class.text-travada]="p.tom === 'alerta'">{{ p.titulo }}</p>
                          <time class="shrink-0 text-[13px] text-abeto-2 tabular-nums" [attr.datetime]="p.quando" [title]="quandoCompleto(p.quando)">{{ hora(p.quando) }}</time>
                        </div>
                        @if (p.detalhe) {
                          <p class="mt-0.5 text-[14px] leading-snug text-abeto-2">{{ p.detalhe }}</p>
                        }
                        @if (p.por) {
                          <p class="mt-0.5 truncate text-[13px] text-abeto-3" [title]="p.por">por {{ p.por }}</p>
                        }
                        @if (entregaDe(p); as e) {
                          <span class="entrega mt-2" [class]="'entrega-' + e.tom">
                            <i class="block size-1.5 rounded-full bg-current" aria-hidden="true"></i>{{ e.texto }}
                          </span>
                        }
                      </div>
                    </li>
                  }
                </ol>
              } @empty {
                <p class="mt-4 rounded-2xl bg-white px-4 py-6 text-center text-[15px] text-abeto-2">Nada registrado ainda.</p>
              }
            }
          </section>

          <p class="mt-4 flex items-start gap-2.5 rounded-2xl bg-white/70 px-4 py-3 text-[13.5px] leading-snug text-abeto-2 ring-1 ring-linha/60">
            <svg class="mt-0.5 shrink-0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
            <span>Aqui aparece só a participação. As respostas e o resultado são só de {{ primeiro() }}: a empresa nunca vê.</span>
          </p>
        </div>
      </div>

      @if (edicao(); as e) {
        <app-novo-convite [empresaId]="empresaId()" [cargos]="gestao()?.cargos ?? []" [edicao]="e" (adicionado)="salvou()" (fechou)="edicao.set(null)" />
      }
    </dialog>
  `,
})
export class LinhaDoTempoRh {
  readonly lider = input.required<LiderRh>();
  readonly empresaId = input.required<number>();
  /** "3 de 6": a posição na lista que está na tela. */
  readonly posicao = input('');
  readonly temAnterior = input(false);
  readonly temProximo = input(false);
  /** Os atalhos de contato, os mesmos da linha da lista. */
  readonly whatsapp = input<string | null>(null);
  readonly copiado = input(false);

  /** Cargos e se dá para mandar e-mail (da gestão da página do RH). */
  readonly gestao = input<GestaoRh | undefined>();

  readonly fechar = output<void>();
  readonly anterior = output<void>();
  readonly proximo = output<void>();
  readonly copiar = output<void>();
  /** "Por e-mail": a página abre o modal (pela plataforma ou pelo e-mail do RH). */
  readonly porEmail = output<void>();
  /** Abriu o WhatsApp com a mensagem: a página registra na linha do tempo. */
  readonly porWhatsapp = output<void>();
  /** Editou ou retirou: a lista se atualiza. */
  readonly mudou = output<void>();
  /** Saiu da avaliação: a página fecha o painel e oferece desfazer. */
  readonly retirado = output<LiderRh>();

  private readonly http = inject(HttpClient);
  protected readonly folha = viewChild.required<ElementRef<HTMLDialogElement>>('folha');

  protected readonly passos = httpResource<PassoRh[]>(
    () => `${API_RH}/empresas/${this.empresaId()}/lideres/${this.lider().id}/linha-do-tempo`,
  );

  /**
   * A confirmação aberta, o que está em andamento e o que acabou de dar certo: zeram ao trocar de
   * líder, mas não quando a lista se atualiza (o mesmo líder chega como objeto novo a cada minuto;
   * por isso a origem é o id num computed, que só muda quando o número muda).
   */
  private readonly liderId = computed(() => this.lider().id);
  protected readonly confirmando = linkedSignal<number, 'retirar' | null>({ source: this.liderId, computation: () => null });
  protected readonly fazendo = signal<'retirar' | null>(null);
  protected readonly feito = linkedSignal<number, string | null>({ source: this.liderId, computation: () => null });
  protected readonly erroAcao = linkedSignal<number, string | null>({ source: this.liderId, computation: () => null });
  protected readonly carregandoEdicao = signal(false);
  protected readonly edicao = signal<EdicaoLider | null>(null);

  private api(): string {
    return `${API_RH}/empresas/${this.empresaId()}/lideres/${this.lider().id}`;
  }

  protected readonly situacao = computed(() => SITUACAO[this.lider().situacao]);
  protected readonly primeiro = computed(() => primeiroNome(this.lider().nome));
  protected readonly iniciais = computed(() =>
    this.lider()
      .nome.split(/\s+/)
      .filter(Boolean)
      .filter((_, i, todos) => i === 0 || i === todos.length - 1)
      .map((p) => p.charAt(0).toUpperCase())
      .join(''),
  );
  protected readonly progresso = computed(() => {
    const l = this.lider();
    return l.perguntas ? Math.round(((l.respondidas ?? 0) / l.perguntas) * 100) : 0;
  });

  /** O que fazer agora, numa frase. */
  protected readonly proximoPasso = computed(() => {
    const l = this.lider();
    const nome = primeiroNome(l.nome);
    switch (l.situacao) {
      case 'RESPONDEU':
        return l.respondendoDeNovo
          ? `${nome} respondeu e começou de novo; a resposta nova vale quando terminar.`
          : `${nome} respondeu${l.respondeuEm ? ' ' + minuscula(quando(l.respondeuEm)) : ''}. Não precisa fazer mais nada.`;
      case 'COMECOU':
        return `${nome} está no meio. Um lembrete curto costuma bastar: o link continua de onde parou.`;
      case 'ABRIU':
        return `${nome} abriu o convite, mas não começou. Vale lembrar o prazo e que as respostas não são mostradas à empresa.`;
      default:
        return l.enviadoEm
          ? `O convite saiu ${minuscula(quando(l.enviadoEm))} e ainda não foi aberto. Uma mensagem pelo WhatsApp costuma funcionar melhor que outro e-mail.`
          : `O convite ainda não foi enviado. Mande pelo WhatsApp ou por e-mail: pela plataforma ou pelo seu próprio e-mail.`;
    }
  });

  /** Os passos do mais recente para o mais antigo, agrupados por dia ("Hoje", "Ontem", "segunda-feira, 23 de novembro"). */
  protected readonly dias = computed(() => {
    const passos = [...(this.passos.hasValue() ? this.passos.value() : [])].reverse();
    const grupos: { rotulo: string; passos: PassoRh[] }[] = [];
    for (const p of passos) {
      const rotulo = this.rotuloDoDia(p.quando);
      const ultimo = grupos.at(-1);
      if (ultimo?.rotulo === rotulo) ultimo.passos.push(p);
      else grupos.push({ rotulo, passos: [p] });
    }
    return grupos;
  });

  constructor() {
    afterNextRender(() => this.folha().nativeElement.showModal());

    // A lista se atualiza a cada minuto: a linha do tempo do líder aberto vem junto. (Trocar de
    // líder já busca a linha dele, pelo endereço novo.)
    let ultimo: number | null = null;
    effect(() => {
      const id = this.lider().id;
      if (id === ultimo) untracked(() => this.passos.reload());
      ultimo = id;
    });
  }

  protected pedir(acao: 'retirar') {
    this.erroAcao.set(null);
    this.feito.set(null);
    this.confirmando.set(acao);
  }

  /** Abre o formulário com os dados de agora (o servidor confere que o líder é desta empresa). */
  protected async editar() {
    if (this.carregandoEdicao()) return;
    this.erroAcao.set(null);
    this.feito.set(null);
    this.confirmando.set(null);
    this.carregandoEdicao.set(true);
    try {
      const d = await firstValueFrom(this.http.get<LiderEditavel>(this.api()));
      if (d.respondeu) {
        this.erroAcao.set(`${this.primeiro()} acabou de responder: o cadastro ficou travado.`);
        this.mudou.emit();
        return;
      }
      this.edicao.set({ url: this.api(), nome: d.nome, email: d.email, whatsapp: d.whatsapp, cargoId: d.cargoId, cargoOutro: d.cargoOutro });
    } catch (e) {
      this.erroAcao.set(problemaDe(e).detail ?? 'Não foi possível abrir os dados agora. Tente de novo.');
    } finally {
      this.carregandoEdicao.set(false);
    }
  }

  protected salvou() {
    this.feito.set('Dados salvos.');
    this.mudou.emit();
  }


  protected async retirar() {
    if (this.fazendo()) return;
    this.fazendo.set('retirar');
    this.erroAcao.set(null);
    const lider = this.lider();
    try {
      await firstValueFrom(this.http.post(`${this.api()}/retirar`, null));
      // A página fecha o painel (tira o ?lider= do endereço): esperar o "close" do diálogo não dá,
      // porque a lista atualizada pode tirar o líder (e este painel) da tela antes de o evento chegar.
      this.retirado.emit(lider);
    } catch (e) {
      this.erroAcao.set(problemaDe(e).detail ?? 'Não foi possível retirar agora. Tente de novo.');
      this.mudou.emit();
    } finally {
      this.fazendo.set(null);
    }
  }

  protected entregaDe(p: PassoRh) {
    return entrega(p);
  }

  protected hora(iso: string): string {
    return HORA.format(new Date(iso));
  }

  protected quandoCompleto(iso: string): string {
    return quando(iso);
  }

  /** Clicar no fundo escurecido (fora do painel) fecha. */
  protected cliqueFora(evento: MouseEvent) {
    if (evento.target === this.folha().nativeElement) this.folha().nativeElement.close();
  }

  /** ← e → passam de líder no computador (fora dos campos de texto). */
  protected tecla(evento: KeyboardEvent) {
    const alvo = evento.target as HTMLElement | null;
    if (alvo?.closest('input, textarea, select, [contenteditable]')) return;
    // Com o formulário de edição aberto por cima, as setas são dele.
    if (this.edicao()) return;
    if (evento.key === 'ArrowLeft' && this.temAnterior()) this.anterior.emit();
    if (evento.key === 'ArrowRight' && this.temProximo()) this.proximo.emit();
  }

  private rotuloDoDia(iso: string): string {
    const data = new Date(iso);
    const hoje = new Date();
    const ontem = new Date();
    ontem.setDate(hoje.getDate() - 1);
    const mesmo = (a: Date, b: Date) => a.toDateString() === b.toDateString();
    if (mesmo(data, hoje)) return 'Hoje';
    if (mesmo(data, ontem)) return 'Ontem';
    return DIA.format(data);
  }
}
