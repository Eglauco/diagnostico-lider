import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';

import { sessaoExpiradaInterceptor } from './admin/acesso/sessao';
import { routes } from './app.routes';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // XSRF fica ligado (padrão): o Angular copia o cookie XSRF-TOKEN no cabeçalho X-XSRF-TOKEN.
    provideHttpClient(withFetch(), withInterceptors([sessaoExpiradaInterceptor])),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // A rolagem fica com cuidarDaRolagem (compartilhado/rolagem.ts): topo em tela nova, posição
      // de antes no voltar, e nada quando só os filtros da mesma tela mudam.
      withInMemoryScrolling({ scrollPositionRestoration: 'disabled' }),
    ),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
  ],
};
