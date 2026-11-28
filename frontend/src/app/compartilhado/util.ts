import { DestroyRef, inject, signal, Signal } from '@angular/core';

/** localStorage que nunca quebra a tela (modo privado, armazenamento bloqueado etc.). */
export const armazenamento = {
  ler<T>(chave: string): T | null {
    try {
      const valor = localStorage.getItem(chave);
      return valor ? (JSON.parse(valor) as T) : null;
    } catch {
      return null;
    }
  },
  gravar(chave: string, valor: unknown): void {
    try {
      localStorage.setItem(chave, JSON.stringify(valor));
    } catch {
      /* sem armazenamento: o diagnóstico segue funcionando, só não retoma */
    }
  },
  remover(chave: string): void {
    try {
      localStorage.removeItem(chave);
    } catch {
      /* idem */
    }
  },
};

export const esperar = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Vibração curta de confirmação no Android; ignorada onde não existe. */
export function vibrar(ms = 10): void {
  try {
    navigator.vibrate?.(ms);
  } catch {
    /* sem suporte */
  }
}

export const semMovimento = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * Tela de computador (a partir de 1024 px, o `lg:` do Tailwind), acompanhando a janela quando ela
 * muda de tamanho. Chame no construtor (precisa do contexto de injeção).
 */
export function telaDeComputador(): Signal<boolean> {
  const consulta = matchMedia('(min-width: 64rem)');
  const larga = signal(consulta.matches);
  const mudou = (e: MediaQueryListEvent) => larga.set(e.matches);
  consulta.addEventListener('change', mudou);
  inject(DestroyRef).onDestroy(() => consulta.removeEventListener('change', mudou));
  return larga.asReadonly();
}
