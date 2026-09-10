import { describe, expect, it } from 'vitest';
import { readFileSync, statSync } from 'node:fs';

const html = readFileSync('src/landing.html', 'utf8');

describe('orientacao de configuracao na pagina inicial', () => {
  it('aparece antes da explicacao geral do produto', () => {
    expect(html.indexOf('id="configurar-tag"')).toBeGreaterThan(0);
    expect(html.indexOf('id="configurar-tag"')).toBeLessThan(html.indexOf('id="como-funciona"'));
  });

  it('explica o papel do QR Code, a aproximacao e a confirmacao final', () => {
    expect(html).toContain('O QR Code do cartão mostra como começar.');
    expect(html).toContain('Encoste o celular no chaveiro');
    expect(html).toContain('Confirmar cadastro do pet');
  });

  it('oferece a orientacao pelo menu principal e pelo menu do celular', () => {
    expect(html.match(/href="#configurar-tag"/g)?.length).toBeGreaterThanOrEqual(3);
  });

  it('separa as duas jornadas no banner sem repetir uma faixa intermediaria', () => {
    expect(html).toContain('href="#chamada-final">Quero proteger meu pet');
    expect(html).toContain('href="#configurar-tag">Já tenho a tag');
    expect(html).not.toContain('decisionbar');
  });

  it('usa imagens especificas para o resgate e para o perfil demonstrativo', () => {
    expect(html).toContain('/imagens/home-resgate-conectapet.webp');
    expect(html).toContain('/imagens/pet-perfil-exemplo.webp');
    expect(html).toContain('Nina, uma cachorra vira-lata de pelagem caramelo e branca');
    expect(html).not.toContain('data:image');
  });

  it('mostra os quatro passos com imagens instrutivas e alternativas textuais', () => {
    const imagens = [
      ['ativacao-passo-1-nfc.webp', 'Mão segurando um celular Android com a chave NFC ligada'],
      ['ativacao-passo-2-aproximar.webp', 'celular sendo aproximada da tag ConectaPet'],
      ['ativacao-passo-3-aviso.webp', 'Aviso da ConectaPet aparecendo no celular'],
      ['ativacao-passo-4-cadastro.webp', 'Cadastro do pet preenchido no celular'],
    ];

    for (const [arquivo, descricao] of imagens) {
      expect(html).toContain(`/imagens/${arquivo}`);
      expect(html).toContain(descricao);
      expect(statSync(`public/imagens/${arquivo}`).size).toBeGreaterThan(10_000);
      expect(statSync(`public/imagens/${arquivo}`).size).toBeLessThan(100_000);
    }
    expect(html).not.toContain('<video');
  });
});
