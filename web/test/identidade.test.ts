import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';

/**
 * A vitrine e o produto precisam parecer a mesma marca.
 *
 * A ligação entre os dois arquivos não existe em lugar nenhum do código: é
 * convenção, e convenção sem teste quebra calada. Foi o que aconteceu — o
 * landing.html tinha 30 tokens com Poppins e Inter, escalas de raio, sombra e
 * espaço; o base.css tinha 12 cores e a fonte do sistema. Ninguém relê dois
 * arquivos de CSS procurando um hex diferente por dois dígitos, e o sintoma
 * era o usuário sair de um site e entrar em outro ao terminar o cadastro.
 */
function tokens(caminho: string): Map<string, string> {
  const css = readFileSync(caminho, 'utf8');
  const bloco = css.match(/:root\s*\{([\s\S]*?)\}/);
  if (!bloco) throw new Error(`sem bloco :root em ${caminho}`);
  const mapa = new Map<string, string>();
  for (const m of bloco[1].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    mapa.set(m[1], m[2].trim().replace(/\s*\/\*[\s\S]*$/, '').trim().toLowerCase());
  }
  return mapa;
}

const vitrine = tokens('src/landing.html');
const produto = tokens('src/styles/base.css');

describe('identidade visual', () => {
  it('os dois arquivos de fato declaram tokens', () => {
    // Guarda contra o teste virar vazio se alguém mover o bloco :root.
    expect(vitrine.size).toBeGreaterThan(20);
    expect(produto.size).toBeGreaterThan(20);
  });

  it('nenhum token compartilhado tem valor diferente', () => {
    const divergentes: string[] = [];
    for (const [nome, valor] of produto) {
      const naVitrine = vitrine.get(nome);
      if (naVitrine !== undefined && naVitrine !== valor) {
        divergentes.push(`${nome}: vitrine=${naVitrine} produto=${valor}`);
      }
    }
    expect(divergentes, 'tokens com o mesmo nome e valores diferentes').toEqual([]);
  });

  it('o produto herda a tipografia da marca, e não a do sistema', () => {
    // Era esta a maior quebra: a vitrine com Poppins e Inter, o produto com
    // system-ui. Duas tipografias é o mesmo que duas marcas.
    for (const t of ['--font-display', '--font-title', '--font-body']) {
      expect(produto.get(t), `${t} ausente no produto`).toBeDefined();
      expect(produto.get(t)).toBe(vitrine.get(t));
    }
    const css = readFileSync('src/styles/base.css', 'utf8');
    expect(css, 'body precisa usar a fonte da marca').toMatch(/body\{[\s\S]*var\(--font-body\)/);
    expect(css, 'h1 precisa usar a fonte de títulos').toMatch(/h1\{[\s\S]*var\(--font-title\)/);
  });

  it('não sobrou nenhum símbolo desenhado à mão', () => {
    // A arte aproximada existia enquanto o arquivo real não estava no projeto.
    // Agora está, e um símbolo antigo esquecido num canto é uma marca errada
    // aparecendo para o cliente.
    const html = readFileSync('src/landing.html', 'utf8');
    expect(html).not.toContain('logo-mark');
    expect(html).toContain('/marca.png');
  });
});

const resgate = tokens('src/pages/p/[codigo].astro');

/** Relação de contraste da WCAG entre duas cores em hex. */
function contraste(a: string, b: string): number {
  const lum = (hex: string) => {
    const h = hex.length === 4 ? '#' + [...hex.slice(1)].map((c) => c + c).join('') : hex;
    const c = [1, 3, 5]
      .map((i) => parseInt(h.slice(i, i + 2), 16) / 255)
      .map((v) => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)));
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  };
  const [x, y] = [lum(a), lum(b)];
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

describe('a tela de resgate usa a mesma paleta', () => {
  it('não diverge dos outros dois arquivos', () => {
    // Ela tem CSS próprio de propósito — é embutido, para chegar em uma
    // requisição só. Isso não é desculpa para ter outra paleta.
    const divergentes: string[] = [];
    for (const [nome, valor] of resgate) {
      const base = produto.get(nome);
      if (base !== undefined && base !== valor) {
        divergentes.push(`${nome}: base=${base} resgate=${valor}`);
      }
    }
    expect(divergentes).toEqual([]);
  });
});

describe('contraste dos botões que um estranho aperta na rua', () => {
  // Estes dois já reprovaram: #0F8F4E e #128C7E davam 4.15 e 4.14 contra os
  // 4.5 exigidos, e estavam no ar tanto na vitrine quanto na tela de resgate.
  // O teste existe porque ninguém recalcula contraste ao ajustar um verde.
  for (const token of ['--call-600', '--whats-700']) {
    it(`${token} passa em AA com texto branco`, () => {
      for (const [onde, mapa] of [['vitrine', vitrine], ['produto', produto], ['resgate', resgate]] as const) {
        const cor = mapa.get(token);
        if (!cor) continue;
        const r = contraste('#FFFFFF', cor);
        expect(r, `${token} em ${onde} (${cor}) dá ${r.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
      }
    });
  }
});

describe('canais de contato', () => {
  const html = readFileSync('src/landing.html', 'utf8');

  it('o que promete WhatsApp leva ao WhatsApp', () => {
    // Passou despercebido numa auditoria minha: o botão "Falar com a gente no
    // WhatsApp" apontava para #faq, e os links de rede social apontavam para
    // #topo. Como são âncoras válidas, uma checagem de "link quebrado" aprova
    // as duas — o defeito é o destino não corresponder ao rótulo.
    for (const m of html.matchAll(/<a [^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g)) {
      const [, destino, dentro] = m;
      const texto = dentro.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
      if (/whatsapp/i.test(texto)) {
        expect(destino, `"${texto}" deveria abrir o WhatsApp`).toMatch(/^https:\/\/wa\.me\//);
      }
      if (/^e-?mail$|fale conosco/i.test(texto)) {
        expect(destino, `"${texto}" deveria abrir o e-mail`).toMatch(/^mailto:/);
      }
    }
  });

  it('o número e o e-mail são os oficiais', () => {
    expect(html).toContain('https://wa.me/5567984360414');
    expect(html).toContain('mailto:conectapet.contato@gmail.com');
  });

  it('todo ícone usado existe no sprite', () => {
    // Criei o link de e-mail apontando para um #i-mail que não existia.
    // Ícone ausente não quebra a página: some, calado.
    const usados = [...html.matchAll(/<use href="#(i-[a-z-]+)"/g)].map((m) => m[1]);
    const definidos = new Set([...html.matchAll(/<symbol id="(i-[a-z-]+)"/g)].map((m) => m[1]));
    const faltando = [...new Set(usados)].filter((i) => !definidos.has(i));
    expect(faltando, 'ícones usados mas não definidos').toEqual([]);
  });
});
