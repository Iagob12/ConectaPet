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

export async function chamar<T>(
  caminho: string,
  opcoes: { metodo?: string; corpo?: unknown; cookie?: string | null } = {}
): Promise<Resposta<T>> {
  const { metodo = 'GET', corpo, cookie } = opcoes;

  let r: Response;
  try {
    r = await fetch(`${BASE}${caminho}`, {
      method: metodo,
      headers: {
        ...(corpo ? { 'Content-Type': 'application/json' } : {}),
        ...(cookie ? { Cookie: cookie } : {}),
      },
      body: corpo ? JSON.stringify(corpo) : undefined,
      redirect: 'manual',
    });
  } catch {
    return {
      ok: false, status: 0, dados: null, campos: {}, cookies: [],
      erro: 'Não consegui falar com o servidor. Verifique sua conexão.',
    };
  }

  const cookies = typeof (r.headers as any).getSetCookie === 'function'
    ? (r.headers as any).getSetCookie()
    : [];

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

/** Repassa ao navegador os cookies que a API emitiu. */
export function repassarCookies(headers: Headers, cookies: string[]) {
  for (const c of cookies) headers.append('Set-Cookie', c);
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
