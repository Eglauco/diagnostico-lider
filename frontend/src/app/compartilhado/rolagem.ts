import { ViewportScroller } from '@angular/common';
import { inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, Scroll } from '@angular/router';
import { filter } from 'rxjs';

/**
 * Para navegar sem mexer na rolagem: filtros e busca que vivem na URL (?status=, ?busca=) trocam
 * só o conteúdo da mesma tela, e a pessoa continua onde estava.
 * Uso: router.navigate([], { queryParams, state: MANTER_ROLAGEM }).
 */
export const MANTER_ROLAGEM = { manterRolagem: true } as const;

/**
 * A rolagem entre navegações (no lugar do scrollPositionRestoration do roteador, que não tem
 * exceção): tela nova abre no topo, inclusive cada pergunta do questionário; voltar e avançar
 * devolvem a posição de antes; navegação com MANTER_ROLAGEM não rola. Chame uma vez, no App.
 */
export function cuidarDaRolagem() {
  const router = inject(Router);
  const rolagem = inject(ViewportScroller);
  // Só esta função decide: sem isso o navegador também restaura a posição no voltar, por conta própria.
  rolagem.setHistoryScrollRestoration('manual');
  router.events
    .pipe(
      filter((e): e is Scroll => e instanceof Scroll && e.routerEvent instanceof NavigationEnd),
      takeUntilDestroyed(),
    )
    .subscribe((e) => {
      const posicao = e.position;
      if (posicao) {
        // Voltar: a tela ainda está carregando os dados, então a posição é reaplicada quando ela cresce.
        rolagem.scrollToPosition(posicao);
        for (const espera of [150, 500]) setTimeout(() => rolagem.scrollToPosition(posicao), espera);
        return;
      }
      const estado = history.state as { manterRolagem?: boolean } | null;
      if (estado?.manterRolagem) return;
      rolagem.scrollToPosition([0, 0]);
    });
}
