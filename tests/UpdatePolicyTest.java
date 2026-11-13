package cl.mitv.app;

public final class UpdatePolicyTest {
    public static void main(String[] args) {
        String good = "https://github.com/yairul10/Iptv/releases/download/v0.3.0-build.10/Mi-TV.apk";
        String signer = "a".repeat(64), other = "b".repeat(64), pkg = "cl.mitv.app.preview";
        if(!UpdatePolicy.compatible(9,10,pkg,pkg,signer,signer,good)) throw new AssertionError("Compatible upgrade rejected");
        if(UpdatePolicy.compatible(10,10,pkg,pkg,signer,signer,good)) throw new AssertionError("Same version offered");
        if(UpdatePolicy.compatible(11,10,pkg,pkg,signer,signer,good)) throw new AssertionError("Downgrade offered");
        if(UpdatePolicy.compatible(9,10,pkg,pkg,signer,other,good)) throw new AssertionError("Changed signature offered");
        if(UpdatePolicy.compatible(9,10,pkg,"cl.mitv.app",signer,signer,good)) throw new AssertionError("Changed package offered");
        String[] bad = {
            good.replace("https:","http:"), good.replace("github.com","github.com.evil.example"),
            good.replace("yairul10/Iptv", "someone/another-repo"), good + "?redirect=https://evil.example/",
            good.replace("Mi-TV.apk","anything.apk"), good.replace("/v0.3.0-build.10/","/../"),
            good.replace("https://github.com", "https://user@github.com"), "javascript:alert(1)"
        };
        for(String value : bad) if(UpdatePolicy.trustedApk(value)) throw new AssertionError("Untrusted APK accepted");
        System.out.println("Updates: version, package, signer and download-origin checks passed");
    }
}
