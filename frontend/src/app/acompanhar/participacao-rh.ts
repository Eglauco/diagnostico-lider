import { NgTemplateOutlet } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../api/diagnostico-api';
import {
  buscarArquivo,
  compartilharArquivo,
  podeCompartilharArquivos,
  salvarBlob,
} from '../compartilhado/download';
import { prazoPorExtenso, quando } from '../compartilhado/formatos';
import { telaDeComputador } from '../compartilhado/util';
import { AlvoEmail, EmailRh } from './email-rh';
import { AvisoRh, GestaoLideresRh } from './gestao-lideres-rh';
import { LinhaDoTempoRh } from './linha-do-tempo-rh';
import { mensagemDoLider } from './mensagens-rh';
import { API_RH, GestaoRh, GrupoRh, LiderRh, numeroWhatsapp, PainelRh, primeiroNome, SessaoRh, SituacaoLider } from './rh-api';

const BUSCA_A_PARTIR_DE = 8;

/** Tirar acentos e caixa para a busca ("joao" acha "João"). */
const simples = (texto: string) =>
  texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();

const minuscula = (texto: string) => texto.charAt(0).toLowerCase() + texto.slice(1);
const maiuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

const COR_GRUPO: Record<SituacaoLider, string> = {
  COMECOU: 'var(--color-plato)',
  ABRIU: '#D9A441',
  NAO_ABRIU: 'var(--color-abeto-3)',
  RESPONDEU: 'var(--color-ascensao)',
};

/**
 * A participação ao vivo: o placar, quem falta (com o WhatsApp e o link pessoal de cada um, para
 * cobrar em um toque) e quem já respondeu. Nenhuma resposta nem resultado individual.
 *
 * No celular, tudo numa coluna. No computador, o placar fica parado à esquerda e a lista rola à
 * direita, em colunas (nome, cargo, situação, ações).
 */
