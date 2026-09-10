import type { APIRoute } from 'astro';

const origem = 'https://www.conectapet.app.br';
const paginas = [
  { caminho: '/', prioridade: '1.0', frequencia: 'weekly' },
  { caminho: '/tag-nfc-para-cachorro', prioridade: '0.9', frequencia: 'monthly' },
  { caminho: '/como-configurar-tag-nfc-pet', prioridade: '0.9', frequencia: 'monthly' },
  { caminho: '/identificacao-petshop', prioridade: '0.8', frequencia: 'monthly' },
  { caminho: '/privacidade', prioridade: '0.2', frequencia: 'yearly' },
];

export const GET: APIRoute = () => {
  const urls = paginas.map(({ caminho, prioridade, frequencia }) => `  <url>
    <loc>${origem}${caminho}</loc>
    <changefreq>${frequencia}</changefreq>
    <priority>${prioridade}</priority>
  </url>`).join('\n');

  return new Response(`<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>\n`, {
    headers: { 'Content-Type': 'application/xml; charset=utf-8', 'Cache-Control': 'public, max-age=3600' },
  });
};
