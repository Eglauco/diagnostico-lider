import { Routes } from '@angular/router';

import { exigeLogin, soVisitante } from './acesso/sessao';
import { AdminLayout } from './admin-layout';
import { EmpresaLayout } from './empresa/empresa-layout';

export const ROTAS_ADMIN: Routes = [
  {
    path: 'entrar',
    title: 'Entrar · Administrador',
    canActivate: [soVisitante],
    loadComponent: () => import('./acesso/admin-entrar').then((m) => m.AdminEntrar),
  },
  {
    path: '',
    component: AdminLayout,
    canActivate: [exigeLogin],
    children: [
      {
        // A casa do painel é a carteira de empresas. Um link antigo da lista com ?campanha= vai para
        // o Diagnóstico Geral, que manda o link de empresa para a aba Diagnóstico dela.
        path: '',
        pathMatch: 'full',
        redirectTo: ({ queryParams }) => (queryParams['campanha'] ? 'geral' : 'empresas'),
      },
      {
        path: 'geral',
        title: 'Diagnóstico Geral · Administrador',
        data: { modo: 'geral' },
        loadComponent: () => import('./admin-lista').then((m) => m.AdminLista),
      },
      {
        path: 'diagnosticos/:id',
        title: 'Diagnóstico · Administrador',
        loadComponent: () => import('./admin-detalhe').then((m) => m.AdminDetalhe),
      },
      {
        path: 'empresas',
        title: 'Empresas · Administrador',
        loadComponent: () => import('./admin-empresas').then((m) => m.AdminEmpresas),
      },
      {
        path: 'empresas/nova',
        title: 'Cadastrar empresa · Administrador',
        loadComponent: () => import('./admin-nova-empresa').then((m) => m.AdminNovaEmpresa),
      },
      {
        // Tudo da empresa num lugar só: o cabeçalho fica, as abas trocam embaixo.
        path: 'empresas/:id',
        component: EmpresaLayout,
        children: [
          {
            path: '',
            title: 'Empresa · Administrador',
            loadComponent: () => import('./admin-empresa').then((m) => m.AdminEmpresa),
          },
          {
            path: 'diagnostico',
            title: 'Diagnóstico da empresa · Administrador',
            data: { modo: 'empresa' },
            loadComponent: () => import('./admin-lista').then((m) => m.AdminLista),
          },
          {
            path: 'lideres',
            title: 'Líderes · Administrador',
            loadComponent: () => import('./convites/admin-convites').then((m) => m.AdminConvites),
          },
          // Endereço antigo da página de líderes.
          { path: 'convites', redirectTo: 'lideres' },
          {
            path: 'editar',
            title: 'Marca e cargos · Administrador',
            loadComponent: () => import('./admin-editar-empresa').then((m) => m.AdminEditarEmpresa),
          },
        ],
      },
      {
        path: 'usuarios',
        title: 'Usuários · Administrador',
        loadComponent: () => import('./acesso/admin-usuarios').then((m) => m.AdminUsuarios),
      },
    ],
  },
];
