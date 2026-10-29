// Applies the saved theme before the first paint. It is a separate file because the production
// Content-Security-Policy (script-src 'self') blocks inline scripts.
try {
  const theme = localStorage.getItem('gymplanner-theme');
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme;
} catch {
  /* Browser storage is optional. */
}
