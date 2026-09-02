import { describe, expect, it } from 'vitest';
import { readFile } from 'node:fs/promises';

const ler = (arquivo: string) => readFile(new URL(arquivo, import.meta.url), 'utf8');

describe('confirmação final da NFC', () => {
  it('avançar depois de ler o código não reivindica a tag', async () => {
    const pagina = await ler('../src/pages/ativar/codigo.astro');

    expect(pagina).not.toContain("/api/tags/reivindicar");
    expect(pagina).toContain("verDepois('/ativar/pet')");
  });

  it('o último botão envia pet e código para a operação atômica', async () => {
    const pagina = await ler('../src/pages/ativar/pet.astro');

    expect(pagina).toContain("/api/tags/confirmar-cadastro");
    expect(pagina).toContain('codigoPublico: codigo');
    expect(pagina).toContain('Confirmar cadastro do pet');
    expect(pagina).not.toContain('tagUuid:');
  });
});
