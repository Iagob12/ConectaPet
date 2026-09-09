import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const html = readFileSync('src/landing.html', 'utf8');

describe('orientacao de configuracao na pagina inicial', () => {
  it('aparece antes da explicacao geral do produto', () => {
    expect(html.indexOf('id="configurar-tag"')).toBeGreaterThan(0);
    expect(html.indexOf('id="configurar-tag"')).toBeLessThan(html.indexOf('id="como-funciona"'));
  });

  it('explica o papel do QR Code, a aproximacao e a confirmacao final', () => {
    expect(html).toContain('O QR Code do cartão trouxe você até esta orientação.');
    expect(html).toContain('Encoste o celular no chaveiro');
    expect(html).toContain('Confirmar cadastro do pet');
  });

  it('oferece a orientacao pelo menu principal e pelo menu do celular', () => {
    expect(html.match(/href="#configurar-tag"/g)?.length).toBeGreaterThanOrEqual(3);
  });
});
