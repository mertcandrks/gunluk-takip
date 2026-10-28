export const SYNC_PORT = 47832;

export function buildSyncQrPayload(address, pairingCode, serviceId = '') {
  const host = String(address || '').trim();
  const code = String(pairingCode || '').trim();
  if (!host || host.length > 253 || /[\s/\\]/.test(host)) throw new Error('Geçersiz PC adresi');
  if (!/^\d{8}$/.test(code)) throw new Error('Geçersiz eşleştirme kodu');
  const query = new URLSearchParams({ v: '1', host, port: String(SYNC_PORT), code });
  if (serviceId) {
    if (!/^[a-f0-9]{32}$/.test(serviceId)) throw new Error('Geçersiz PC kimliği');
    query.set('id', serviceId);
  }
  return `gunluktakip://sync?${query.toString()}`;
}