@Component({
  selector: 'app-participacao-rh',
  imports: [EmailRh, GestaoLideresRh, LinhaDoTempoRh, NgTemplateOutlet],
  host: { class: 'block' },
  styleUrl: './acompanhar.css',
  template: `
    @let p = painel().participacao;

    <div [class]="p.total > 0 ? 'lg:grid lg:grid-cols-[300px_minmax(0,1fr)] lg:items-start lg:gap-8 xl:grid-cols-[350px_minmax(0,1fr)] xl:gap-10' : 'lg:mx-auto lg:max-w-xl'">
      <!-- Placar, atalho do resultado e relatório: no computador, uma coluna que fica parada. -->
      <div #coluna [class.coluna-placar]="p.total > 0" [class.fixa]="placarCabe()">
        <section class="pt-4" [class.placar-rh]="p.total > 0" aria-labelledby="titulo-participacao">
          @if (prazo(); as pz) {
            <p class="surgir inline-flex min-h-9 items-center gap-2 rounded-[18px] px-3.5 py-1.5 text-[14px] leading-snug font-semibold" style="--i: 0"
               [class]="pz.encerrada ? 'bg-white text-abeto-2 ring-1 ring-linha' : pz.urgente ? 'bg-[#FDEDEF] text-travada' : 'bg-white text-abeto ring-1 ring-linha'">
              <svg class="shrink-0" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><circle cx="12" cy="13" r="8" /><path d="M12 9v4l2.5 2M9 2.5h6" stroke-linecap="round" /></svg>
              {{ pz.texto }}
            </p>
          }

          <h1 id="titulo-participacao" class="sr-only">Participação</h1>
          @if (p.total > 0) {
            <div class="surgir mt-5 flex items-end justify-between gap-4" style="--i: 1">
              <p class="display text-[76px] leading-[0.8] font-bold tabular-nums" [attr.aria-label]="p.responderam + ' de ' + p.total + ' líderes responderam'">
                {{ p.responderam }}<span class="ml-1 text-[28px] font-semibold tracking-normal text-abeto-3">/{{ p.total }}</span>
              </p>
              <p class="display pb-1 text-[34px] leading-none font-bold text-abeto-2 tabular-nums" aria-hidden="true">{{ percentual() }}%</p>
            </div>
            <p class="surgir mt-3 text-[17px] font-semibold" style="--i: 2">
              {{ p.responderam === 1 ? 'líder respondeu' : 'líderes responderam' }}
              @if (p.hoje > 0) {
                <span class="ml-1 inline-flex min-h-7 items-center rounded-full bg-ascensao-suave px-2.5 text-[13.5px] text-[#17714A]">+{{ p.hoje }} hoje</span>
              }
            </p>

            @if (p.peloLink > 0) {
              <p class="surgir mt-1.5 text-[14.5px] text-abeto-2" style="--i: 2">
                {{ convidados() }} {{ convidados() === 1 ? 'convidado' : 'convidados' }} · {{ p.peloLink }} pelo link aberto da empresa
              </p>
            }

            <!-- Barra: quem respondeu, quem está no meio, quem nem abriu. -->
            <div class="surgir mt-5" style="--i: 3">
              <div class="flex h-3 gap-[3px] overflow-hidden rounded-full bg-white ring-1 ring-linha/60" aria-hidden="true">
                @for (parte of partes(); track parte.codigo) {
                  @if (parte.quantidade) {
                    <span class="h-full transition-[width] duration-700" [style.width.%]="(parte.quantidade / p.total) * 100" [style.background]="parte.cor"></span>
                  }
                }
              </div>
              <dl class="mt-4 grid grid-cols-2 gap-x-4 gap-y-2.5 text-[14.5px]">
                @for (parte of partes(); track parte.codigo) {
                  <div class="flex items-center gap-2">
                    <i class="block size-2.5 shrink-0 rounded-full" [style.background]="parte.cor" aria-hidden="true"></i>
                    <dt class="text-abeto-2">{{ parte.rotulo }}</dt>
                    <dd class="ml-auto font-bold tabular-nums">{{ parte.quantidade }}</dd>
                  </div>
                }
              </dl>
            </div>
          } @else {
            <div class="surgir mt-5 cartao-rh p-5" style="--i: 1">
              <p class="display text-[26px] leading-tight font-bold">Ninguém na lista ainda</p>
              <p class="mt-2 text-[16px] text-abeto-2">
                Adicione os líderes um a um ou importe a planilha. Quem começar pelo link aberto da empresa também aparece aqui.
              </p>
              <app-gestao-lideres-rh [empresaId]="empresaId()" [empresa]="painel().empresa.nome" [gestao]="gestaoAtual()" (mudou)="atualizar.emit()" (aviso)="mostrar($event)" />
            </div>
          }

          <div class="surgir mt-5 flex items-center justify-between gap-3 text-[13.5px] text-abeto-2" style="--i: 4">
            <span class="inline-flex items-center gap-2">
              <i class="ao-vivo" aria-hidden="true"></i>
              Ao vivo · atualizado {{ atualizado() }}
            </span>
            <button type="button" class="-mr-2 inline-flex min-h-11 items-center gap-1.5 rounded-full px-3 font-semibold text-abeto hover:bg-nevoa-2" [attr.aria-busy]="atualizando()" (click)="atualizar.emit()">
              <svg [class.animate-spin]="atualizando()" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v4.5h-4.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
              Atualizar
            </button>
          </div>
        </section>

        <!-- Atalho para o resultado do grupo. -->
        <button type="button" class="surgir cartao-rh cartao-clicavel mt-6 flex w-full items-center gap-4 p-4 text-left" style="--i: 5" (click)="verResultado.emit()">
          <span class="grid size-12 shrink-0 place-items-center rounded-2xl bg-abeto text-white" aria-hidden="true">
            @if (painel().concluidos >= 3) {
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke-width="2.2"><path d="M3 18c3 0 4-9 8-9s5 4 10 1" stroke="var(--color-brasa)" stroke-linecap="round" /><circle cx="8" cy="12.5" r="1.5" fill="#fff" /><circle cx="13" cy="9.3" r="1.5" fill="#fff" /><circle cx="17" cy="10.8" r="1.5" fill="#fff" /></svg>
            } @else {
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
            }
          </span>
          <span class="min-w-0 flex-1">
            <span class="block text-[16px] font-semibold">Resultado do grupo</span>
            <span class="block text-[14px] leading-snug text-abeto-2">
              @if (painel().concluidos >= 3) {
                Índice, faixas e dimensões, sem identificar ninguém.
              } @else {
                Abre com 3 respostas: {{ 3 - painel().concluidos === 1 ? 'falta 1' : 'faltam ' + (3 - painel().concluidos) }}.
              }
            </span>
          </span>
          <svg class="shrink-0 text-abeto-3" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </button>

        <!-- No computador, o relatório fica junto do placar. -->
        @if (p.total > 0 && computador()) {
          <ng-container [ngTemplateOutlet]="relatorio" />
        }
      </div>

      @if (p.total > 0) {
        <!-- Quem falta e quem já respondeu. -->
        <section class="mt-10 lg:mt-0 lg:pt-4" aria-labelledby="titulo-lideres">
          <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
            <h2 id="titulo-lideres" class="display text-[26px] leading-tight font-bold lg:text-[30px]">
              {{ faltam() === 0 ? 'Todos responderam' : faltam() === 1 ? 'Falta 1 líder' : 'Faltam ' + faltam() + ' líderes' }}
            </h2>
            <!-- Cobrar todos de uma vez: o mesmo modal do e-mail (pela plataforma ou pelo e-mail do RH). -->
            @if (faltam() > 1) {
              <button type="button" class="botao-secundario min-h-12 bg-white px-4 text-[15px]" aria-haspopup="dialog" (click)="lembrarTodos()">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16zM10 20.5a2 2 0 0 0 4 0" stroke-linecap="round" stroke-linejoin="round" /></svg>
                Lembrar os {{ faltam() }} que faltam
              </button>
            }
          </div>
          <p class="mt-1.5 text-[15px] leading-snug text-abeto-2">
            @if (faltam() > 0) {
              {{ computador() ? 'Clique no WhatsApp ou no e-mail' : 'Toque no WhatsApp' }} para cobrar com uma mensagem pronta e o link pessoal.
            }
            {{ computador() ? 'Clique' : 'Toque' }} no nome para ver a linha do tempo e editar o cadastro.
          </p>

          <app-gestao-lideres-rh [empresaId]="empresaId()" [empresa]="painel().empresa.nome" [gestao]="gestaoAtual()" (mudou)="atualizar.emit()" (aviso)="mostrar($event)" />

          @if (mostrarOrigens() || p.total >= buscaAPartirDe) {
            <div class="mt-5 flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-center">
              @if (mostrarOrigens()) {
                <div class="-mx-5 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none] sm:-mx-8 sm:px-8 lg:mx-0 lg:shrink-0 lg:overflow-visible lg:px-0 lg:pb-0" role="group" aria-label="Mostrar por origem">
                  @for (o of origens(); track o.valor) {
                    <button type="button" class="chip-rh" [attr.aria-pressed]="origem() === o.valor" (click)="origem.set(o.valor)">
                      {{ o.texto }} <span class="tabular-nums opacity-70">{{ o.total }}</span>
                    </button>
                  }
                </div>
              }
              @if (p.total >= buscaAPartirDe) {
                <div class="relative lg:ml-auto lg:max-w-80 lg:min-w-52 lg:flex-1">
                  <label for="busca-lider" class="sr-only">Buscar líder pelo nome</label>
                  <svg class="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" stroke-linecap="round" /></svg>
                  <input
                    id="busca-lider"
                    #campoBusca
                    type="search"
                    enterkeyhint="search"
                    autocomplete="off"
                    placeholder="Buscar líder pelo nome"
                    class="h-13 w-full rounded-full bg-white pr-4 pl-11 text-[16px] ring-1 ring-linha outline-none placeholder:text-abeto-3 focus:ring-2 focus:ring-abeto lg:h-12"
                    [value]="busca()"
                    (input)="busca.set(campoBusca.value)"
                  />
                </div>
              }
            </div>
          }

          @for (g of grupos(); track g.codigo) {
            @if (g.codigo === 'RESPONDEU' && !busca()) {
              <details class="dobra cartao-rh mt-5 px-4 lg:px-5">
                <summary class="flex min-h-15 cursor-pointer list-none items-center gap-3">
                  <i class="block size-2.5 shrink-0 rounded-full" [style.background]="cor(g.codigo)" aria-hidden="true"></i>
                  <span class="flex-1 text-[16px] font-semibold">{{ g.titulo }}</span>
                  <span class="text-[15px] font-semibold text-abeto-2 tabular-nums">{{ g.lideres.length }}</span>
                  <svg class="seta shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                </summary>
                <ul class="divide-y divide-linha/70 border-t border-linha/70">
                  @for (l of g.lideres; track l.id) {
                    <li class="flex items-center gap-3 py-3">
                      <ng-container [ngTemplateOutlet]="colunas" [ngTemplateOutletContext]="{ $implicit: l }" />
                      <span class="flex shrink-0 justify-end lg:w-34" aria-hidden="true">
                        <svg class="text-ascensao" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                      </span>
                    </li>
                  }
                </ul>
              </details>
            } @else {
              <section class="cartao-rh mt-5 px-4 pt-4 lg:px-5" [attr.aria-labelledby]="'grupo-' + g.codigo">
                <div class="flex items-center gap-3">
                  <i class="block size-2.5 shrink-0 rounded-full" [style.background]="cor(g.codigo)" aria-hidden="true"></i>
                  <h3 [id]="'grupo-' + g.codigo" class="flex-1 text-[16px] font-semibold">{{ g.titulo }}</h3>
                  <span class="text-[15px] font-semibold text-abeto-2 tabular-nums">{{ g.lideres.length }}</span>
                </div>
                @if (!busca()) {
                  <p class="mt-1 pl-5.5 text-[14px] leading-snug text-abeto-2">{{ g.dica }}</p>
                }
                <ul class="mt-2 divide-y divide-linha/70">
                  @for (l of g.lideres; track l.id) {
                    <li class="flex items-center gap-2.5 py-3">
                      <ng-container [ngTemplateOutlet]="colunas" [ngTemplateOutletContext]="{ $implicit: l }" />
                      @if (l.link) {
                        <span class="flex shrink-0 items-center justify-end gap-2.5 lg:w-34 lg:gap-2">
                          @if (whatsapp(l); as w) {
                            <a class="acao-linha acao-whatsapp" [href]="w" target="_blank" rel="noopener" [attr.aria-label]="'Mandar o link para ' + l.nome + ' pelo WhatsApp'" title="Mandar pelo WhatsApp" (click)="usouWhatsapp(l)">
                              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.2a9.7 9.7 0 0 0-8.4 14.6L2.3 21.7l5-1.3A9.7 9.7 0 1 0 12 2.2zm0 17.7a8 8 0 0 1-4.1-1.1l-.3-.2-3 .8.8-2.9-.2-.3A8 8 0 1 1 12 19.9zm4.4-6c-.2-.1-1.4-.7-1.7-.8-.2-.1-.4-.1-.5.1l-.8 1c-.1.2-.3.2-.5.1a6.6 6.6 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.5-.4h-.5a.9.9 0 0 0-.6.3 2.6 2.6 0 0 0-.8 1.9 4.5 4.5 0 0 0 .9 2.4 10.4 10.4 0 0 0 4 3.5c1.5.6 2 .7 2.8.6a2.3 2.3 0 0 0 1.5-1.1 1.9 1.9 0 0 0 .1-1.1c0-.1-.2-.2-.4-.3z" /></svg>
                            </a>
                          }
                          <!-- E-mail: no celular, só quando não há WhatsApp; no computador, sempre. -->
                          @if (!whatsapp(l) || computador()) {
                            <button type="button" class="acao-linha" aria-haspopup="dialog" [attr.aria-label]="'Mandar e-mail para ' + l.nome + ': pela plataforma ou pelo seu e-mail'" title="Mandar por e-mail" (click)="abrirEmail(l)">
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M4 7l8 6 8-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                            </button>
                          }
                          <button type="button" class="acao-linha" [class.acao-feita]="copiado() === l.id" [attr.aria-label]="'Copiar o link pessoal de ' + l.nome" [title]="copiado() === l.id ? 'Link copiado' : 'Copiar o link pessoal'" (click)="copiar(l)">
                            @if (copiado() === l.id) {
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" /></svg>
                            } @else {
                              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1" stroke-linecap="round" /><path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1" stroke-linecap="round" /></svg>
                            }
                          </button>
                        </span>
                      }
                    </li>
                  }
                </ul>
              </section>
            }
          } @empty {
            <p class="mt-5 rounded-2xl bg-white/70 px-4 py-6 text-center text-[15px] text-abeto-2 ring-1 ring-linha/60">
              {{ busca() ? 'Ninguém com “' + busca() + '” no nome.' : 'Ninguém nesta origem.' }}
            </p>
          }

          <!-- Quem saiu da avaliação: dá para devolver quem não tinha respondido. -->
          @if (retirados().length) {
            <details class="dobra mt-5 rounded-[20px] bg-white/60 px-4 ring-1 ring-linha/60 lg:px-5">
              <summary class="flex min-h-13 cursor-pointer list-none items-center gap-3">
                <svg class="shrink-0 text-abeto-3" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" aria-hidden="true"><circle cx="10" cy="8" r="3.5" /><path d="M3.5 19.5c.7-3.4 3.2-5.5 6.5-5.5 1.4 0 2.6.4 3.6 1M15.5 16.5h6" stroke-linecap="round" /></svg>
                <span class="flex-1 text-[15px] font-semibold text-abeto-2">
                  {{ retirados().length === 1 ? '1 líder retirado da avaliação' : retirados().length + ' líderes retirados da avaliação' }}
                </span>
                <svg class="seta shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </summary>
              <ul class="divide-y divide-linha/70 border-t border-linha/70">
                @for (r of retirados(); track r.id) {
                  <li class="flex items-center gap-3 py-2.5">
                    <span class="min-w-0 flex-1">
                      <span class="block truncate text-[15px] font-semibold">{{ r.nome }}</span>
                      <span class="block truncate text-[13.5px] text-abeto-2">{{ r.cargo || 'Sem cargo' }}</span>
                    </span>
                    @if (r.podeVoltar) {
                      <button type="button" class="botao-secundario min-h-11 shrink-0 bg-white px-4 text-[14px]" [attr.aria-busy]="devolvendo() === r.id" [disabled]="devolvendo() !== null" (click)="devolver(r)">
                        {{ devolvendo() === r.id ? 'Devolvendo…' : 'Devolver à lista' }}
                      </button>
                    } @else {
                      <span class="max-w-40 shrink-0 text-right text-[13px] leading-snug text-abeto-3">Já tinha respondido: só quem administra devolve</span>
                    }
                  </li>
                }
              </ul>
            </details>
          } @else if (p.inativos > 0) {
            <p class="mt-4 text-[13.5px] text-abeto-2">
              {{ p.inativos === 1 ? '1 líder foi retirado da avaliação e não aparece aqui.' : p.inativos + ' líderes foram retirados da avaliação e não aparecem aqui.' }}
            </p>
          }
        </section>

        <!-- No celular, o relatório vem depois da lista. -->
        @if (!computador()) {
          <ng-container [ngTemplateOutlet]="relatorio" />
        }
      }
    </div>

    <!--
      Nome, cargo e situação de um líder: uma pilha no celular; no computador, duas colunas (nome com
      o cargo embaixo | situação) e, a partir de 1280 px, três (nome | cargo | situação).
    -->
    <ng-template #colunas let-l>
      <!-- A linha inteira (nome, cargo, situação) abre a linha do tempo do líder. -->
      <button type="button" class="linha-lider -my-1 -ml-2 min-w-0 flex-1 rounded-xl px-2 py-1 text-left lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-center lg:gap-x-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,1.2fr)]" [attr.aria-haspopup]="'dialog'" (click)="abrirLider.emit(l.id)">
        @if (computador()) {
          <span class="block min-w-0 xl:contents">
            <span class="flex min-w-0 items-center gap-2">
              <span class="nome-lider truncate text-[16px] font-semibold" [title]="l.nome">{{ l.nome }}</span>
              @if (l.origem === 'LINK') {
                <span class="etiqueta-link">pelo link</span>
              }
            </span>
            <span class="block truncate text-[14px]" [class]="l.cargo ? 'text-abeto-2' : 'text-abeto-3'" [title]="l.cargo ?? ''">{{ l.cargo || 'Sem cargo' }}</span>
          </span>
          <span class="block text-[14px] leading-snug text-abeto-2">{{ situacao(l) }}</span>
        } @else {
          <span class="flex min-w-0 items-center gap-2">
            <span class="nome-lider truncate text-[16px] font-semibold">{{ l.nome }}</span>
            @if (l.origem === 'LINK') {
              <span class="etiqueta-link">pelo link</span>
            }
          </span>
          <span class="block text-[14px] leading-snug text-abeto-2">{{ detalhe(l) }}</span>
        }
        @if (l.alerta) {
          <span class="mt-0.5 flex items-center gap-1.5 text-[13.5px] font-semibold text-travada lg:col-span-full">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M12 8v5M12 16.5v.01M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" stroke-linecap="round" stroke-linejoin="round" /></svg>
            {{ l.alerta }}: mande pelo WhatsApp
          </span>
        }
        <span class="sr-only">: ver a linha do tempo</span>
      </button>
    </ng-template>

    <!-- Para mandar adiante: o relatório de participação (o mesmo do e-mail diário). -->
    <ng-template #relatorio>
      <section class="mt-10 lg:mt-8" aria-labelledby="titulo-baixar">
        <h2 id="titulo-baixar" class="secao-titulo">Relatório de participação</h2>
        <p class="mt-1.5 text-[15px] leading-snug text-abeto-2 lg:text-[14px]">Com o link pessoal de quem falta. Nenhuma resposta individual.</p>
        <div class="mt-4 grid grid-cols-2 gap-2.5 lg:mt-3">
          <button type="button" class="botao-secundario bg-white px-4 lg:min-h-12" [attr.aria-busy]="baixando() === 'pdf'" [disabled]="!!baixando()" (click)="baixar('pdf')">
            @if (baixando() === 'pdf') {
              <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto" aria-hidden="true"></span>
            } @else {
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M7 3.5h7l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19V5a1.5 1.5 0 0 1 1-1.5z" stroke-linejoin="round" /><path d="M14 3.5V8h4" stroke-linejoin="round" /></svg>
            }
            PDF
          </button>
          <button type="button" class="botao-secundario bg-white px-4 lg:min-h-12" [attr.aria-busy]="baixando() === 'xlsx'" [disabled]="!!baixando()" (click)="baixar('xlsx')">
            @if (baixando() === 'xlsx') {
              <span class="size-4 animate-spin rounded-full border-2 border-linha border-t-abeto" aria-hidden="true"></span>
            } @else {
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="2.5" /><path d="M4 9.5h16M4 14.5h16M10 9.5V20" /></svg>
            }
            Excel
          </button>
        </div>
        @if (erroArquivo(); as e) {
          <p class="aparecer mt-3 text-[15px] font-medium text-travada" role="alert">{{ e }}</p>
        }
      </section>
    </ng-template>

    <!-- A linha do tempo do líder aberto (?lider= no endereço: o "voltar" do celular fecha). -->
    @if (aberto(); as l) {
      <app-linha-do-tempo-rh
        [lider]="l"
        [empresaId]="empresaId()"
        [posicao]="posicao()"
        [temAnterior]="indiceAberto() > 0"
        [temProximo]="indiceAberto() >= 0 && indiceAberto() < ordem().length - 1"
        [whatsapp]="whatsapp(l)"
        [copiado]="copiado() === l.id"
        [gestao]="gestaoAtual()"
        (fechar)="fecharLider.emit()"
        (anterior)="vizinho(-1)"
        (proximo)="vizinho(1)"
        (copiar)="copiar(l)"
        (porEmail)="abrirEmail(l)"
        (porWhatsapp)="usouWhatsapp(l)"
        (mudou)="atualizar.emit()"
        (retirado)="retirou($event)"
      />
    }

    <!-- "Por e-mail": sempre pergunta por onde sai (plataforma ou o e-mail do RH). Abre por cima do painel do líder. -->
    @if (emailAlvo(); as alvo) {
      <app-email-rh
        [alvo]="alvo"
        [empresaId]="empresaId()"
        [empresa]="painel().empresa.nome"
        [remetente]="remetente()"
        [frasePrazo]="frasePrazo()"
        [gestao]="gestaoAtual()"
        (fechou)="emailAlvo.set(null)"
        (mudou)="atualizar.emit()"
        (corrigirEmail)="corrigirEmail($event)"
      />
    }

    <p class="sr-only" aria-live="polite">{{ aviso() }}</p>

    <!-- O que acabou de acontecer (cadastro, convites, retirada), com "Desfazer" quando dá. -->
    <div aria-live="polite">
      @if (avisoRh(); as a) {
        <div class="aviso-rh" [class.aviso-erro]="a.tipo === 'erro'" [attr.role]="a.tipo === 'erro' ? 'alert' : 'status'">
          <span class="min-w-0 flex-1">{{ a.texto }}</span>
          @if (a.acao; as acao) {
            <button type="button" class="aviso-acao" (click)="acao.fazer()">{{ acao.rotulo }}</button>
          }
          <button type="button" class="grid size-11 shrink-0 place-items-center rounded-xl text-white/80 hover:bg-white/10 hover:text-white" aria-label="Fechar o aviso" (click)="avisoRh.set(null)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" /></svg>
          </button>
        </div>
      }
    </div>
  `,
})
export class ParticipacaoRhComponente {
  readonly painel = input.required<PainelRh>();
  readonly empresaId = input.required<number>();
  readonly agora = input.required<number>();
  readonly atualizado = input('');
  readonly atualizando = input(false);

