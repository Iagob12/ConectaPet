import type { APIRoute } from 'astro';

export const GET: APIRoute = () =>
  new Response(
    [
      'User-agent: *',
      'Allow: /',
      'Sitemap: https://www.conectapet.app.br/sitemap.xml',
      '',
    ].join('\n'),
    { headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'public, max-age=3600' } },
  );
