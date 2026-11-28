import { Component, computed, input, signal } from '@angular/core';

import { problemaDe } from '../api/diagnostico-api';
import { Curva, PontoGrupo } from '../compartilhado/curva';
import {
  buscarArquivo,
  compartilharArquivo,
  podeCompartilharArquivos,
  salvarBlob,
} from '../compartilhado/download';
import { estiloFaixa, FAIXAS } from '../compartilhado/faixas';
import { quando } from '../compartilhado/formatos';
import { telaDeComputador } from '../compartilhado/util';
import { API_RH, GrupoCargoRh, ParteFaixa, ResultadoRh } from './rh-api';

const minuscula = (texto: string) => texto.charAt(0).toLowerCase() + texto.slice(1);

/**
 * O resultado do grupo para o RH. Sempre a foto anônima do servidor: abre com 3 respostas e só
 * muda quando chegam mais 3, para ninguém deduzir a resposta de quem acabou de terminar. Cargos e
 * perguntas entram no resultado final.
 */
@Component({
  selector: 'app-resultado-rh',
  imports: [Curva],
  host: { class: 'block' },
  styleUrl: './acompanhar.css',
  template: `
    @if (erro(); as e) {
      <p class="mt-8 rounded-2xl bg-[#FDEDEF] px-4 py-4 text-[15px] text-travada" role="alert">{{ e }}</p>
    } @else if (carregando() || !resultado()) {
      <div class="grid min-h-[50dvh] place-items-center" aria-busy="true">
        <span class="size-8 animate-spin rounded-full border-[3px] border-linha border-t-abeto" aria-hidden="true"></span>
        <span class="sr-only">Carregando o resultado…</span>
      </div>
    } @else if (resultado(); as r) {
      <h1 class="sr-only">Resultado do grupo</h1>

      @if (r.estado === 'AGUARDANDO' || !r.dados) {
        <!-- Ainda fechado: menos de 3 respostas. -->
        <section class="surgir cartao-rh mt-4 overflow-hidden lg:mx-auto lg:mt-6 lg:max-w-2xl" style="--i: 0" aria-labelledby="titulo-fechado">
          <div class="relative px-3 pt-8 opacity-35 grayscale" aria-hidden="true">
            <app-curva modo="grupo" [pontos]="[]" />
          </div>
          <div class="-mt-24 px-6 pb-7 text-center">
            <span class="relative mx-auto grid size-16 place-items-center rounded-full bg-abeto text-white shadow-[0_14px_30px_-16px_rgb(15_43_38/0.8)]" aria-hidden="true">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
            </span>
            <h2 id="titulo-fechado" class="display mt-5 text-[30px] leading-[1.05] font-bold">O resultado do grupo abre com {{ r.minimo }} respostas</h2>
            <div class="mx-auto mt-6 flex max-w-[220px] items-center justify-center gap-2" role="img" [attr.aria-label]="r.concluidos + ' de ' + r.minimo + ' respostas'">
              @for (i of casas(r.minimo); track i) {
                <span class="h-2.5 flex-1 rounded-full transition-colors duration-500" [class]="i < r.concluidos ? 'bg-ascensao' : 'bg-linha'"></span>
              }
            </div>
            <p class="mt-3 text-[16px] font-semibold">
              {{ r.faltamParaAbrir === 1 ? 'Falta 1 resposta' : 'Faltam ' + r.faltamParaAbrir + ' respostas' }}
            </p>
            <p class="mx-auto mt-3 max-w-sm text-[15px] leading-snug text-abeto-2">
              Com menos de {{ r.minimo }}, os números do grupo seriam o resultado de uma pessoa. Assim que o {{ r.minimo }}º líder terminar, ele aparece aqui.
            </p>
          </div>
        </section>
      } @else {
        @let d = r.dados;

        <!-- Situação da foto: parcial (muda a cada 3) ou final. -->
        <section class="surgir mt-4" style="--i: 0" aria-label="Situação do resultado">
          <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
            <span class="inline-flex min-h-8 items-center gap-1.5 rounded-full px-3 text-[13.5px] font-bold"
                  [class]="r.estado === 'FINAL' ? 'bg-ascensao-suave text-[#17714A]' : 'bg-plato-suave text-[#855300]'">
              <i class="block size-2 rounded-full bg-current" aria-hidden="true"></i>
              {{ r.estado === 'FINAL' ? 'Resultado final' : 'Resultado parcial' }}
            </span>
            <span class="text-[14px] text-abeto-2">
              {{ r.respostas }} {{ r.respostas === 1 ? 'resposta' : 'respostas' }} · atualizado {{ minuscula(quando(r.atualizadoEm)) }}
            </span>
            @if (computador()) {
              <!-- No computador, o PDF fica à mão no topo (e de novo no fim). -->
              <button type="button" class="botao-primario ml-auto min-h-12 px-6 text-[15px]" [attr.aria-busy]="baixando()" [disabled]="baixando()" (click)="baixar('topo')">
                @if (baixando()) {
                  <span class="size-4 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
                  Gerando o PDF…
                } @else {
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 19.5h14" stroke-linecap="round" stroke-linejoin="round" /></svg>
                  Baixar o resultado em PDF
                }
              </button>
            }
          </div>
          @if (erroArquivo() && baixouDe() === 'topo') {
            <p class="aparecer mt-3 text-right text-[15px] font-medium text-travada" role="alert">{{ erroArquivo() }}</p>
          }

          @if (r.estado === 'PARCIAL') {
            <details class="dobra cartao-rh mt-4 px-4">
              <summary class="flex min-h-15 cursor-pointer list-none items-center gap-3">
                <span class="flex shrink-0 gap-1" aria-hidden="true">
                  @for (i of casas(r.minimo); track i) {
                    <span class="h-6 w-1.5 rounded-full" [class]="i < r.novas ? 'bg-acao' : 'bg-linha'"></span>
                  }
                </span>
                <span class="min-w-0 flex-1 text-[15px] leading-snug">
                  <strong class="font-semibold">Muda com mais {{ r.faltamParaAtualizar }} {{ r.faltamParaAtualizar === 1 ? 'resposta' : 'respostas' }}</strong>
                  <span class="block text-[13.5px] text-abeto-2">Por que não muda a cada resposta?</span>
                </span>
                <svg class="seta shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </summary>
              <div class="max-w-3xl space-y-2 border-t border-linha/70 py-4 text-[15px] leading-snug text-abeto-2">
                <p>Você vê quem acabou de responder. Se a média mudasse a cada resposta, uma conta mostraria o índice dessa pessoa.</p>
                <p>Por isso o resultado só muda quando chegam {{ r.minimo }} respostas novas de uma vez. Cargos e perguntas aparecem no resultado final, quando a avaliação terminar.</p>
              </div>
            </details>
          } @else if (d.nota) {
            <p class="mt-4 flex items-start gap-3 rounded-2xl bg-white/70 px-4 py-3.5 text-[14.5px] leading-snug text-abeto-2 ring-1 ring-linha/70">
              <svg class="mt-0.5 shrink-0" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8v.01" stroke-linecap="round" /></svg>
              {{ d.nota }}
            </p>
          }
        </section>

        <!-- O índice e a curva: um embaixo do outro no celular, lado a lado no computador. -->
        <div class="lg:mt-6 lg:grid lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-6">
          <!-- O índice médio e a faixa do grupo. -->
          <section class="surgir cartao-lg mt-8 lg:mt-0 lg:flex lg:flex-col" style="--i: 1" aria-labelledby="titulo-indice">
            <h2 id="titulo-indice" class="secao-titulo">Índice médio do grupo</h2>
            <div class="mt-2 flex items-end justify-between gap-4 lg:mt-5 lg:flex-col lg:items-start lg:gap-5">
              <p class="display text-[84px] leading-[0.8] font-bold tabular-nums lg:text-[104px]" [attr.aria-label]="d.indice.media + ' de 100'">
                {{ d.indice.media }}<span class="ml-0.5 text-[22px] font-medium tracking-normal text-abeto-3 lg:text-[26px]">/100</span>
              </p>
              <span class="mb-1 inline-flex min-h-9 items-center gap-2 rounded-full px-3.5 text-[14px] font-bold"
                    [style.background]="faixa(d.faixaDaMedia.codigo)?.suave" [style.color]="faixa(d.faixaDaMedia.codigo)?.texto">
                <i class="block size-2.5 rounded-full" [style.background]="faixa(d.faixaDaMedia.codigo)?.cor" aria-hidden="true"></i>
                {{ d.faixaDaMedia.titulo }}
              </span>
            </div>
            <p class="mt-4 text-[15px] leading-snug text-abeto-2 lg:mt-auto lg:border-t lg:border-linha/70 lg:pt-5">
              Mediana {{ d.indice.mediana }} · do menor ao maior, de {{ d.indice.menor }} a {{ d.indice.maior }}.
              @if (d.referencia; as ref) {
                <br /><strong class="font-semibold text-abeto">{{ comparacao(d.indice.media, ref.media) }}</strong> da média geral, que é {{ ref.media }} ({{ ref.lideres }} líderes de todas as empresas).
              }
            </p>
          </section>

          <!-- A curva: cada ponto é um líder, sem nome. -->
          <figure class="surgir cartao-rh mt-6 px-2 pt-10 pb-3 lg:mt-0 lg:flex lg:flex-col lg:justify-end lg:px-4 lg:pt-12" style="--i: 2">
            <app-curva modo="grupo" [pontos]="pontos()" [largura]="computador() ? 500 : 360" />
            <figcaption class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-3 pt-2 text-[13px] text-abeto-2">
              <span>Cada ponto é um líder, sem identificação.</span>
              <span class="flex gap-3">
                @for (f of ordem; track f) {
                  <span class="inline-flex items-center gap-1.5"><i class="block size-2 rounded-full" [style.background]="faixa(f)?.cor" aria-hidden="true"></i>{{ faixa(f)?.rotulo }}</span>
                }
              </span>
            </figcaption>
          </figure>
        </div>

        <!-- Faixas. -->
        <section class="mt-10 lg:mt-12" aria-labelledby="titulo-faixas">
          <h2 id="titulo-faixas" class="secao-titulo">Como o grupo se distribui</h2>
          <div class="mt-4 flex h-3 gap-[3px] overflow-hidden rounded-full" aria-hidden="true">
            @for (f of d.faixas; track f.codigo) {
              @if (f.quantidade) {
                <span class="h-full" [style.width.%]="f.percentual" [style.background]="faixa(f.codigo)?.cor"></span>
              }
            }
          </div>
          <ul class="mt-4 space-y-3 lg:grid lg:grid-cols-3 lg:gap-4 lg:space-y-0">
            @for (f of d.faixas; track f.codigo) {
              <li class="cartao-rh p-4">
                <div class="flex items-center gap-3">
                  <i class="block size-3 shrink-0 rounded-full" [style.background]="faixa(f.codigo)?.cor" aria-hidden="true"></i>
                  <span class="flex-1 text-[16px] font-semibold">{{ f.titulo }}</span>
                  <span class="display text-[26px] leading-none font-bold tabular-nums">{{ f.percentual }}%</span>
                </div>
                <p class="mt-1 pl-6 text-[13.5px] text-abeto-2">
                  {{ f.quantidade }} {{ f.quantidade === 1 ? 'líder' : 'líderes' }} · índice de {{ f.minimo }} a {{ f.maximo }}
                </p>
                @if (f.leitura) {
                  <p class="mt-2 pl-6 text-[14.5px] leading-snug text-abeto-2">{{ f.leitura }}</p>
                }
              </li>
            }
          </ul>
        </section>

        <!-- O que se destaca. -->
        @if (d.destaques.length) {
          <section class="mt-10" aria-labelledby="titulo-destaques">
            <h2 id="titulo-destaques" class="secao-titulo">O que se destaca</h2>
            <!-- Em duas colunas; se sobrar um, ele ocupa a linha toda. -->
            <ul class="mt-4 space-y-3 lg:grid lg:grid-cols-2 lg:gap-4 lg:space-y-0 lg:[&>li:last-child:nth-child(odd)]:col-span-2">
              @for (x of d.destaques; track x.titulo) {
                <li class="cartao-rh flex gap-3.5 p-4">
                  <i class="mt-1.5 block size-2.5 shrink-0 rounded-full" [style.background]="faixa(x.codigo)?.cor ?? 'var(--color-abeto-3)'" aria-hidden="true"></i>
                  <div>
                    <p class="text-[16px] leading-snug font-semibold">{{ x.titulo }}</p>
                    <p class="mt-1 text-[14.5px] leading-snug text-abeto-2">{{ x.texto }}</p>
                  </div>
                </li>
              }
            </ul>
          </section>
        }

        <!-- Dimensões. -->
        @if (d.dimensoes.length) {
          <section class="mt-10" aria-labelledby="titulo-dimensoes">
            <h2 id="titulo-dimensoes" class="secao-titulo">Por dimensão</h2>
            <!-- Três dimensões (ou seis): três colunas. Outro número: duas, e a que sobrar ocupa a linha. -->
            <ul class="mt-4 space-y-3 lg:grid lg:gap-4 lg:space-y-0"
                [class]="d.dimensoes.length % 3 === 0 ? 'lg:grid-cols-3' : 'lg:grid-cols-2 lg:[&>li:last-child:nth-child(odd)]:col-span-2'">
              @for (dim of d.dimensoes; track dim.codigo) {
                <li class="cartao-rh p-4">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="text-[17px] font-semibold">{{ dim.nome }}</p>
                      @if (dim.destaque) {
                        <span class="mt-1 inline-flex min-h-6 items-center rounded-full px-2.5 text-[12.5px] font-bold"
                              [class]="dim.destaque === 'forte' ? 'bg-ascensao-suave text-[#17714A]' : 'bg-travada-suave text-[#A52B40]'">
                          {{ dim.destaque === 'forte' ? 'A mais forte' : 'Pede atenção' }}
                        </span>
                      }
                    </div>
                    <p class="display shrink-0 text-[36px] leading-none font-bold tabular-nums" [attr.aria-label]="dim.media + ' de 100'">{{ dim.media }}</p>
                  </div>
                  <div class="relative mt-4 h-2.5 rounded-full bg-nevoa" aria-hidden="true">
                    <span class="absolute inset-y-0 left-0 rounded-full" [style.width.%]="dim.media" [style.background]="corDaNota(dim.media, d.limites)"></span>
                    @if (dim.referencia !== null) {
                      <span class="absolute -top-1.5 h-5.5 w-0.5 rounded-full bg-abeto" [style.left.%]="dim.referencia" [title]="'Média geral: ' + dim.referencia"></span>
                    }
                  </div>
                  <p class="mt-2.5 text-[13.5px] leading-snug text-abeto-2">
                    {{ dim.emAlerta }}% abaixo de {{ d.limites.alerta }} · {{ dim.fortes }}% com {{ d.limites.forte }} ou mais
                    @if (dim.referencia !== null) {
                      · média geral {{ dim.referencia }}
                    }
                  </p>
                  @if (dim.acao && dim.destaque === 'fragil') {
                    <p class="mt-3 rounded-2xl bg-nevoa-2 px-3.5 py-3 text-[14.5px] leading-snug">{{ dim.acao }}</p>
                  }
                </li>
              }
            </ul>
          </section>
        }

        @if (r.estado === 'FINAL') {
          <!-- Por cargo (só no final). -->
          @if (d.porCargo; as pc) {
            <section class="mt-10" aria-labelledby="titulo-cargos">
              <h2 id="titulo-cargos" class="secao-titulo">Em que nível a carreira trava</h2>
              <p class="mt-1.5 text-[15px] leading-snug text-abeto-2">Do cargo mais alto para o primeiro nível. Um cargo só aparece sozinho com 3 líderes ou mais.</p>
              <!-- A escada à esquerda e o tempo no cargo à direita, no computador. -->
              <div class="lg:mt-4 lg:grid lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:items-start lg:gap-6">
                <ul class="mt-4 space-y-3 lg:mt-0">
                  @for (g of escada(pc.grupos); track g.nome) {
                    <li class="cartao-rh p-4" [class.cartao-travado]="pc.maisTravado?.nome === g.nome">
                      <div class="flex items-start justify-between gap-3">
                        <div class="min-w-0">
                          <p class="text-[16px] font-semibold">{{ g.nome }}</p>
                          <p class="text-[13.5px] text-abeto-2">{{ g.lideres }} {{ g.lideres === 1 ? 'líder' : 'líderes' }}{{ pc.maisTravado?.nome === g.nome ? ' · o mais travado' : '' }}</p>
                        </div>
                        <p class="display shrink-0 text-[30px] leading-none font-bold tabular-nums">{{ g.media }}</p>
                      </div>
                      <div class="mt-3 flex h-2.5 gap-[2px] overflow-hidden rounded-full" aria-hidden="true">
                        @for (parte of g.faixas; track parte.codigo) {
                          @if (parte.quantidade) {
                            <span class="h-full" [style.width.%]="parte.percentual" [style.background]="faixa(parte.codigo)?.cor"></span>
                          }
                        }
                      </div>
                      <p class="mt-2 text-[13px] text-abeto-2">{{ resumoFaixas(g.faixas) }}</p>
                    </li>
                  }
                </ul>
                @if (pc.tempos.length) {
                  <div>
                    <h3 class="mt-7 text-[16px] font-semibold lg:mt-0">Tempo no cargo</h3>
                    <ul class="mt-3 divide-y divide-linha/70 rounded-[20px] bg-white px-4 ring-1 ring-linha/70">
                      @for (t of pc.tempos; track t.rotulo) {
                        <li class="flex min-h-14 items-center justify-between gap-3 py-2.5">
                          <span class="text-[15px]">{{ t.rotulo }}</span>
                          @if (t.oculto) {
                            <span class="text-right text-[13px] text-abeto-2">Menos de 3 líderes: sem números</span>
                          } @else {
                            <span class="text-right text-[14px]"><strong class="font-bold tabular-nums">{{ t.media }}</strong> <span class="text-abeto-2">· {{ t.lideres }} líderes</span></span>
                          }
                        </li>
                      }
                    </ul>
                  </div>
                }
              </div>
              @if (pc.notaAnonimato) {
                <p class="mt-3 text-[13.5px] leading-snug text-abeto-2">{{ pc.notaAnonimato }}</p>
              }
              @if (pc.notaSemCargo) {
                <p class="mt-2 text-[13.5px] leading-snug text-abeto-2">{{ pc.notaSemCargo }}</p>
              }
            </section>
          }

          <!-- Cada pergunta (só no final). -->
          @if (d.perguntas.length) {
            <section class="mt-10" aria-labelledby="titulo-perguntas">
              <h2 id="titulo-perguntas" class="secao-titulo">O que os líderes responderam</h2>
              <p class="mt-1.5 text-[15px] leading-snug text-abeto-2">Quantos escolheram cada alternativa, sem nomes.</p>
              <div class="mt-4 space-y-3 lg:grid lg:grid-cols-2 lg:items-start lg:gap-4 lg:space-y-0">
                @for (q of d.perguntas; track q.numero) {
                  <!-- Abertas de início: a primeira (no computador, a primeira de cada coluna). -->
                  <details class="dobra cartao-rh px-4 lg:px-5" [open]="$index < (computador() ? 2 : 1)">
                    <summary class="flex min-h-15 cursor-pointer list-none items-start gap-3 py-3.5">
                      <span class="display grid size-8 shrink-0 place-items-center rounded-full bg-nevoa text-[15px] font-bold">{{ q.numero }}</span>
                      <span class="min-w-0 flex-1">
                        @if (q.dimensao) {
                          <span class="block text-[12.5px] font-semibold text-abeto-2">{{ q.dimensao }}</span>
                        }
                        <span class="block text-[15.5px] leading-snug font-semibold">{{ q.enunciado }}</span>
                      </span>
                      <svg class="seta mt-1.5 shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                    </summary>
                    <ul class="space-y-3 border-t border-linha/70 pt-3.5 pb-4">
                      @for (o of q.opcoes; track o.letra) {
                        <li>
                          <div class="flex items-start justify-between gap-3 text-[14.5px] leading-snug">
                            <span [class.font-semibold]="o.maisEscolhida">{{ o.texto }}</span>
                            <span class="shrink-0 font-bold tabular-nums">{{ o.percentual }}%</span>
                          </div>
                          <div class="mt-1.5 h-2 rounded-full bg-nevoa" aria-hidden="true">
                            <span class="block h-full rounded-full" [style.width.%]="o.percentual" [class]="o.maisEscolhida ? 'bg-acao' : 'bg-abeto-3'"></span>
                          </div>
                        </li>
                      }
                    </ul>
                  </details>
                }
              </div>
            </section>
          }
        } @else {
          <section class="mt-10 flex items-start gap-3.5 rounded-[20px] border-[1.5px] border-dashed border-linha px-4 py-4" aria-label="Resultado final">
            <svg class="mt-0.5 shrink-0 text-abeto-2" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" /></svg>
            <p class="text-[15px] leading-snug text-abeto-2">
              <strong class="font-semibold text-abeto">Cargos e perguntas entram no resultado final.</strong>
              Um cargo pode ganhar uma pessoa só entre duas atualizações, então essas partes esperam a avaliação terminar{{ encerraEm() ? ' (' + minuscula(quando(encerraEm())) + ')' : '' }}.
            </p>
          </section>
        }

        <!-- O que fazer com cada grupo. -->
        @if (temAcoes()) {
          <section class="mt-10" aria-labelledby="titulo-acoes">
            <h2 id="titulo-acoes" class="secao-titulo">O que a empresa pode fazer</h2>
            <div class="mt-4 space-y-3 lg:grid lg:grid-cols-3 lg:items-start lg:gap-4 lg:space-y-0">
              @for (f of d.faixas; track f.codigo) {
                @if (f.acoes.length) {
                  <!-- No computador, as três abertas lado a lado; no celular, só a faixa do grupo. -->
                  <details class="dobra cartao-rh px-4 lg:px-5" [open]="computador() || f.codigo === d.faixaDaMedia.codigo">
                    <summary class="flex min-h-15 cursor-pointer list-none items-center gap-3">
                      <i class="block size-3 shrink-0 rounded-full" [style.background]="faixa(f.codigo)?.cor" aria-hidden="true"></i>
                      <span class="flex-1 text-[16px] font-semibold">{{ f.titulo }}</span>
                      <span class="text-[14px] text-abeto-2 tabular-nums">{{ f.quantidade }}</span>
                      <svg class="seta shrink-0 text-abeto-3" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" /></svg>
                    </summary>
                    <ol class="space-y-2.5 border-t border-linha/70 pt-3.5 pb-4">
                      @for (a of f.acoes; track $index) {
                        <li class="flex gap-3 text-[15px] leading-snug">
                          <span class="display grid size-6 shrink-0 place-items-center rounded-full bg-nevoa text-[13px] font-bold">{{ $index + 1 }}</span>
                          <span>{{ a }}</span>
                        </li>
                      }
                    </ol>
                  </details>
                }
              }
            </div>
          </section>
        }

        <!-- O PDF para levar à reunião. -->
        <section class="mt-10 lg:mt-12 lg:flex lg:flex-row-reverse lg:items-center lg:justify-between lg:gap-8">
          <button type="button" class="botao-primario w-full lg:w-auto lg:shrink-0" [attr.aria-busy]="baixando()" [disabled]="baixando()" (click)="baixar('fim')">
            @if (baixando()) {
              <span class="size-5 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
              Gerando o PDF…
            } @else {
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 19.5h14" stroke-linecap="round" stroke-linejoin="round" /></svg>
              {{ compartilha ? 'Compartilhar o resultado em PDF' : 'Baixar o resultado em PDF' }}
            }
          </button>
          <div class="min-w-0 lg:max-w-3xl">
            @if (erroArquivo() && baixouDe() === 'fim') {
              <p class="aparecer mt-3 text-[15px] font-medium text-travada lg:mt-0 lg:mb-2" role="alert">{{ erroArquivo() }}</p>
            }
            <p class="mt-4 text-[13.5px] leading-snug text-abeto-2 lg:mt-0">
              Base: {{ r.respostas }} diagnósticos concluídos {{ d.periodo }}. {{ d.questionario }}.
              @if (d.notaVersoes) {
                {{ d.notaVersoes }}
              }
            </p>
          </div>
        </section>
      }
    }
  `,
})
export class ResultadoRhComponente {
  readonly resultado = input<ResultadoRh | undefined>();
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly empresaId = input.required<number>();
  readonly empresa = input('');
  readonly encerraEm = input<string | null>(null);
  readonly agora = input(0);

