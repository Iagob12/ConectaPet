const LARGURA_EDITOR = 800;
const ALTURA_EDITOR = 600;
const LARGURA_SAIDA = 1200;
const ALTURA_SAIDA = 900;

export interface LimitesDoRecorte {
  escala: number;
  x: number;
  y: number;
}

/**
 * Calcula escala e deslocamento sem deixar bordas vazias no recorte.
 * Exportada porque esta e a parte que mais facilmente quebra ao combinar
 * foto vertical, giro e zoom — e pode ser verificada sem abrir um navegador.
 */
export function limitarRecorte(
  larguraImagem: number,
  alturaImagem: number,
  larguraQuadro: number,
  alturaQuadro: number,
  rotacao: number,
  zoom: number,
  x: number,
  y: number,
): LimitesDoRecorte {
  const girada = Math.abs(rotacao % 180) === 90;
  const larguraVisual = girada ? alturaImagem : larguraImagem;
  const alturaVisual = girada ? larguraImagem : alturaImagem;
  const escala = Math.max(larguraQuadro / larguraVisual, alturaQuadro / alturaVisual) * zoom;
  const limiteX = Math.max(0, (larguraVisual * escala - larguraQuadro) / 2);
  const limiteY = Math.max(0, (alturaVisual * escala - alturaQuadro) / 2);

  return {
    escala,
    x: limiteX === 0 ? 0 : Math.max(-limiteX, Math.min(limiteX, x)),
    y: limiteY === 0 ? 0 : Math.max(-limiteY, Math.min(limiteY, y)),
  };
}

function elemento<T extends HTMLElement>(id: string): T | null {
  return document.getElementById(id) as T | null;
}

/** Liga o editor ao formulario da pagina do pet. Sem JavaScript, o formulario
 * continua enviando a imagem original e o backend preserva o fluxo antigo. */
