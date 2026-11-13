package cl.mitv.app;

/** Decide el estado de salud de una URL a partir de su respuesta HTTP. Funciones puras, sin E/S. */
final class HealthPolicy {
 static final int DOWN_AFTER_FAILURES = 3;
 static final int MAX_FAILURES = 9;

 /** Códigos que prueban que el host responde pero no permiten juzgar el recurso. */
 private static boolean inconclusive(int code) {
  return code == 401 || code == 403 || code == 429 || code == 451;
 }

 static String status(int code, int failures) { return status(code, failures, true); }

 static String status(int code, int failures, boolean manifestOk) {
  int next = Math.min(failures + 1, MAX_FAILURES);
  if (code >= 200 && code < 400) {
   if (!manifestOk) return next >= DOWN_AFTER_FAILURES ? "DOWN" : "UNKNOWN";
   return "UP";
  }
  if (inconclusive(code) || code == 405) return "UNKNOWN";
  if (code == 404 || code == 410) return "DOWN";
  return next >= DOWN_AFTER_FAILURES ? "DOWN" : "UNKNOWN";
 }

 static int failures(int code, int previous) { return failures(code, previous, true); }

 static int failures(int code, int previous, boolean manifestOk) {
  int next = Math.min(previous + 1, MAX_FAILURES);
  if (code >= 200 && code < 400) return manifestOk ? 0 : next;
  if (inconclusive(code) || code == 405) return 0;
  return next;
 }
}