  protected readonly ordem = ['TRAVADA', 'PLATO', 'ASCENSAO'];
  protected readonly quando = quando;
  protected readonly minuscula = minuscula;
  protected readonly compartilha = podeCompartilharArquivos();

  protected readonly computador = telaDeComputador();
  protected readonly baixando = signal(false);
  protected readonly erroArquivo = signal<string | null>(null);
  /** De qual botão veio o pedido do PDF (no computador há um no topo e outro no fim): o erro aparece ao lado dele. */
  protected readonly baixouDe = signal<'topo' | 'fim'>('fim');

  protected readonly pontos = computed<PontoGrupo[]>(() =>
    (this.resultado()?.dados?.pontos ?? []).map((p) => ({ indice: p.indice, cor: estiloFaixa(p.faixa)?.cor ?? FAIXAS['PLATO'].cor })),
  );

  protected readonly temAcoes = computed(() => (this.resultado()?.dados?.faixas ?? []).some((f) => f.acoes.length > 0));

  protected faixa(codigo: string | null | undefined) {
    return estiloFaixa(codigo);
  }

  /** "4 pontos acima", "1 ponto abaixo" ou "Na média". */
  protected comparacao(media: number, geral: number): string {
    const diferenca = media - geral;
    if (diferenca === 0) return 'Na média';
    const pontos = Math.abs(diferenca);
    return `${pontos} ${pontos === 1 ? 'ponto' : 'pontos'} ${diferenca > 0 ? 'acima' : 'abaixo'}`;
  }

