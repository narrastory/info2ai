package com.example.info2ai.events;
public final class SessionDetector implements EventDetector {
    private boolean afk;
    public String kind() {
        return "session";
    }
    public void tick(EventContext c) {
        var n=c.current();
        if(n.client()==null||n.client().idleTicks()==null)return;
        boolean now=n.client().idleTicks()>1800;
        if(now&&!afk)c.emit("afk_started","info",n.client().idleTicks()/20+" 秒没有输入或移动","idle_s",n.client().idleTicks()/20);
        if(!now&&afk)c.emit("afk_ended","info","恢复了活动","idle_s",n.client().idleTicks()/20);
        afk=now;
    }
}
