import { Routes } from '@angular/router';

import { exigeLogin, soVisitante } from './acesso/sessao';
import { AdminLayout } from './admin-layout';

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
        path: '',
        title: 'Diagnósticos · Administrador',
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
        path: 'empresas/:id/editar',
        title: 'Editar empresa · Administrador',
        loadComponent: () => import('./admin-editar-empresa').then((m) => m.AdminEditarEmpresa),
      },
      {
        path: 'empresas/:id/convites',
        title: 'Líderes · Administrador',
        loadComponent: () => import('./convites/admin-convites').then((m) => m.AdminConvites),
      },
      {
        path: 'empresas/:id',
        title: 'Empresa · Administrador',
        loadComponent: () => import('./admin-empresa').then((m) => m.AdminEmpresa),
      },
      {
        path: 'usuarios',
        title: 'Usuários · Administrador',
        loadComponent: () => import('./acesso/admin-usuarios').then((m) => m.AdminUsuarios),
      },
    ],
  },
];
