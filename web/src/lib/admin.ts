import type { APIContext } from 'astro';
import type { Conta } from './painel';

export type Lote = {
  id: number; nome: string; quantidade: number; modelo: string;
  status: 'NAO_CONFIRMADO' | 'CONFIRMADO';
  produzidoEm: string; confirmadoEm: string | null; observacoes: string | null;
};

export type TagAdmin = {
  uuid: string; codigoPublico: string; modelo: string; status: string;
  loteId: number; reivindicada: boolean;
  enviadaEm: string | null; reivindicadaEm: string | null; desativadaEm: string | null;
};

export type Metricas = {
  tagsProduzidas: number; tagsEnviadas: number; tagsAtivadas: number;
  taxaAtivacao: number; leiturasPeriodo: number; petsEmModoPerdido: number;
};

export type Elevacao = { token: string; expiraEm: string };

export const STATUS_TAG: Record<string, string> = {
  CRIADA: 'Criada',
  ENVIADA: 'Enviada',
  REIVINDICADA: 'Reivindicada',
  ATIVA: 'Ativa',
  DESATIVADA: 'Desativada',
};

export const MODELOS: Record<string, string> = {
  CLASSICA: 'Clássica',
  SLIM: 'Slim',
  COLEIRA: 'Coleira',
};

/**
 * Porta do administrativo.
 *
 * Devolve a conta quando ela e ADMIN e uma Response quando nao e. Quem nao e
 * admin vai para o painel de tutor, nao para uma tela de erro: dizer "existe
 * um administrativo aqui, mas nao para voce" e informacao que ninguem precisa.
 */
export async function exigirAdmin(
  ctx: APIContext
): Promise<{ conta: Conta } | { resposta: Response }> {
  const r = await ctx.locals.api<Conta>('/api/me');

  if (r.status === 401 || !r.ok) {
    const destino = ctx.url.pathname + ctx.url.search;
    return { resposta: ctx.redirect('/entrar?next=' + encodeURIComponent(destino)) };
  }
  if (r.dados?.papel !== 'ADMIN') {
    return { resposta: ctx.redirect('/app') };
  }
  return { conta: r.dados };
}

/** Troca a senha por um token curto de elevacao, exigido nas rotas de segredo. */
export async function elevar(ctx: APIContext, senha: string) {
  return ctx.locals.api<Elevacao>('/api/admin/reautenticar', {
    metodo: 'POST',
    corpo: { senha },
  });
}