  readonly atualizar = output<void>();
  readonly verResultado = output<void>();
  /** O líder com a linha do tempo aberta (vem do endereço, ?lider=). */
  readonly liderAberto = input<number | null>(null);
  readonly abrirLider = output<number>();
  readonly trocarLider = output<number>();
  readonly fecharLider = output<void>();

  private readonly destroy = inject(DestroyRef);
  private readonly http = inject(HttpClient);
  protected readonly computador = telaDeComputador();

  /** Cargos, envio de e-mail, quem não recebeu o convite e quem foi retirado: a gestão dos líderes. */
  protected readonly gestao = httpResource<GestaoRh>(() => `${API_RH}/empresas/${this.empresaId()}/gestao`);
  protected readonly gestaoAtual = computed(() => (this.gestao.hasValue() ? this.gestao.value() : undefined));
  protected readonly retirados = computed(() => this.gestaoAtual()?.retirados ?? []);
  protected readonly devolvendo = signal<number | null>(null);
  protected readonly avisoRh = signal<AvisoRh | null>(null);
  /** O modal de e-mail aberto (um líder ou todos que faltam). */
  protected readonly emailAlvo = signal<AlvoEmail | null>(null);
  /** Quem acompanha: no "Lembrar todos" pelo próprio e-mail, vai como destinatário (líderes em cópia oculta). */
  protected readonly remetente = computed(() => this.sessao.gestor()?.email ?? '');
  private readonly sessao = inject(SessaoRh);
  private avisoTimer: ReturnType<typeof setTimeout> | undefined;
  /** O placar só fica parado se couber inteiro na janela (senão o PDF e o Excel sumiriam embaixo). */
  protected readonly placarCabe = signal(false);
  private readonly coluna = viewChild.required<ElementRef<HTMLElement>>('coluna');
  protected readonly buscaAPartirDe = BUSCA_A_PARTIR_DE;
  protected readonly busca = signal('');
  protected readonly origem = signal<'todos' | 'convidados' | 'link'>('todos');
  protected readonly copiado = signal<number | null>(null);
  protected readonly aviso = signal('');
  protected readonly baixando = signal<'pdf' | 'xlsx' | null>(null);
  protected readonly erroArquivo = signal<string | null>(null);

