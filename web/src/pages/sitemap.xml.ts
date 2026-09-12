import type { APIRoute } from 'astro';

const origem = 'https://www.conectapet.app.br';
const paginas = [
  { caminho: '/', prioridade: '1.0', frequencia: 'weekly', alterado: '2026-09-12' },
  { caminho: '/chaveiro-nfc-pet', prioridade: '0.9', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/tag-nfc-para-cachorro', prioridade: '0.9', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/como-configurar-tag-nfc-pet', prioridade: '0.9', frequencia: 'monthly', alterado: '2026-09-10' },
  { caminho: '/cartaz-pet-perdido', prioridade: '0.9', frequencia: 'monthly', alterado: '2026-09-10' },
  { caminho: '/identificacao-petshop', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-10' },
  { caminho: '/sobre-a-conectapet', prioridade: '0.7', frequencia: 'monthly', alterado: '2026-09-10' },
  { caminho: '/blog', prioridade: '0.8', frequencia: 'weekly', alterado: '2026-09-12' },
  { caminho: '/blog/cachorro-fugiu-como-procurar', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/blog/como-funciona-nfc-no-celular', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/blog/o-que-colocar-na-identificacao-do-pet', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/blog/achei-um-cachorro-perdido-o-que-fazer', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/blog/tag-nfc-plaquinha-microchip-ou-gps', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/blog/como-evitar-que-seu-cachorro-se-perca', prioridade: '0.8', frequencia: 'monthly', alterado: '2026-09-12' },
  { caminho: '/privacidade', prioridade: '0.2', frequencia: 'yearly', alterado: '2026-09-10' },
];

export const GET: APIRoute = () => {
  const urls = paginas.map(({ caminho, prioridade, frequencia, alterado }) => `  <url>
    <loc>${origem}${caminho}</loc>
    <lastmod>${alterado}</lastmod>
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
