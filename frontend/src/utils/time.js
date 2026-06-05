export function minuteToTime(min) {
  const h = Math.floor(min / 60).toString().padStart(2, '0');
  const m = (min % 60).toString().padStart(2, '0');

  return `${h}:${m}`;
}

export function timeToMinute(t) {
  const [h, m] = (t || '09:00').split(':').map(Number);

  return h * 60 + m;
}
