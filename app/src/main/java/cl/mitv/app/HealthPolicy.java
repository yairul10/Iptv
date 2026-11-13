package cl.mitv.app;
final class HealthPolicy {
 static String status(int code,int failures) {
  if(code>=200&&code<400) return "UP";
  if(code==401||code==403||code==429) return "UNKNOWN";
  return code==404||code==410||failures>=3?"DOWN":"UNKNOWN";
 }
 static int failures(int code,int previous) { return code>=200&&code<400||code==401||code==403||code==429?0:previous+1; }
}
