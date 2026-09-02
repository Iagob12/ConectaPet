import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { ApiDublada, SiteEmExecucao, perfilDeExemplo } from './apoio/servidores';

/**
 * As telas rodando, exercitadas por HTTP contra o build de produção.
 *
 * O que se mede aqui é o que chega ao navegador de quem achou o pet: o HTML da
 * primeira resposta, os redirecionamentos e o comportamento quando a API cai.
 * Nada disso aparece testando funções isoladas.
 */
const api = new ApiDublada();
const site = new SiteEmExecucao();

const ATIVA = 'HW9NNWQ5JB';
const SEM_PERFIL = 'ABCDEF2345';

beforeAll(async () => {
  await api.subir();
  api.perfis.set(ATIVA, perfilDeExemplo());
  await site.subir(`http://127.0.0.1:${api.porta}`, 4399);
}, 60_000);

afterAll(async () => {
  site.derrubar();
  await api.derrubar();
});

const pegar = (caminho: string, init?: RequestInit) =>
  fetch(site.base + caminho, { redirect: 'manual', ...init });

describe('página de resgate — a tela mais importante', () => {
  it('tag com perfil mostra o pet e como falar com o dono', async () => {
    const r = await pegar(`/p/${ATIVA}`);
    const html = await r.text();

    expect(r.status).toBe(200);
    expect(html).toContain('Thor');
    expect(html).toContain('Campinas, SP');
    // Discagem direta: funciona sem app e sem JavaScript.
    expect(html).toContain('tel:+5511988887777');
    expect(html).toContain('wa.me/5511988887777');
  });

  it('mostra o cabeçalho do site com navegação no computador e no celular', async () => {
    const html = await (await pegar(`/p/${ATIVA}`)).text();

    expect(html).toContain('class="site-header"');
    expect(html).toContain('href="/#como-funciona"');
    expect(html).toContain('href="/ativar"');
    expect(html).toContain('href="/entrar"');
    expect(html).toContain('id="menu-btn"');
    expect(html).toContain('id="menu-mobile"');
  });

  it('tem um h1, e ele é o nome do pet', async () => {
    // Sem h1, quem usa leitor de tela chega numa página sem título e com
    // <h2> soltos — a lista de cabeçalhos, que é como essas pessoas se
    // orientam, vem quebrada. O nome do pet já era o maior texto da tela;
    // agora ele também é o cabeçalho, e a hierarquia fecha.
    const html = await (await pegar(`/p/${ATIVA}`)).text();

    const inicioH1 = html.indexOf('<h1');
    expect(inicioH1).toBeGreaterThan(-1);
    expect(html.slice(inicioH1, html.indexOf('</h1>', inicioH1))).toContain('Thor');

    // Nenhum <h2> pode aparecer antes do <h1>.
    const posH2 = html.indexOf('<h2');
    if (posH2 !== -1) expect(inicioH1).toBeLessThan(posH2);
  });

  it('o foco do teclado é visível nos botões de contato', async () => {
    // O anel padrão do navegador é desenhado com a cor do texto do elemento:
    // sobre os botões escuros de ligar e WhatsApp ele some. Quem navega por
    // teclado ou controle adaptativo perde a única pista de onde está.
    const html = await (await pegar(`/p/${ATIVA}`)).text();
    // A folha e embutida (ver inlineStylesheets no astro.config); se um dia
    // voltar a ser externa, o teste a busca em vez de falhar por engano.
    const externa = html.match(/<link rel="stylesheet" href="([^"]+)"/);
    const css = externa ? await (await pegar(externa[1])).text() : html;
    expect(css).toContain(':focus-visible');
  });

  it('não entra em índice de busca', async () => {
    // A página tem nome, foto e telefone de uma pessoa. Indexar isso
    // transformaria o produto num diretório de contatos.
    const r = await pegar(`/p/${ATIVA}`);
    expect(r.headers.get('x-robots-tag')).toContain('noindex');
    expect(r.headers.get('cache-control')).toContain('no-store');
  });

  it('modo perdido põe o alerta antes de tudo', async () => {
    api.perfis.set(ATIVA, perfilDeExemplo({ perdido: true }));
    const html = await (await pegar(`/p/${ATIVA}`)).text();
    api.perfis.set(ATIVA, perfilDeExemplo());

    expect(html).toContain('ESTE PET ESTÁ PERDIDO');
    // Antes do cartão do pet: é a primeira informação que a pessoa lê. A
    // comparação começa depois do <body> porque o nome do pet também está no
    // <title>, que vem antes de tudo.
    const corpo = html.slice(html.indexOf('<body'));
    expect(corpo.indexOf('PERDIDO')).toBeLessThan(corpo.indexOf('class="nome"'));
  });

  it('tag sem perfil vai direto para o cadastro', async () => {
    const r = await pegar(`/p/${SEM_PERFIL}`);
    expect(r.status).toBe(302);
    expect(r.headers.get('location')).toBe(`/ativar?codigo=${SEM_PERFIL}`);
  });

  it('código inexistente segue para o mesmo lugar que o não ativado', async () => {
    // Se diferissem, a página viraria um verificador de quais códigos existem.
    const r = await pegar('/p/ZZZZZZZZZZ');
    expect(r.status).toBe(302);
    expect(r.headers.get('location')).toBe('/ativar?codigo=ZZZZZZZZZZ');
  });
});

