export function readParts(raw) {
  const parts = JSON.parse(raw || '[]');
  if (!Array.isArray(parts) || parts.length > 100 || new Set(parts.map(part => part.id)).size !== parts.length || parts.some(part =>
    typeof part.id !== 'string' || !part.id.trim() || part.id.length > 80 || typeof part.title !== 'string' ||
    !part.title.trim() || part.title.length > 200 || !Number.isInteger(part.progress) || part.progress < 0 || part.progress > 100)) {
    throw new Error('Geçersiz alt görevler');
  }
  return parts;
}

export const progressPercent = parts => parts.length ? Math.floor(parts.reduce((sum, part) => sum + part.progress, 0) / parts.length) : 0;

export function appendPendingPart(parts, title) {
  if (!title.trim()) return parts;
  if (parts.length >= 100 || title.length > 200) throw new Error('En fazla 100 alt görev; başlık en fazla 200 karakter olabilir.');
  return [...parts, {id:crypto.randomUUID(), title:title.trim(), progress:0}];
}
