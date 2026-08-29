import { createServer, type Server } from 'node:http';
import { spawn, type ChildProcess } from 'node:child_process';
import { once } from 'node:events';

/**
 * Sobe uma API dublada e o servidor do site apontado para ela.
 *
 * O site é exercitado como um cliente o exercita: por HTTP, contra o build de
 * produção. Testar o render por dentro não pegaria redirecionamento, cookie nem
 * degradação quando a API cai — que é justamente o que quebra na vida real.
 */

export type Comportamento =
  | { tipo: 'normal' }
  | { tipo: 'fora' }      // conexão recusada
  | { tipo: 'travado' };  // aceita e nunca responde

export class ApiDublada {
  private servidor!: Server;
  private travados: import('node:net').Socket[] = [];
  comportamento: Comportamento = { tipo: 'normal' };
  porta = 0;

  /** Tags conhecidas: código público -> perfil devolvido pela API. */
  perfis = new Map<string, unknown>();

  async subir() {
    this.servidor = createServer((req, res) => {
      if (this.comportamento.tipo === 'travado') {
        // Segura o socket: é a API lenta, não a recusada. Sem isto o teste do
        // teto de espera não teria como acontecer.
        this.travados.push(req.socket);
        return;
      }
      const url = req.url ?? '';
      res.setHeader('Content-Type', 'application/json');

      const publico = url.match(/^\/api\/public\/tags\/([^/?]+)/)?.[1];
      if (publico) {
        if (url.includes('/leituras')) {
          res.statusCode = 202;
          return res.end('{}');
        }
        const perfil = this.perfis.get(publico);
        if (url.includes('/status')) {
          return res.end(JSON.stringify({
            estado: perfil ? 'ATIVO' : 'NAO_ATIVADA', modoPerdido: false,
          }));
        }
        return res.end(JSON.stringify(perfil ?? {
          estado: 'NAO_ATIVADA', pet: null, tutor: null,
          saude: null, contatosEmergencia: [], mensagemPersonalizada: null,
        }));
      }

      // Tudo o que exige sessão responde 401: os testes de rota protegida
      // conferem para onde o site manda quem não está logado.
      res.statusCode = 401;
      res.end('{}');
    });

    this.servidor.listen(this.porta, '127.0.0.1');
    await once(this.servidor, 'listening');
    this.porta = (this.servidor.address() as import('node:net').AddressInfo).port;
  }

  /** Fecha a porta sem esperar conexões: simula a API fora do ar. */
  async derrubar() {
    for (const s of this.travados) s.destroy();
    this.travados = [];
    this.servidor.close();
    await once(this.servidor, 'close').catch(() => {});
  }
}

export class SiteEmExecucao {
  private processo!: ChildProcess;
  porta = 0;
  base = '';

  async subir(urlDaApi: string, porta: number) {
    this.porta = porta;
    this.base = `http://127.0.0.1:${porta}`;

    this.processo = spawn(process.execPath, ['dist/server/entry.mjs'], {
      cwd: new URL('../..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'),
      env: { ...process.env, HOST: '127.0.0.1', PORT: String(porta), API_URL: urlDaApi },
      stdio: 'ignore',
    });

    const limite = Date.now() + 30_000;
    while (Date.now() < limite) {
      try {
        await fetch(`${this.base}/entrar`, { signal: AbortSignal.timeout(1500) });
        return;
      } catch {
        await new Promise((r) => setTimeout(r, 250));
      }
    }
    throw new Error('O servidor do site não subiu a tempo.');
  }

  derrubar() {
    this.processo?.kill();
  }
}

/** Perfil completo, como a API devolve para uma tag ativa. */
export function perfilDeExemplo(opcoes: { perdido?: boolean } = {}) {
  return {
    estado: 'ATIVO',
    modoPerdido: opcoes.perdido ?? false,
    pet: {
      nome: 'Thor', especie: 'CACHORRO', raca: 'Golden',
      cidade: 'Campinas', estado: 'SP', numeroMicrochip: null, foto: null,
    },
    tutor: {
      nome: 'Bruno',
      telefoneExibicao: '(11) 98888-7777', telefoneE164: '+5511988887777',
      whatsappExibicao: '(11) 98888-7777', whatsappE164: '5511988887777',
    },
    saude: { alergias: 'Alergia a frango', medicacaoContinua: null, condicoes: null,
             cuidadosEspeciais: null, veterinarioNome: null,
             veterinarioTelefoneExibicao: null, veterinarioTelefoneE164: null, clinica: null },
    contatosEmergencia: [
      { nome: 'Marina', parentesco: 'Irmã', telefoneExibicao: '(19) 99777-1234',
        telefoneE164: '+5519997771234' },
    ],
    mensagemPersonalizada: 'Ele é medroso, chame com calma.',
  };
}
