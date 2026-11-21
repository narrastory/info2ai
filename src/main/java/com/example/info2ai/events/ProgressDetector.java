package com.example.info2ai.events;
import static com.example.info2ai.events.Observation.*;
import com.example.info2ai.util.NameZh;
public final class ProgressDetector implements EventDetector {
    private Integer deathXp;
    public String kind() {
        return "progress";
    }
    public void tick(EventContext c) {
        var n=c.current();
        var p=c.previous();
        for(var a:n.actions())if(a.confirmed()) {
            if(a.type()==ActionType.ADVANCEMENT) {
                String name=a.details()==null?null:com.example.info2ai.util.JsonUtil.string(a.details().toJson(),"name");
                c.action("advancement","info","解锁进度「"+(name==null?a.id():name)+"」",a,"id",a.id(),"name",name);
            }
            if(a.type()==ActionType.RECIPE)c.action("recipe_unlocked","info","学会了 "+a.count()+" 个新配方",a,"n",a.count());
            if(a.type()==ActionType.CRAFT)c.action("item_crafted","info","做出了"+NameZh.get(a.id())+"（"+a.count()+" 个）",a,"item",a.id(),"items",a.count());
        }
        if(p==null||p.vital()==null||n.vital()==null)return;
        if(n.vital().xpLevel()>p.vital().xpLevel())c.emit("level_up","info","升到 "+n.vital().xpLevel()+" 级","level",n.vital().xpLevel());
        if(n.vital().dead()&&!p.vital().dead())deathXp=p.vital().xpTotal();
        if(deathXp!=null&&!n.vital().dead()&&p.vital().dead()) {
            if(deathXp>n.vital().xpTotal())c.emit("xp_lost","warn","死亡后经验总量减少 "+(deathXp-n.vital().xpTotal()),"n",deathXp-n.vital().xpTotal());
            deathXp=null;
        }
    }
}
