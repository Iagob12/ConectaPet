import type { APIRoute } from 'astro';

/**
 * Repassa a foto original do pet, que a API so entrega ao dono autenticado.
 *
 * O navegador nao fala direto com a API — e por isso que a foto precisa passar
 * por aqui. `no-store` acompanha a resposta da API: e foto de pet com dados de
 * contato ao lado, nao pode ficar em cache de proxy.
 */
export const GET: APIRoute = async ({ params, locals }) => {
  const r = await locals.apiBruto(`/api/pets/${params.uuid}/foto`);
  if (!r.ok) {
    return new Response(null, { status: r.status === 401 ? 401 : 404 });
  }
  return new Response(await r.arrayBuffer(), {
    status: 200,
    headers: {
      'Content-Type': r.headers.get('content-type') ?? 'image/jpeg',
      'Cache-Control': 'no-store',
    },
  });
};

export const prerender = false;
