import { defineConfig } from 'astro/config';
import node from '@astrojs/node';
import vercel from '@astrojs/vercel';

/**
 * O adaptador vem do ambiente.
 *
 * Vercel em producao; Node em desenvolvimento e nos testes, que sobem
 * dist/server/entry.mjs como um processo comum. Fixar o adaptador da Vercel
 * aqui tiraria a suite inteira do ar, e ela e o que garante que a pagina de
 * resgate continua de pe.
 */
function adaptador() {
  return process.env.ALVO_DEPLOY === 'vercel'
    ? vercel()
    : node({ mode: 'standalone' });
}

/**
 * Converte URL_SITE no formato que o Astro espera, e acrescenta o localhost do
 * desenvolvimento. Sem entrada nenhuma a lista fica vazia e a checagem quebra,
 * entao o padrao precisa cobrir o caso de quem so roda `astro dev`.
 */
function dominiosPermitidos() {
  const padrao = [{ hostname: 'localhost' }, { hostname: '127.0.0.1' }];
  const bruto = process.env.URL_SITE;
  if (!bruto) return padrao;
  try {
    const u = new URL(bruto);
    return [...padrao, {
      hostname: u.hostname,
      protocol: u.protocol.replace(":", ""),
      ...(u.port ? { port: u.port } : {}),
    }];
  } catch {
    return padrao;
  }
}

export default defineConfig({
  // A pagina de resgate NAO pode ser estatica: os dados mudam, e ela precisa
  // vir renderizada do servidor. Alguem segurando um cachorro desconhecido, no
  // sol, em rede ruim, nao pode esperar bundle + roteador + fetch.
  // Endereco publico. Usado para link canonico e para a checagem abaixo.
  build: {
    // O padrao do Astro e "auto", que so embute folhas abaixo de 4 kB. A da
    // pagina de resgate tem 5 kB e ficava de fora por pouco — custando uma ida
    // e volta a mais para buscar o CSS antes de a tela existir.
    //
    // Essa ida e volta e paga por TODO leitor de tag, sempre: a pagina responde
    // no-store e quem a abre e um estranho que nunca esteve no site, entao nao
    // ha cache a aproveitar. Em 4G ruim, na rua, com um cachorro no colo, ela
    // aparece como um instante de tela branca antes dos botoes de ligar.
    //
    // O custo da troca e real e pequeno: as folhas deixam de ser cacheadas
    // entre paginas do painel, onde a navegacao e repetida. Cinco a sete
    // quilobytes por pagina, contra uma ida e volta na tela que justifica o
    // produto inteiro.
    inlineStylesheets: 'always',
  },
  site: process.env.URL_SITE ?? 'http://localhost:4321',

  security: {
    // Os hosts que o servidor aceita responder.
    //
    // Parece detalhe de configuracao e nao e: com a lista vazia (o padrao), o
    // Astro descarta o cabecalho Host e passa a calcular a origem da
    // requisicao como "http://localhost". A protecao contra CSRF compara essa
    // origem com o Origin que o navegador manda, nunca casa, e TODO envio de
    // formulario vira 403 — login, cadastro e ativacao inclusive. O site sobe,
    // as paginas abrem, e nada que dependa de POST funciona.
    //
    // Em desenvolvimento nao aparece, porque ali a origem vem da requisicao.
    // So quebra no build de producao, que e onde ninguem estava olhando.
    allowedDomains: dominiosPermitidos(),
  },
  output: 'server',
  adapter: adaptador(),
  server: { port: 4321 },
  devToolbar: { enabled: false },
});