  protected casas(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
  }

  /** A escada: o cargo mais alto em cima; "Outros cargos" por último. */
  protected escada(grupos: GrupoCargoRh[]): GrupoCargoRh[] {
    return [...grupos.filter((g) => !g.outros).reverse(), ...grupos.filter((g) => g.outros)];
  }

  protected resumoFaixas(partes: ParteFaixa[]): string {
    return partes
      .filter((p) => p.quantidade > 0)
      .map((p) => `${p.rotulo} ${p.percentual}%`)
      .join(' · ');
  }

  protected corDaNota(nota: number, limites: { alerta: number; forte: number }): string {
    return nota < limites.alerta ? FAIXAS['TRAVADA'].cor : nota < limites.forte ? FAIXAS['PLATO'].cor : FAIXAS['ASCENSAO'].cor;
  }

  protected async baixar(de: 'topo' | 'fim') {
    this.baixouDe.set(de);
    this.erroArquivo.set(null);
    this.baixando.set(true);
    try {
      const arquivo = await buscarArquivo(`${API_RH}/empresas/${this.empresaId()}/resultado.pdf`, 'resultado.pdf');
      if (this.compartilha) {
        const r = await compartilharArquivo(arquivo, `Resultado do grupo · ${this.empresa()}`);
        if (r === 'precisa-de-toque') salvarBlob(arquivo, arquivo.name);
      } else {
        salvarBlob(arquivo, arquivo.name);
      }
    } catch (e) {
      this.erroArquivo.set(e instanceof Error ? e.message : problemaDe(e).detail ?? 'Algo deu errado. Tente de novo.');
    } finally {
      this.baixando.set(false);
    }
  }
}

