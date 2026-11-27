import { Routes } from '@angular/router';

export const ROTAS_ACOMPANHAR: Routes = [
  {
    path: '',
    title: 'Acompanhamento da avaliação',
    loadComponent: () => import('./acompanhar-pagina').then((m) => m.AcompanharPagina),
  },
  // O botão do e-mail: /acompanhar/entrar#token.
  {
    path: 'entrar',
    title: 'Entrando · Acompanhamento da avaliação',
    loadComponent: () => import('./entrar-pelo-link').then((m) => m.EntrarPeloLink),
  },
  {
    path: ':empresa',
    title: 'Acompanhamento da avaliação',
    loadComponent: () => import('./painel-rh').then((m) => m.PainelRhPagina),
  },
];
