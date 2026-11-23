import { formatarTelefone } from './entrada-telefone';

const HORA = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });
const DIA_MES = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' });
const DIA_MES_ANO = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' });
const COMPLETA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeStyle: 'short' });

function mesmoDia(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "Hoje, 16:32", "Ontem, 10:05", "22 de nov., 16:32" ou "22 de nov. de 2025". */
export function quando(iso: string | null | undefined, agora = new Date()): string {
  if (!iso) return '—';
  const data = new Date(iso);
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (mesmoDia(data, agora)) return `Hoje, ${HORA.format(data)}`;
  if (mesmoDia(data, ontem)) return `Ontem, ${HORA.format(data)}`;
  if (data.getFullYear() === agora.getFullYear()) return `${DIA_MES.format(data)}, ${HORA.format(data)}`;
  return DIA_MES_ANO.format(data);
}

/** "22 de novembro de 2026 às 16:32". */
export function dataCompleta(iso: string | null | undefined): string {
  return iso ? COMPLETA.format(new Date(iso)) : '—';
}

/** Tempo entre dois instantes: "menos de 1 min", "3 min", "1 h 05 min". */
export function duracao(inicio: string | null | undefined, fim: string | null | undefined): string | null {
  if (!inicio || !fim) return null;
  const segundos = Math.max(0, Math.round((new Date(fim).getTime() - new Date(inicio).getTime()) / 1000));
  if (segundos < 60) return 'menos de 1 min';
  const minutos = Math.round(segundos / 60);
  if (minutos < 60) return `${minutos} min`;
  return `${Math.floor(minutos / 60)} h ${String(minutos % 60).padStart(2, '0')} min`;
}

/** "5511987654321" → "+55 (11) 98765-4321". */
export function whatsappLegivel(digitos: string): string {
  const nacional = digitos.startsWith('55') ? digitos.slice(2) : digitos;
  return `+55 ${formatarTelefone(nacional)}`;
}

export function linkWhatsapp(digitos: string): string {
  return `https://wa.me/${digitos}`;
}
