import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { API_ADMIN } from '../admin-modelos';
import { MudancaFundo } from './campo-fundo';

/** Envia (ou apaga, ou só refoca) a foto de fundo depois que os dados da empresa foram salvos. */
export async function salvarFundo(http: HttpClient, empresaId: number | string, mudanca: MudancaFundo): Promise<void> {
  const url = `${API_ADMIN}/empresas/${empresaId}/fundo`;
  if (mudanca?.tipo === 'nova') {
    const formulario = new FormData();
    formulario.append('arquivo', mudanca.foto.jpeg, 'fundo.jpg');
    formulario.append('focoX', String(mudanca.foco.x));
    formulario.append('focoY', String(mudanca.foco.y));
    await firstValueFrom(http.put(url, formulario));
  } else if (mudanca?.tipo === 'foco') {
    await firstValueFrom(http.put(`${url}/foco`, mudanca.foco));
  } else if (mudanca?.tipo === 'remover') {
    await firstValueFrom(http.delete(url));
  }
}
