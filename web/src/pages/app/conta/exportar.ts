import type { APIRoute } from 'astro';

/**
 * Repassa o arquivo de dados que a API gera.
 *
 * Passa por aqui porque o navegador nao fala direto com a API. O
 * Content-Disposition e reescrito em vez de repassado: e ele que faz o
 * navegador salvar em vez de exibir um JSON gigante na tela.
 */
export const GET: APIRoute = async ({ locals, redirect }) => {
  const r = await locals.apiBruto('/api/me/exportar');
  if (r.status === 401) return redirect('/entrar?next=%2Fapp%2Fconta');
  if (!r.ok) return redirect('/app/conta?export-falhou=1');

  const hoje = new Date().toISOString().slice(0, 10);
  return new Response(await r.arrayBuffer(), {
    status: 200,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Content-Disposition': `attachment; filename="conectapet-meus-dados-${hoje}.json"`,
      'Cache-Control': 'no-store',
    },
  });
};

export const prerender = false;
