import { Routes } from '@angular/router';

export const routes: Routes = [
  // Link geral (vários clientes, sem empresa).
  {
    path: '',
    pathMatch: 'full',
    title: 'Diagnóstico de carreira do líder',
    loadComponent: () => import('./diagnostico/diagnostico-pagina').then((m) => m.DiagnosticoPagina),
  },
  // Link de uma campanha específica, por exemplo vinculada a uma empresa: /c/demo
  {
    path: 'c/:codigo',
    title: 'Diagnóstico de carreira do líder',
    loadComponent: () => import('./diagnostico/diagnostico-pagina').then((m) => m.DiagnosticoPagina),
  },
  {
    path: 'resultado/:id',
    title: 'Seu diagnóstico de carreira',
    loadComponent: () => import('./resultado/resultado-pagina').then((m) => m.ResultadoPagina),
  },
  {
    path: '**',
    title: 'Página não encontrada',
    loadComponent: () => import('./nao-encontrado').then((m) => m.NaoEncontrado),
  },
];