describe('quando a API cai', () => {
  it('não culpa a conexão de quem está lendo, e mostra o código', async () => {
    await api.derrubar();
    const html = await (await pegar(`/p/${ATIVA}`)).text();
    await api.subir();
    api.perfis.set(ATIVA, perfilDeExemplo());

    expect(html).toContain('Nossos servidores não responderam');
    // "Verifique sua conexão" era falso: a página só renderizou porque a
    // conexão da pessoa funciona.
    expect(html).not.toContain('Verifique sua conexão');
    // Com o código anotado, o resgate continua possível.
    expect(html).toContain(ATIVA);
  });

  it('API travada não deixa a página carregando para sempre', async () => {
    api.comportamento = { tipo: 'travado' };
    const inicio = Date.now();
    const html = await (await pegar(`/p/${ATIVA}`)).text();
    const decorrido = Date.now() - inicio;
    api.comportamento = { tipo: 'normal' };

    // Duas tentativas de 4s. O que se trava aqui é o teto existir: sem ele a
    // espera é indefinida, que é pior do que falhar.
    expect(decorrido).toBeLessThan(15_000);
    expect(html).toContain('Nossos servidores não responderam');
  }, 30_000);
});

describe('rotas que exigem sessão', () => {
  it.each([
    ['/app', '/entrar?next=%2Fapp'],
    ['/app/conta', '/entrar?next=%2Fapp%2Fconta'],
  ])('%s manda para o login guardando o destino', async (caminho, destino) => {
    const r = await pegar(caminho);
    expect(r.status).toBe(302);
    expect(r.headers.get('location')).toBe(destino);
  });

  it('o administrativo não revela que existe para quem não é admin', async () => {
    // Sem sessão vai para o login como qualquer rota; a distinção entre
    // "não é admin" e "não existe" não é informação que ajude ninguém.
    const r = await pegar('/admin');
    expect(r.status).toBe(302);
    expect(r.headers.get('location')).toContain('/entrar');
  });
});

describe('entrada do fluxo de ativação', () => {
  it('guarda o código num cookie de servidor', async () => {
    const r = await pegar(`/ativar?codigo=${SEM_PERFIL}`);
    const cookies = r.headers.getSetCookie().join(' ');

    expect(cookies).toContain(`cp_ativando=${SEM_PERFIL}`);
    // HttpOnly porque não há motivo para JavaScript de página ler isso.
    expect(cookies).toContain('HttpOnly');
  });

  it('avisa antes do cadastro quando a tag já tem dono', async () => {
    const html = await (await pegar(`/ativar?codigo=${ATIVA}`)).text();

    expect(html).toContain('Esta tag já está em uso');
    expect(html).toContain('código de transferência');
  });

  it('código com caractere banido não vira cookie', async () => {
    const r = await pegar('/ativar?codigo=LIVRETAG12');
    expect(r.headers.getSetCookie().join(' ')).not.toContain('cp_ativando=LIVRETAG12');
  });
});