  constructor() {
    afterNextRender(() => {
      const coluna = this.coluna().nativeElement;
      // 96 px do cabeçalho até o topo do placar e 16 px de folga embaixo.
      const medir = () => this.placarCabe.set(coluna.offsetHeight + 112 <= window.innerHeight);
      medir();
      const observador = new ResizeObserver(medir);
      observador.observe(coluna);
      window.addEventListener('resize', medir);
      this.destroy.onDestroy(() => {
        observador.disconnect();
        window.removeEventListener('resize', medir);
      });
    });

    // A lista atualizou (a cada minuto ou depois de uma ação): a gestão vem junto.
    let anterior: PainelRh | undefined;
    effect(() => {
      const p = this.painel();
      if (anterior && anterior !== p) untracked(() => this.gestao.reload());
      anterior = p;
    });
    this.destroy.onDestroy(() => clearTimeout(this.avisoTimer));
  }

  /** O aviso no rodapé: some sozinho (com "Desfazer", fica mais tempo). */
  protected mostrar(aviso: AvisoRh) {
    clearTimeout(this.avisoTimer);
    this.avisoRh.set(aviso);
    this.avisoTimer = setTimeout(() => this.avisoRh.set(null), aviso.acao ? 9000 : 5000);
  }

  protected retirou(l: LiderRh) {
    this.fecharLider.emit();
    this.atualizar.emit();
    this.mostrar({
      tipo: 'ok',
      texto: `${primeiroNome(l.nome)} saiu da avaliação.`,
      acao: { rotulo: 'Desfazer', fazer: () => void this.devolver(l) },
    });
  }

