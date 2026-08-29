import { describe, it, expect } from 'vitest';
import {
  ALFABETO, normalizarCodigo, caractereInvalido,
  mesclarCookies, temCookie, redirecionarCom, verDepois,
} from '../src/lib/api';
import { quando, dia, texto, marcado } from '../src/lib/painel';

/**
 * As decisões que o front toma sozinho, sem servidor no meio.
 *
 * São poucas e todas com consequência: um código aceito errado manda a pessoa
 * gastar tentativa à toa, e um cookie perdido no repasse derruba a sessão logo
 * depois de criar a conta — que foi um bug real deste projeto.
 */
describe('código da tag', () => {
  it('o alfabeto não tem os caracteres que se confundem ao ler', () => {
    // 0/O, 1/I/L e U ficam de fora porque o código é lido de uma peça
    // pequena, muitas vezes gravada a laser.
    for (const c of '01OILU') {
      expect(ALFABETO, `"${c}" não pode estar no alfabeto`).not.toContain(c);
    }
    expect(ALFABETO).toHaveLength(30);
  });

  it('normaliza o que a pessoa realmente digita', () => {
    expect(normalizarCodigo(' hw9nnwq5jb ')).toBe('HW9NNWQ5JB');
    expect(normalizarCodigo('HW9N-NWQ5-JB')).toBe('HW9NNWQ5JB');
    expect(normalizarCodigo('HW9N NWQ5 JB')).toBe('HW9NNWQ5JB');
    expect(normalizarCodigo(null)).toBe('');
    expect(normalizarCodigo(undefined)).toBe('');
  });

  it('aponta o primeiro caractere fora do alfabeto', () => {
    expect(caractereInvalido('HW9NNWQ5JB')).toBeNull();
    expect(caractereInvalido('HW9NNWQ5J0')).toBe('0');
    // O primeiro, não qualquer um: a tela sugere a letra parecida, e sugerir
    // sobre o segundo erro confundiria quem está conferindo o primeiro.
    expect(caractereInvalido('LIVRETAG12')).toBe('L');
  });
});

describe('repasse de cookies', () => {
  it('o Set-Cookie novo vence o valor antigo do mesmo nome', () => {
    // Renovar a sessão no meio de um render precisa valer para as chamadas
    // seguintes; senão a primeira renova e as outras seguem com o token velho.
    const mesclado = mesclarCookies('cp_sessao=velho; outro=x', ['cp_sessao=novo; Path=/; HttpOnly']);
    expect(mesclado).toContain('cp_sessao=novo');
    expect(mesclado).not.toContain('velho');
    expect(mesclado).toContain('outro=x');
  });

  it('acrescenta cookie que ainda não existia', () => {
    expect(mesclarCookies('a=1', ['b=2; Path=/'])).toBe('a=1; b=2');
  });

  it('aguenta cabeçalho vazio ou ausente', () => {
    expect(mesclarCookies(null, ['a=1; Path=/'])).toBe('a=1');
    expect(mesclarCookies('', [])).toBe('');
  });

  it('temCookie não confunde nome parecido nem valor vazio', () => {
    expect(temCookie('cp_sessao=abc', 'cp_sessao')).toBe(true);
    expect(temCookie('cp_sessao_extra=abc', 'cp_sessao')).toBe(false);
    expect(temCookie('cp_sessao=', 'cp_sessao')).toBe(false);
    expect(temCookie(null, 'cp_sessao')).toBe(false);
  });
});

describe('redirecionamentos', () => {
  it('redirecionarCom leva os cookies junto, em 303', () => {
    // Astro.redirect() descarta os cabeçalhos e a sessão se perdia: a pessoa
    // criava a conta e voltava para a tela de login.
    const r = redirecionarCom('/app', ['cp_sessao=a; Path=/', 'cp_refresh=b; Path=/']);
    expect(r.status).toBe(303);
    expect(r.headers.get('location')).toBe('/app');
    expect(r.headers.getSetCookie()).toHaveLength(2);
  });

  it('verDepois é 303, não 302', () => {
    // 303 força a próxima requisição a ser GET: tira o formulário do histórico
    // e um F5 não reenvia o cadastro.
    expect(verDepois('/app/conta?salvo=1').status).toBe(303);
  });
});

describe('formulários e datas', () => {
  it('campo vazio vira null, para a API não gravar string vazia', () => {
    const d = new FormData();
    d.set('cheio', '  Thor  ');
    d.set('vazio', '   ');
    expect(texto(d, 'cheio')).toBe('Thor');
    expect(texto(d, 'vazio')).toBeNull();
    expect(texto(d, 'inexistente')).toBeNull();
  });

  it('checkbox ausente é false', () => {
    const d = new FormData();
    d.set('ligado', 'on');
    expect(marcado(d, 'ligado')).toBe(true);
    expect(marcado(d, 'desligado')).toBe(false);
  });

  it('data sai no fuso de São Paulo', () => {
    // 14:05 UTC = 11:05 em São Paulo. Sem o fuso fixo, o servidor em outra
    // região mostraria ao tutor uma hora que não foi a da leitura.
    expect(quando('2026-08-29T14:05:00Z')).toContain('11:05');
    expect(dia('2026-08-29T14:05:00Z')).toContain('2026');
  });

  it('data ausente não quebra a tela', () => {
    expect(quando(null)).toBe('—');
    expect(dia(undefined)).toBe('—');
  });
});
