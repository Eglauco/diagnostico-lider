import { Signal, computed, effect, linkedSignal, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';

import { TemaMarca } from '../../compartilhado/tema-marca';
import { API_ADMIN, PreviaTema } from '../admin-modelos';

/** A identidade do Diagnóstico do Líder (mesmos valores de PaletaMarca.PADRAO). */
export const TEMA_PADRAO: TemaMarca = {
  acao: '#0F2B26',
  acaoForte: '#17403A',
  sobreAcao: '#FFFFFF',
  tinta: '#0F2B26',
  tinta2: '#4A635C',
  tinta3: '#7D918B',
  fundo: '#EDF2EF',
  fundo2: '#F5F8F6',
  linha: '#D2DDD8',
  destaque: '#FF5B37',
  destaqueTexto: '#C23A1C',
  sobreDestaque: '#0F2B26',
  destaqueSobreTinta: '#FF5B37',
  tintaBrilho: '#2E6B5F',
};

const HEX = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i;

/** "0057b8" ou "#05b" → "#0057B8"/"#0055BB"; inválida → null. Mesma regra de PaletaMarca.normalizar. */
export function normalizarHex(cor: string | null | undefined): string | null {
  const m = HEX.exec((cor ?? '').trim());
  if (!m) return null;
  const hex = m[1].length === 3 ? [...m[1]].map((c) => c + c).join('') : m[1];
  return `#${hex.toUpperCase()}`;
}

const CONECTIVOS = new Set(['de', 'da', 'do', 'das', 'dos', 'e', 'a', 'o', 'the', 'of', 'and', 's/a', 'sa', 'ltda', 'me', 'eireli']);

/** Iniciais do monograma, como em MarcaEmpresa.siglaDe: "Construtora Horizonte" → CH. */
export function siglaDe(nome: string): string {
  const palavras = nome
    .trim()
    .split(/[\s&.,/-]+/)
    .filter((p) => p && /^[\p{L}\p{N}]/u.test(p) && !CONECTIVOS.has(p.toLowerCase()));
  if (palavras.length === 0) return '?';
  const sigla = palavras.length === 1 ? palavras[0].slice(0, 2) : palavras[0][0] + palavras[1][0];
  return sigla.toUpperCase();
}

/**
 * Prévia da paleta enquanto o administrador escolhe as cores. Quem calcula é o backend (o mesmo
 * cálculo dos PDFs e do questionário), com uma pequena espera para não consultar a cada movimento
 * do seletor. Mantém a última prévia na tela enquanto a próxima chega.
 */
export function usarPreviaDeCores(primaria: Signal<string>, secundaria: Signal<string>) {
  const pedido = signal({ primaria: '', secundaria: '' });
  effect((onCleanup) => {
    const p = normalizarHex(primaria()) ?? '';
    const s = p ? (normalizarHex(secundaria()) ?? '') : '';
    const espera = setTimeout(() => pedido.set({ primaria: p, secundaria: s }), 140);
    onCleanup(() => clearTimeout(espera));
  });
  const recurso = httpResource<PreviaTema>(() => {
    const { primaria: p, secundaria: s } = pedido();
    const params: Record<string, string> = s ? { primaria: p, secundaria: s } : { primaria: p };
    return p ? { url: `${API_ADMIN}/empresas/tema`, params } : undefined;
  });
  const ultima = linkedSignal<PreviaTema | undefined, PreviaTema | null>({
    source: () => (recurso.hasValue() ? recurso.value() : undefined),
    computation: (nova, anterior) => nova ?? anterior?.value ?? null,
  });
  const semCor = computed(() => !pedido().primaria);
  return {
    tema: computed<TemaMarca>(() => (semCor() ? TEMA_PADRAO : (ultima()?.tema ?? TEMA_PADRAO))),
    ajustes: computed<string[]>(() => (semCor() ? [] : (ultima()?.ajustes ?? []))),
    carregando: computed(() => recurso.isLoading()),
  };
}
