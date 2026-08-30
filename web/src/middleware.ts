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
  aplicarCabecalhosDeSeguranca(resposta);
  return resposta;
});

/**
 * Cabecalhos de seguranca da resposta.
 *
 * Existiam so no Caddyfile — que e o caminho da VPS, nao o do deploy real.
 * Em Vercel + Render a pilha nao passa por Caddy nenhum, entao na producao de
 * verdade so havia HSTS, posto pela propria Vercel. Aqui eles valem para as
 * duas rotas de deploy, porque saem da aplicacao e nao da infraestrutura.
 *
 * frame-ancestors e o que mais importa neste produto. Sem ele, qualquer site
 * pode carregar /p/<codigo> dentro de um iframe e desenhar o que quiser por
 * cima — uma pagina de "achamos seu pet, pague para reaver" com o perfil real
 * emoldurado atras fica convincente justamente porque os dados SAO os reais.
 *
 * Referrer-Policy: os navegadores atuais ja mandam so a origem para outro site,
 * entao o codigo da tag nao vaza para o WhatsApp por padrao. Mas esse codigo e
 * a unica coisa que protege o telefone e a cidade de uma pessoa, e depender do
 * padrao do navegador de quem achou o pet — que pode ser velho — e fino demais
 * para o que esta em jogo. Declarado, custa uma linha.
 */
function aplicarCabecalhosDeSeguranca(resposta: Response) {
  // Nao mexe no que ja foi definido pela rota: a pagina de resgate, por
  // exemplo, define o proprio Cache-Control e X-Robots-Tag.
  const por = (nome: string, valor: string) => {
    if (!resposta.headers.has(nome)) resposta.headers.set(nome, valor);
  };

  por('X-Content-Type-Options', 'nosniff');
  por('Referrer-Policy', 'strict-origin-when-cross-origin');
  por('X-Frame-Options', 'DENY');
  por('Permissions-Policy', 'camera=(), microphone=(), payment=()');

  // A API entra na politica porque o NAVEGADOR fala com ela direto em dois
  // pontos: a foto do pet (img-src) e a confirmacao de leitura (connect-src).
  // Sem esses dois, a foto some da tela de resgate e o tutor deixa de ser
  // avisado — e nada acusaria o erro, so o CSP no console.
  const api = apiPublica();
  const politica = [
    "default-src 'self'",
    ("img-src 'self' data: " + api).trim(),
    ("connect-src 'self' " + api).trim(),
    // 'unsafe-inline' e uma concessao honesta: o Astro embute estilo e script
    // na pagina, e nonce por requisicao exigiria reescrever o build. O ganho
    // que fica de pe e o que importa aqui — script de OUTRO servidor nao roda.
    "script-src 'self' 'unsafe-inline'",
    // A landing carrega Inter e Poppins do Google Fonts: a folha vem de
    // fonts.googleapis.com e os arquivos de fonte de fonts.gstatic.com. Sao os
    // dois unicos hosts externos do site inteiro, e estao aqui nominalmente —
    // liberar 'https:' inteiro seria mais facil e nao protegeria de nada.
    //
    // Descoberto quebrando: a primeira versao desta politica bloqueou a folha,
    // e a landing passou a renderizar com a fonte do sistema. Nada quebra de
    // forma visivel nesse caso — so a tipografia inteira muda, em silencio.
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
  por('Content-Security-Policy', politica);
}

/** Mesma leitura de ambiente das paginas: process.env antes do congelado no build. */
function apiPublica(): string {
  const amb = typeof process !== 'undefined' ? process.env : ({} as Record<string, string>);
  const bruto = amb.API_URL_PUBLICA ?? amb.API_URL ?? import.meta.env.API_URL ?? '';
  if (!bruto) return '';
  try {
    return new URL(bruto).origin;
  } catch {
    return '';
  }
}
