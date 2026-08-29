import { defineConfig } from 'astro/config';
import node from '@astrojs/node';

export default defineConfig({
  // A pagina de resgate NAO pode ser estatica: os dados mudam, e ela precisa
  // vir renderizada do servidor. Alguem segurando um cachorro desconhecido, no
  // sol, em rede ruim, nao pode esperar bundle + roteador + fetch.
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  server: { port: 4321 },
  devToolbar: { enabled: false },
});
