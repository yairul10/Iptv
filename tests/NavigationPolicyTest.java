package cl.mitv.app;

public final class NavigationPolicyTest {
    public static void main(String[] args) {
        String[] allowed = {
            "https://www.tvn.cl/en-vivo", "https://rudo.video/live/c9",
            "https://TVMAS.TV/page/en-vivo", "https://www.13.cl/en-vivo",
            "https://www.t13.cl:443/en-vivo", "https://www.tvu.cl/"
        };
        String[] blocked = {
            "http://www.tvn.cl/", "https://evil-tvn.cl/", "https://tvn.cl.evil.example/",
            "https://evil.example/?next=https://www.tvn.cl/", "https://tvn.cl@evil.example/",
            "https://user@www.tvn.cl/", "https://www.tvn.cl:8443/",
            "javascript:alert(1)", "file:///sdcard/test.html", "intent://player/",
            "https://", "not a URL", "https://www.tvn.cl\\@evil.example/"
        };
        for(String url : allowed) if(!NavigationPolicy.isOfficialHttps(url)) throw new AssertionError("Blocked official URL: " + url);
        for(String url : blocked) if(NavigationPolicy.isOfficialHttps(url)) throw new AssertionError("Allowed unexpected URL: " + url);
        if(!NavigationPolicy.isChannelHttps("https://player.newchannel.example/live", "https://www.newchannel.example/live", "https://www.newchannel.example/")) throw new AssertionError("New channel domain rejected");
        if(NavigationPolicy.isChannelHttps("https://newchannel.example.evil.test/", "https://www.newchannel.example/live", "https://www.newchannel.example/")) throw new AssertionError("Spoofed channel domain accepted");
        for(String url : new String[]{"https://127.0.0.1/", "https://localhost/", "file:///test", "http://www.tvn.cl/", "https://192.168.1.1/", "https://test.local/"})
            if(NavigationPolicy.isPublicHttps(url)) throw new AssertionError("Invalid catalogue URL accepted");
        System.out.println("Navigation policy: " + (allowed.length + blocked.length) + " cases passed");
    }
}