  protected async devolver(r: { id: number; nome: string }) {
    if (this.devolvendo() !== null) return;
    this.devolvendo.set(r.id);
    try {
      await firstValueFrom(this.http.post(`${API_RH}/empresas/${this.empresaId()}/lideres/${r.id}/devolver`, null));
      this.atualizar.emit();
      this.mostrar({ tipo: 'ok', texto: `${primeiroNome(r.nome)} voltou para a lista.` });
    } catch (e) {
      this.mostrar({ tipo: 'erro', texto: problemaDe(e).detail ?? 'Não foi possível devolver agora. Tente de novo.' });
    } finally {
      this.devolvendo.set(null);
    }
  }

  protected readonly percentual = computed(() => {
    const p = this.painel().participacao;
    return p.total ? Math.round((p.responderam / p.total) * 100) : 0;
  });

  /** Convidados: vieram da planilha ou do cadastro manual (os outros começaram pelo link aberto). */
  protected readonly convidados = computed(() => {
    const p = this.painel().participacao;
    return p.total - p.peloLink;
  });

  /** O filtro por origem só aparece quando há das duas. */
  protected readonly mostrarOrigens = computed(() => this.painel().participacao.peloLink > 0 && this.convidados() > 0);

  protected readonly origens = computed(() => {
    const p = this.painel().participacao;
    return [
      { valor: 'todos' as const, texto: 'Todos', total: p.total },
      { valor: 'convidados' as const, texto: 'Convidados', total: this.convidados() },
      { valor: 'link' as const, texto: 'Pelo link da empresa', total: p.peloLink },
    ];
  });

