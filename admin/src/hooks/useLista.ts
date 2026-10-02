import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';

/**
 * Carrega uma lista do backend (GET {@code url}) e a mantém no estado — o bloco que as telas do
 * painel repetiam uma a uma.
 *
 * <ul>
 *   <li>A 1ª carga não liga {@code loading}: ele já nasce true. setState síncrono dentro de um
 *       efeito dispara render em cascata (react-hooks/set-state-in-effect); por isso os setState
 *       ficam em callbacks de promessa, nunca num try/catch no corpo (o analisador trata o catch
 *       como se pudesse rodar de forma síncrona).</li>
 *   <li>{@code recarregar} (depois de uma ação) volta ao estado de carregamento.</li>
 *   <li>{@code carregadoEm} é o "agora" da última carga: telas que mostram idades ("há 5h") o
 *       usam em vez de {@code Date.now()} na renderização (react-hooks/purity).</li>
 *   <li>Erro de carga vira lista vazia + {@code erro}, para a tela poder dizer que falhou em vez
 *       de mostrar "nada aqui" como se fosse boa notícia.</li>
 * </ul>
 */
export function useLista<T>(url: string) {
  const [itens, setItens] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState('');
  const [carregadoEm, setCarregadoEm] = useState(() => Date.now());

  const carregar = useCallback(() =>
    api.get<T[]>(url)
      .then(d => setItens(Array.isArray(d) ? d : []))
      .catch((e: unknown) => {
        setErro(e instanceof Error ? e.message : 'Erro ao carregar a lista.');
        setItens([]);
      })
      .finally(() => {
        setCarregadoEm(Date.now());
        setLoading(false);
      }),
  [url]);

  function recarregar() {
    setLoading(true);
    setErro('');
    return carregar();
  }

  useEffect(() => { carregar(); }, [carregar]);

  return { itens, setItens, loading, erro, carregadoEm, recarregar };
}
