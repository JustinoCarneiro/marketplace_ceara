/** Uma sessão antiga nunca pode registrar um dispositivo após a troca de conta. */
export function createPushSession(
  register: (token: string) => Promise<boolean>,
  unregister: (token: string) => Promise<boolean>,
) {
  let closed = false;
  let latest: string | undefined;
  let serial = Promise.resolve();
  return {
    update(token: string): Promise<void> {
      serial = serial.then(async () => {
        if (closed) return;
        if (latest && latest !== token && !await unregister(latest)) return;
        latest = token;
        const ok = await register(token);
        if (closed && ok) await unregister(token);
      }).catch(() => {});
      return serial;
    },
    async close(): Promise<void> {
      closed = true;
      await serial;
      if (latest) await unregister(latest);
    },
  };
}
