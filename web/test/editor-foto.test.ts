import { describe, expect, it } from 'vitest';
import { limitarRecorte } from '../src/lib/editor-foto';

describe('enquadramento da foto', () => {
  it('foto horizontal cobre o quadro sem deslocamento vertical', () => {
    const r = limitarRecorte(1600, 900, 800, 600, 0, 1, 999, 999);
    expect(r.escala).toBeCloseTo(2 / 3);
    expect(r.x).toBeCloseTo((1600 * r.escala - 800) / 2);
    expect(r.y).toBe(0);
  });

  it('foto vertical pode ser movida para cima e para baixo, sem revelar borda', () => {
    const r = limitarRecorte(600, 1200, 800, 600, 0, 1, -999, -999);
    expect(r.x).toBe(0);
    expect(r.y).toBeCloseTo(-(1200 * r.escala - 600) / 2);
  });

  it('girar troca os eixos usados para limitar o movimento', () => {
    const semGiro = limitarRecorte(600, 1200, 800, 600, 0, 1, 999, 999);
    const girada = limitarRecorte(600, 1200, 800, 600, 90, 1, 999, 999);
    expect(semGiro.x).toBe(0);
    expect(semGiro.y).toBeGreaterThan(0);
    expect(girada.x).toBeGreaterThan(0);
    expect(girada.y).toBe(0);
  });

  it('zoom aumenta a area disponivel para reposicionar', () => {
    const normal = limitarRecorte(800, 600, 800, 600, 0, 1, 999, 999);
    const ampliada = limitarRecorte(800, 600, 800, 600, 0, 2, 999, 999);
    expect(normal.x).toBe(0);
    expect(normal.y).toBe(0);
    expect(ampliada.x).toBe(400);
    expect(ampliada.y).toBe(300);
  });
});
