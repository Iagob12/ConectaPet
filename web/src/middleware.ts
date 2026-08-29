import { defineMiddleware } from 'astro:middleware';
import { chamar, chamarBruto, mesclarCookies, temCookie, type Opcoes, type Resposta } from './lib/api';

const SESSAO = 'cp_sessao';
const REFRESH = 'cp_refresh';

/**
 * Renovacao de sessao no servidor, transparente para a pagina.
 *
 * O access token dura 15 minutos. Sem isto, o painel simplesmente parava de
 * funcionar no meio de uma sessao: a pessoa preenchia a saude do pet e era
 * jogada no login ao salvar, perdendo o que digitou.
 *
 * A renovacao acontece aqui, e nao em cada pagina, para o token novo valer para
 * TODAS as chamadas do mesmo render e o Set-Cookie sair uma vez so, na resposta
 * final — inclusive quando a pagina responde com um redirect proprio.
 */
export const onRequest = defineMiddleware(async (ctx, next) => {
  let cookie = ctx.request.headers.get('cookie');
  const novos: string[] = [];
  let renovando: Promise<boolean> | null = null;

  const renovar = async (): Promise<boolean> => {
    // Uma renovacao por requisicao. Duas chamadas paralelas tomando 401 ao
    // mesmo tempo girariam o refresh duas vezes, e a segunda rotacao pareceria
    // reuso de token — exatamente o sinal que derruba a familia inteira.
    if (!renovando) {
      renovando = (async () => {
        const r = await chamar('/api/auth/refresh', { metodo: 'POST', cookie });
        if (!r.ok || r.cookies.length === 0) return false;
        cookie = mesclarCookies(cookie, r.cookies);
        novos.push(...r.cookies);
        return true;
      })();
    }
    return renovando;
  };

  const podeRenovar = () => temCookie(cookie, REFRESH);

  // Repetir apos o 401 e seguro: 401 vem do filtro de seguranca, antes do
  // handler rodar, entao nada foi executado na primeira tentativa.
  ctx.locals.api = async <T>(caminho: string, opcoes: Opcoes = {}): Promise<Resposta<T>> => {
    const r = await chamar<T>(caminho, { ...opcoes, cookie });
    if (r.status !== 401 || !podeRenovar()) return r;
    if (!(await renovar())) return r;
    return chamar<T>(caminho, { ...opcoes, cookie });
  };

  ctx.locals.apiBruto = async (caminho: string, opcoes: Opcoes = {}): Promise<Response> => {
    const r = await chamarBruto(caminho, { ...opcoes, cookie });
    if (r.status !== 401 || !podeRenovar()) return r;
    if (!(await renovar())) return r;
    return chamarBruto(caminho, { ...opcoes, cookie });
  };

  ctx.locals.autenticado = temCookie(cookie, SESSAO) || temCookie(cookie, REFRESH);

  const resposta = await next();
  for (const c of novos) resposta.headers.append('Set-Cookie', c);
  return resposta;
});