describe('vitrine e telas públicas', () => {
  it('a landing é servida na raiz', async () => {
    const html = await (await pegar('/')).text();
    expect(html).toContain('<title>ConectaPet');
    expect(html).toContain('Aproxima e Protege');
  });

  it('a landing leva para ativar e para entrar', async () => {
    const html = await (await pegar('/')).text();
    expect(html).toContain('href="/ativar"');
    expect(html).toContain('href="/entrar"');
  });

  it('login e recuperação de senha abrem sem sessão', async () => {
    for (const caminho of ['/entrar', '/criar-conta', '/esqueci-senha']) {
      expect((await pegar(caminho)).status, caminho).toBe(200);
    }
  });

  it('formulário da própria origem passa', async () => {
    // Trava o bug que só aparecia no build de produção: sem os hosts
    // permitidos configurados, o Astro calculava a origem como
    // "http://localhost" e recusava TODO envio de formulário — login, cadastro
    // e ativação. O site subia e nada que dependesse de POST funcionava.
    const r = await pegar('/entrar', {
      method: 'POST',
      headers: { Origin: site.base, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'email=a@b.com&senha=xxxxxxxxxx',
    });
    expect(r.status).not.toBe(403);
  });

  it('formulário de outro site é barrado', async () => {
    const r = await pegar('/entrar', {
      method: 'POST',
      headers: { Origin: 'http://site-malicioso.com', 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'email=a@b.com&senha=xxxxxxxxxx',
    });
    expect(r.status).toBe(403);
  });

  it('POST sem corpo de formulário responde 400, sem girar em redirect', async () => {
    // Redirecionar para a própria URL fazia um cliente que repete o POST girar
    // sem fim.
    const r = await pegar('/entrar', {
      method: 'POST',
      headers: { Origin: site.base, 'Content-Type': 'text/plain' },
      body: 'nao-e-formulario',
    });
    expect(r.status).toBe(400);
  });
});

describe('cabeçalhos de segurança', () => {
  it('a página de resgate não pode ser emoldurada por outro site', async () => {
    // Sem isto, um golpe monta "achamos seu pet, pague para reaver" com o
    // perfil real num iframe atrás — convincente porque os dados são reais.
    const r = await pegar(`/p/${ATIVA}`);
    expect(r.headers.get('x-frame-options')).toBe('DENY');
    expect(r.headers.get('content-security-policy')).toContain("frame-ancestors 'none'");
  });

  it('o código da tag não vaza para o WhatsApp no Referer', async () => {
    const r = await pegar(`/p/${ATIVA}`);
    expect(r.headers.get('referrer-policy')).toBe('strict-origin-when-cross-origin');
  });

  it('a política deixa a foto e o aviso ao tutor funcionarem', async () => {
    // O navegador fala DIRETO com a API nesses dois pontos. Uma política que
    // esqueça a origem da API apaga a foto e cala a notificação — e nada
    // acusa: o erro fica só no console de quem achou o pet.
    const csp = (await pegar(`/p/${ATIVA}`)).headers.get('content-security-policy') ?? '';
    const origemApi = `http://127.0.0.1:${api.porta}`;
    expect(csp).toContain(`img-src 'self' data: ${origemApi}`);
    expect(csp).toContain('blob:');
    expect(csp).toContain(`connect-src 'self' ${origemApi}`);
  });

it('a política não bloqueia as fontes que a landing carrega', async () => {
    // Aconteceu de verdade: a primeira versão desta política bloqueou a folha
    // do Google Fonts e a landing passou a renderizar com a fonte do sistema.
    // Não quebra nada de forma visível — a tipografia inteira só muda, calada.
    // Este teste lê os hosts do próprio HTML, então cobre também qualquer
    // fonte que venha a ser adicionada depois.
    const html = await (await pegar('/')).text();
    const csp = (await pegar('/entrar')).headers.get('content-security-policy') ?? '';

    const externos = [...html.matchAll(/<link[^>]+href="(https:\/\/[^"]+)"/g)]
      .map((m) => new URL(m[1]).origin);

    for (const origem of new Set(externos)) {
      expect(csp, `${origem} não está liberado no CSP`).toContain(origem);
    }
    // Guarda contra o teste virar vazio se a landing parar de linkar externos.
    expect(csp).toContain('https://fonts.googleapis.com');
    expect(csp).toContain('https://fonts.gstatic.com');
  });

  it('as demais páginas também vêm protegidas', async () => {
    const r = await pegar('/entrar');
    expect(r.headers.get('x-content-type-options')).toBe('nosniff');
    expect(r.headers.get('content-security-policy')).toContain("object-src 'none'");
    expect(r.headers.get('content-security-policy')).toContain("form-action 'self'");
  });
});
