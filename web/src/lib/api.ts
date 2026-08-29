const BASE = import.meta.env.API_URL ?? 'http://localhost:8080';

/**
 * A pagina conversa com a API pelo SERVIDOR, nao pelo navegador.
 *
 * Isso resolve tres coisas de uma vez: nao ha CORS, o token nunca passa perto
 * de JavaScript do cliente (ele so existe em cookie HttpOnly), e os formularios
 * funcionam sem JavaScript nenhum, porque viram POST de HTML puro.
 */
export type Resposta<T> = {
  ok: boolean;
  status: number;
  dados: T | null;
  /** Mensagem em portugues, ja pronta para a tela. */
  erro: string | null;
  /** Erros por campo do problem+json, para exibir no campo certo. */
  campos: Record<string, string>;
  /** Set-Cookie devolvido pela API, para repassar ao navegador. */
  cookies: string[];
};

export type Opcoes = {
  metodo?: string;
  /** Objeto vira JSON; FormData vai como multipart, sem tocar no boundary. */
  corpo?: unknown;
  cookie?: string | null;
  /** Cabecalhos extras. Usado pela reautenticacao do administrativo. */
  cabecalhos?: Record<string, string>;
};

/** A resposta crua, para quando o corpo nao e JSON (imagem, CSV). */
export async function chamarBruto(caminho: string, opcoes: Opcoes = {}): Promise<Response> {
  const { metodo = 'GET', corpo, cookie, cabecalhos } = opcoes;
  const multipart = typeof FormData !== 'undefined' && corpo instanceof FormData;

  return fetch(`${BASE}${caminho}`, {
    method: metodo,
    headers: {
      // Em multipart o Content-Type precisa vir do fetch: ele carrega o
      // boundary, e escrever o cabecalho a mao quebraria o parse do outro lado.
      ...(corpo && !multipart ? { 'Content-Type': 'application/json' } : {}),
      ...(cookie ? { Cookie: cookie } : {}),
      ...(cabecalhos ?? {}),
    },
    body: corpo == null ? undefined : (multipart ? (corpo as FormData) : JSON.stringify(corpo)),
    redirect: 'manual',
  });
}

export async function chamar<T>(caminho: string, opcoes: Opcoes = {}): Promise<Resposta<T>> {
  let r: Response;
  try {
    r = await chamarBruto(caminho, opcoes);
  } catch {
    return {
      ok: false, status: 0, dados: null, campos: {}, cookies: [],
      erro: 'Não consegui falar com o servidor. Verifique sua conexão.',
    };
  }

  const cookies = lerSetCookie(r.headers);

  let corpoResposta: any = null;
  const texto = await r.text();
  if (texto) {
    try { corpoResposta = JSON.parse(texto); } catch { corpoResposta = null; }
  }

  if (r.ok) {
    return { ok: true, status: r.status, dados: corpoResposta as T, erro: null, campos: {}, cookies };
  }

  const campos: Record<string, string> = {};
  if (Array.isArray(corpoResposta?.errors)) {
    for (const e of corpoResposta.errors) {
      if (e?.campo) campos[e.campo] = e.mensagem;
    }
  }

  return {
    ok: false,
    status: r.status,
    dados: null,
    campos,
    cookies,
    erro: corpoResposta?.detail ?? corpoResposta?.title ?? 'Algo deu errado. Tente de novo.',
  };
}

export function lerSetCookie(headers: Headers): string[] {
  return typeof (headers as any).getSetCookie === 'function' ? (headers as any).getSetCookie() : [];
}

/** Repassa ao navegador os cookies que a API emitiu. */
export function repassarCookies(headers: Headers, cookies: string[]) {
  for (const c of cookies) headers.append('Set-Cookie', c);
}

/**
 * Aplica Set-Cookie sobre um cabecalho Cookie ja existente.
 *
 * Depois de renovar a sessao no meio de um render, as chamadas seguintes
 * precisam usar o token novo — senao a primeira renova e as outras continuam
 * levando o token velho e tomando 401 em sequencia.
 */
export function mesclarCookies(cookie: string | null, setCookie: string[]): string {
  const mapa = new Map<string, string>();
  for (const par of (cookie ?? '').split(';')) {
    const i = par.indexOf('=');
    if (i > 0) mapa.set(par.slice(0, i).trim(), par.slice(i + 1).trim());
  }
  for (const bruto of setCookie) {
    const primeiro = bruto.split(';')[0];
    const i = primeiro.indexOf('=');
    if (i > 0) mapa.set(primeiro.slice(0, i).trim(), primeiro.slice(i + 1).trim());
  }
  return [...mapa].map(([k, v]) => `${k}=${v}`).join('; ');
}

export function temCookie(cookie: string | null, nome: string): boolean {
  return new RegExp(`(^|;\s*)${nome}=[^;]`).test(cookie ?? '');
}

/** Normaliza o que a pessoa digitou no campo de codigo. */
export const ALFABETO = '23456789ABCDEFGHJKMNPQRSTVWXYZ';

export function normalizarCodigo(bruto: string | null | undefined): string {
  return (bruto ?? '').trim().toUpperCase().replace(/[\s-]/g, '');
}

/**
 * Devolve o caractere invalido, se houver.
 *
 * A validacao acontece antes do envio para nao gastar uma das 5 tentativas por
 * hora com um erro de leitura do cartao. E orienta em vez de adivinhar: trocar
 * 0 por O automaticamente esconderia o engano e faria a pessoa errar de novo.
 */
export function caractereInvalido(codigo: string): string | null {
  for (const c of codigo) {
    if (!ALFABETO.includes(c)) return c;
  }
  return null;
}

/**
 * Redireciona levando os cookies junto.
 *
 * Astro.redirect() monta uma Response nova e descarta o que foi posto em
 * Astro.response.headers — entao o Set-Cookie da API se perdia e a pessoa
 * voltava para a tela de login logo apos criar a conta.
 */
export function redirecionarCom(destino: string, cookies: string[]): Response {
  const headers = new Headers({ Location: destino });
  for (const c of cookies) headers.append('Set-Cookie', c);
  return new Response(null, { status: 303, headers });
}

/** 303 apos POST: tira o formulario do historico e um F5 nao reenvia. */
export function verDepois(destino: string): Response {
  return new Response(null, { status: 303, headers: { Location: destino } });
}
