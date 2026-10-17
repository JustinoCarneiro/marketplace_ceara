/** Todas as mutações do mesmo celular passam por esta fila, inclusive os botões do perfil. */
let serial: Promise<unknown> = Promise.resolve();
export function serializePush<T>(work: () => Promise<T>): Promise<T> {
  const next = serial.then(work, work);
  serial = next.catch(() => {});
  return next;
}