  protected readonly faltam = computed(() => {
    const p = this.painel().participacao;
    return p.total - p.responderam;
  });

  protected readonly partes = computed(() => {
    const p = this.painel().participacao;
    return [
      { codigo: 'RESPONDEU', rotulo: 'Responderam', quantidade: p.responderam, cor: COR_GRUPO.RESPONDEU },
      { codigo: 'COMECOU', rotulo: 'Começaram', quantidade: p.comecaram, cor: COR_GRUPO.COMECOU },
      { codigo: 'ABRIU', rotulo: 'Só abriram', quantidade: p.abriram, cor: COR_GRUPO.ABRIU },
      { codigo: 'NAO_ABRIU', rotulo: 'Não abriram', quantidade: p.naoAbriram, cor: COR_GRUPO.NAO_ABRIU },
    ];
  });

  /** O prazo em linguagem de gente: "Faltam 3 dias · sexta, 28 de novembro, às 18h". */
  protected readonly prazo = computed(() => {
    const p = this.painel();
    if (!p.encerraEm) return null;
    if (p.encerrada) return { texto: `Avaliação encerrada · ${minuscula(quando(p.encerraEm))}`, encerrada: true, urgente: false };
    const fim = new Date(p.encerraEm);
    const hoje = new Date(this.agora());
    const dias = Math.round(
      (new Date(fim.getFullYear(), fim.getMonth(), fim.getDate()).getTime() -
        new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate()).getTime()) /
        86_400_000,
    );
    const hora = `${fim.getHours()}h${fim.getMinutes() ? String(fim.getMinutes()).padStart(2, '0') : ''}`;
    const texto =
      dias <= 0 ? `Encerra hoje, às ${hora}` : dias === 1 ? `Encerra amanhã, às ${hora}` : `Faltam ${dias} dias · ${prazoPorExtenso(p.encerraEm)}`;
    return { texto, encerrada: false, urgente: dias <= 1 };
  });

  protected readonly grupos = computed<GrupoRh[]>(() => {
    const termo = simples(this.busca().trim());
    const origem = this.origem();
    const grupos = this.painel().participacao.grupos;
    if (!termo && origem === 'todos') return grupos;
    return grupos
      .map((g) => ({
        ...g,
        lideres: g.lideres.filter(
          (l) =>
            (!termo || simples(l.nome).includes(termo)) &&
            (origem === 'todos' || (origem === 'link') === (l.origem === 'LINK')),
        ),
      }))
      .filter((g) => g.lideres.length > 0);
  });

  /** Os líderes na ordem em que aparecem (com a busca e o filtro de origem): as setas do painel seguem essa ordem. */
  protected readonly ordem = computed(() => this.grupos().flatMap((g) => g.lideres));
  protected readonly aberto = computed(() => {
    const id = this.liderAberto();
    return id == null ? undefined : this.painel().participacao.grupos.flatMap((g) => g.lideres).find((l) => l.id === id);
  });
  protected readonly indiceAberto = computed(() => this.ordem().findIndex((l) => l.id === this.liderAberto()));
  protected readonly posicao = computed(() => {
    const i = this.indiceAberto();
    return i < 0 ? '' : `${i + 1} de ${this.ordem().length}`;
  });

  protected vizinho(passo: number) {
    const proximo = this.ordem()[this.indiceAberto() + passo];
    if (proximo) this.trocarLider.emit(proximo.id);
  }

  protected cor(codigo: SituacaoLider): string {
    return COR_GRUPO[codigo];
  }

  /** O que aconteceu por último com o líder ("12 de 30 perguntas · último acesso ontem"). */
  protected situacao(l: LiderRh): string {
    const partes: string[] = [];
    switch (l.situacao) {
      case 'COMECOU':
        if (l.respondidas !== null && l.perguntas) partes.push(`${l.respondidas} de ${l.perguntas} perguntas`);
        if (l.ultimoAcessoEm) partes.push(`último acesso ${minuscula(quando(l.ultimoAcessoEm))}`);
        break;
      case 'ABRIU':
        if (l.primeiroAcessoEm) partes.push(`abriu ${minuscula(quando(l.primeiroAcessoEm))}${l.acessos > 1 ? ` · ${l.acessos} vezes` : ''}`);
        break;
      case 'NAO_ABRIU':
        partes.push(
          l.enviadoEm
            ? `convite enviado ${minuscula(quando(l.enviadoEm))}${l.envios > 1 ? ` · ${l.envios} envios` : ''}`
            : 'convite ainda não enviado',
        );
        break;
      case 'RESPONDEU':
        if (l.respondeuEm) partes.push(`respondeu ${minuscula(quando(l.respondeuEm))}`);
        if (l.respondendoDeNovo) partes.push(`respondendo de novo (${l.respondidas ?? 0} de ${l.perguntas ?? 0})`);
        break;
    }
    if (l.origem === 'LINK' && l.situacao === 'COMECOU') {
      partes.push('começou pelo link da empresa');
    }
    return maiuscula(partes.join(' · '));
  }

  /** A segunda linha de cada líder no celular: cargo e o que aconteceu por último. */
  protected detalhe(l: LiderRh): string {
    const situacao = this.situacao(l);
    if (!l.cargo) return situacao;
    return situacao ? `${maiuscula(l.cargo)} · ${minuscula(situacao)}` : maiuscula(l.cargo);
  }

  /** O prazo para as mensagens prontas: " O prazo vai até sexta, 28 de novembro, às 18h." (ou nada). */
  protected readonly frasePrazo = computed(() => {
    const pz = this.prazo();
    return pz && !pz.encerrada ? ` ${pz.texto.replace(/^Faltam \d+ dias · /, 'O prazo vai até ')}.` : '';
  });

  protected whatsapp(l: LiderRh): string | null {
    const numero = numeroWhatsapp(l.whatsapp);
    const texto = mensagemDoLider(l, this.painel().empresa.nome, this.frasePrazo());
    return numero ? `https://wa.me/${numero}?text=${encodeURIComponent(texto)}` : null;
  }

  /** "Por e-mail" (na lista ou no painel do líder): o modal pergunta por onde sai. */
  protected abrirEmail(l: LiderRh) {
    this.emailAlvo.set({ tipo: 'um', lider: l });
  }

  /** "Lembrar os N que faltam": o mesmo modal, para todos que ainda não responderam. */
  protected lembrarTodos() {
    const lideres = this.painel()
      .participacao.grupos.filter((g) => g.codigo !== 'RESPONDEU')
      .flatMap((g) => g.lideres);
    if (lideres.length) this.emailAlvo.set({ tipo: 'todos', lideres });
  }

  /** O e-mail voltou: fecha o modal e abre o cadastro do líder (lá, "Corrigir o e-mail"). */
  protected corrigirEmail(l: LiderRh) {
    this.emailAlvo.set(null);
    if (this.liderAberto() !== l.id) this.abrirLider.emit(l.id);
  }

  /** Abriu o WhatsApp com a mensagem: fica na linha do tempo do líder (sem confirmação de envio). */
  protected usouWhatsapp(l: LiderRh) {
    this.http
      .post(`${API_RH}/empresas/${this.empresaId()}/lideres/contato`, { ids: [l.id], canal: 'WHATSAPP' })
      .subscribe({ next: () => this.atualizar.emit(), error: () => undefined });
  }

  protected async copiar(l: LiderRh) {
    if (!l.link) return;
    try {
      await navigator.clipboard.writeText(l.link);
      this.copiado.set(l.id);
      this.aviso.set(`Link de ${primeiroNome(l.nome)} copiado.`);
      setTimeout(() => {
        if (this.copiado() === l.id) this.copiado.set(null);
      }, 2200);
    } catch {
      this.aviso.set(
        this.computador()
          ? 'Não deu para copiar. Mande o link pelo botão do e-mail ou do WhatsApp.'
          : 'Não deu para copiar. Segure o dedo no botão do WhatsApp para copiar a mensagem.',
      );
    }
  }

  protected async baixar(tipo: 'pdf' | 'xlsx') {
    this.erroArquivo.set(null);
    this.baixando.set(tipo);
    try {
      const url = `${API_RH}/empresas/${this.empresaId()}/participacao.${tipo}`;
      const arquivo = await buscarArquivo(url, `participacao.${tipo}`);
      if (tipo === 'pdf' && podeCompartilharArquivos()) {
        const r = await compartilharArquivo(arquivo, 'Relatório de participação');
        if (r === 'precisa-de-toque') salvarBlob(arquivo, arquivo.name);
      } else {
        salvarBlob(arquivo, arquivo.name);
      }
    } catch (e) {
      this.erroArquivo.set(e instanceof Error ? e.message : problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    } finally {
      this.baixando.set(null);
    }
  }
}
