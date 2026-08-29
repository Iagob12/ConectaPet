import type { APIRoute } from 'astro';
// ?raw embute o arquivo no bundle em tempo de build. Serve para dev e para o
// servidor de producao sem depender de caminho em disco.
import landing from '../landing.html?raw';

/**
 * A landing continua sendo o arquivo unico aprovado, servida pelo mesmo
 * servidor do painel.
 *
 * Fica numa rota, e nao em public/, para existir um endereco so. Em public/
 * ela responderia tambem em /index.html — duas URLs para a mesma pagina, que e
 * conteudo duplicado para busca. (No servidor de desenvolvimento o Vite ainda
 * atende /index.html; no build de producao ele da 404, como se espera.)
 */
export const GET: APIRoute = () =>
  new Response(landing, {
    headers: {
      'Content-Type': 'text/html; charset=utf-8',
      // Publica e igual para todo mundo, mas revalida: a copy ainda muda.
      'Cache-Control': 'public, max-age=0, must-revalidate',
    },
  });

export const prerender = false;
