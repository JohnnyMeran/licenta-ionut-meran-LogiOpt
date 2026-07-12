// Tema aplicației. Dark rămâne implicită; light există pentru tipar (capturile din lucrare).
// Culorile CSS vin din variabilele definite în styles.css; aici stau doar cele pe care Recharts
// le cere ca valori JS (axe, grilă, tooltip), pentru că nu poate citi variabile CSS.
const CHART = {
  dark: {
    axis: '#9fb0c9',
    grid: 'rgba(148, 163, 184, 0.16)',
    axisLine: '#334155',
    tooltipBg: '#0b1424',
    tooltipBorder: '1px solid rgba(148, 163, 184, 0.25)',
    tooltipText: '#e5e7eb',
  },
  light: {
    axis: '#475569',
    grid: 'rgba(15, 23, 42, 0.12)',
    axisLine: '#cbd5e1',
    tooltipBg: '#ffffff',
    tooltipBorder: '1px solid rgba(15, 23, 42, 0.15)',
    tooltipText: '#0f172a',
  },
};

export const chartTheme = (theme) => CHART[theme] || CHART.dark;

export const STORAGE_KEY = 'logioptTheme';

export function initialTheme() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // localStorage indisponibil (mod privat): se folosește tema implicită
  }
  return 'dark';
}

export function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // fără persistență, tema rămâne doar pentru sesiunea curentă
  }
}