export function iniciarEditorFoto(): void {
  const formulario = elemento<HTMLFormElement>('form-foto');
  const arquivo = elemento<HTMLInputElement>('arquivo');
  const editor = elemento<HTMLElement>('editor');
  const quadro = elemento<HTMLElement>('quadro');
  const tela = elemento<HTMLCanvasElement>('tela');
  const controleZoom = elemento<HTMLInputElement>('zoom');
  const girar = elemento<HTMLButtonElement>('girar');
  const recomecar = elemento<HTMLButtonElement>('recomecar');
  const estado = elemento<HTMLElement>('estado-editor');
  const enviar = elemento<HTMLButtonElement>('enviar-foto');

  if (!formulario || !arquivo || !editor || !quadro || !tela || !controleZoom
      || !girar || !recomecar || !estado || !enviar) {
    return;
  }

  const contexto = tela.getContext('2d');
  if (!contexto) return;

  tela.width = LARGURA_EDITOR;
  tela.height = ALTURA_EDITOR;

  let imagem: HTMLImageElement | null = null;
  let urlDaImagem: string | null = null;
  let rotacao = 0;
  let zoom = 1;
  let deslocamentoX = 0;
  let deslocamentoY = 0;
  let preparando = false;

  const anunciar = (mensagem: string) => {
    estado.textContent = mensagem;
  };

  const limparUrl = () => {
    if (urlDaImagem) URL.revokeObjectURL(urlDaImagem);
    urlDaImagem = null;
  };

  const desenhar = (
    ctx: CanvasRenderingContext2D,
    largura: number,
    altura: number,
    atualizarDeslocamento: boolean,
  ) => {
    if (!imagem) return;

    const fator = largura / LARGURA_EDITOR;
    const limites = limitarRecorte(
      imagem.naturalWidth,
      imagem.naturalHeight,
      largura,
      altura,
      rotacao,
      zoom,
      deslocamentoX * fator,
      deslocamentoY * fator,
    );

    if (atualizarDeslocamento) {
      deslocamentoX = limites.x / fator;
      deslocamentoY = limites.y / fator;
    }

    // PNG transparente vira branco, nunca preto. O backend faz o mesmo ao
    // reencodar, entao a previa e a foto salva continuam iguais.
    ctx.save();
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, largura, altura);
    ctx.translate(largura / 2 + limites.x, altura / 2 + limites.y);
    ctx.rotate(rotacao * Math.PI / 180);
    ctx.scale(limites.escala, limites.escala);
    ctx.drawImage(imagem, -imagem.naturalWidth / 2, -imagem.naturalHeight / 2);
    ctx.restore();
  };

  const redesenhar = () => desenhar(contexto, LARGURA_EDITOR, ALTURA_EDITOR, true);

  const centralizar = (voltarRotacao = false) => {
    deslocamentoX = 0;
    deslocamentoY = 0;
    zoom = 1;
    controleZoom.value = '100';
    if (voltarRotacao) rotacao = 0;
    redesenhar();
  };

  arquivo.addEventListener('change', () => {
    limparUrl();
    imagem = null;
    editor.hidden = true;
    delete editor.dataset.ativo;
    arquivo.setCustomValidity('');
    anunciar('');

    const escolhido = arquivo.files?.[0];
    if (!escolhido) return;

    arquivo.setCustomValidity('Aguarde a foto carregar.');
    urlDaImagem = URL.createObjectURL(escolhido);
    const novaImagem = new Image();
    novaImagem.onload = () => {
      if (novaImagem.naturalWidth * novaImagem.naturalHeight > 40_000_000) {
        arquivo.setCustomValidity('Esta imagem tem resolução alta demais. Escolha uma foto menor.');
        arquivo.reportValidity();
        anunciar('Esta imagem tem resolução alta demais. Escolha uma foto menor.');
        limparUrl();
        return;
      }

      imagem = novaImagem;
      arquivo.setCustomValidity('');
      rotacao = 0;
      editor.hidden = false;
      editor.dataset.ativo = '1';
      centralizar(true);
      anunciar('Foto carregada. Ajuste o enquadramento e depois envie.');
    };
    novaImagem.onerror = () => {
      arquivo.setCustomValidity('Não consegui abrir esta imagem. Escolha um arquivo JPEG, PNG ou WebP.');
      arquivo.reportValidity();
      anunciar('Não consegui abrir esta imagem. Escolha um arquivo JPEG, PNG ou WebP.');
      limparUrl();
    };
    novaImagem.src = urlDaImagem;
  });

  controleZoom.addEventListener('input', () => {
    zoom = Number(controleZoom.value) / 100;
    redesenhar();
  });

  girar.addEventListener('click', () => {
    rotacao = (rotacao + 90) % 360;
    deslocamentoX = 0;
    deslocamentoY = 0;
    redesenhar();
    anunciar('Foto girada.');
  });

  recomecar.addEventListener('click', () => {
    centralizar(true);
    anunciar('Enquadramento restaurado.');
  });

  let ponteiro: number | null = null;
  let ultimoX = 0;
  let ultimoY = 0;

  quadro.addEventListener('pointerdown', (evento) => {
    if (!imagem || ponteiro !== null) return;
    ponteiro = evento.pointerId;
    ultimoX = evento.clientX;
    ultimoY = evento.clientY;
    quadro.setPointerCapture(evento.pointerId);
  });

  quadro.addEventListener('pointermove', (evento) => {
    if (!imagem || evento.pointerId !== ponteiro) return;
    const retangulo = quadro.getBoundingClientRect();
    deslocamentoX += (evento.clientX - ultimoX) * LARGURA_EDITOR / retangulo.width;
    deslocamentoY += (evento.clientY - ultimoY) * ALTURA_EDITOR / retangulo.height;
    ultimoX = evento.clientX;
    ultimoY = evento.clientY;
    redesenhar();
  });

  const soltarPonteiro = (evento: PointerEvent) => {
    if (evento.pointerId !== ponteiro) return;
    ponteiro = null;
    if (quadro.hasPointerCapture(evento.pointerId)) quadro.releasePointerCapture(evento.pointerId);
  };
  quadro.addEventListener('pointerup', soltarPonteiro);
  quadro.addEventListener('pointercancel', soltarPonteiro);

  quadro.addEventListener('keydown', (evento) => {
    if (!imagem) return;
    const passo = evento.shiftKey ? 24 : 8;
    let tratado = true;

    if (evento.key === 'ArrowLeft') deslocamentoX -= passo;
    else if (evento.key === 'ArrowRight') deslocamentoX += passo;
    else if (evento.key === 'ArrowUp') deslocamentoY -= passo;
    else if (evento.key === 'ArrowDown') deslocamentoY += passo;
    else if (evento.key === '+' || evento.key === '=') {
      controleZoom.value = String(Math.min(400, Number(controleZoom.value) + 10));
      zoom = Number(controleZoom.value) / 100;
    } else if (evento.key === '-' || evento.key === '_') {
      controleZoom.value = String(Math.max(100, Number(controleZoom.value) - 10));
      zoom = Number(controleZoom.value) / 100;
    } else if (evento.key.toLowerCase() === 'r') {
      rotacao = (rotacao + 90) % 360;
      deslocamentoX = 0;
      deslocamentoY = 0;
    } else tratado = false;

    if (tratado) {
      evento.preventDefault();
      redesenhar();
    }
  });

  const criarFotoEditada = () => new Promise<Blob>((resolver, rejeitar) => {
    const saida = document.createElement('canvas');
    saida.width = LARGURA_SAIDA;
    saida.height = ALTURA_SAIDA;
    const ctx = saida.getContext('2d');
    if (!ctx) {
      rejeitar(new Error('Canvas indisponível'));
      return;
    }
    desenhar(ctx, LARGURA_SAIDA, ALTURA_SAIDA, false);
    saida.toBlob(
      (blob) => blob ? resolver(blob) : rejeitar(new Error('Não foi possível gerar a foto')),
      'image/jpeg',
      0.9,
    );
  });

  formulario.addEventListener('submit', async (evento) => {
    if (!imagem || preparando) return;
    evento.preventDefault();
    preparando = true;
    formulario.setAttribute('aria-busy', 'true');
    enviar.disabled = true;
    enviar.textContent = 'Preparando foto…';
    anunciar('Preparando a foto para enviar.');

    try {
      const blob = await criarFotoEditada();
      const original = arquivo.files?.[0];
      const nomeBase = (original?.name ?? 'foto').replace(/\.[^.]+$/, '');
      const editada = new File([blob], `${nomeBase}-editada.jpg`, {
        type: 'image/jpeg',
        lastModified: Date.now(),
      });
      const transferencia = new DataTransfer();
      transferencia.items.add(editada);
      arquivo.files = transferencia.files;

      if (!arquivo.files || arquivo.files.length !== 1) {
        throw new Error('O navegador não permitiu substituir a foto');
      }

      // Envio nativo: preserva validacao, cookies e os redirects ja usados
      // pela pagina, sem criar um segundo caminho de upload em JavaScript.
      HTMLFormElement.prototype.submit.call(formulario);
    } catch {
      preparando = false;
      formulario.removeAttribute('aria-busy');
      enviar.disabled = false;
      enviar.textContent = 'Enviar foto';
      anunciar('Não consegui preparar o recorte. Tente escolher a foto novamente.');
    }
  });

  window.addEventListener('pagehide', limparUrl, { once: true });
}
