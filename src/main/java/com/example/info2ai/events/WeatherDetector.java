package com.example.info2ai.events;
public final class WeatherDetector implements EventDetector {
    private long lastBedNight=Long.MIN_VALUE,lastPhantomNight=Long.MIN_VALUE;
    public String kind() {
        return "weather";
    }
    public void tick(EventContext c) {
        var n=c.current();
        var p=c.previous();
        if(n.env()==null)return;
        var a=n.env();
        if(p!=null&&p.env()!=null) {
            var b=p.env();
            if(a.raining()!=b.raining()||a.thundering()!=b.thundering())c.emit("weather_changed","info",
            a.thundering()?"打雷了":a.raining()?"开始下雨了":"雨停了","rain",a.raining(),"thunder",a.thundering());
            if(b.dayTime()<13000&&a.dayTime()>=13000)c.emit("night_fell","info","天黑了","day_time",a.dayTime());
            if(b.dayTime()>=23000&&a.dayTime()<b.dayTime()&&a.worldTime()>b.worldTime())c.emit("dawn","info","第 "+(a.worldTime()/24000+1)+" 天开始","day",
            (a.worldTime()/24000+1));
        }
        boolean night=a.dayTime()>=13000&&a.dayTime()<23000;
        long day=(a.worldTime()/24000+1);
        if(night&&n.inv()!=null&&Boolean.FALSE.equals(n.inv().hasBed())&&Boolean.FALSE.equals(a.bedNearby())&&lastBedNight!=day) {
            lastBedNight=day;
            c.emit("no_bed_at_night","warn","夜晚，背包和已检查附近区域没有床","day",day);
        }
        if(night&&n.vital()!=null&&n.vital().restTicks()>=72000&&lastPhantomNight!=day) {
            lastPhantomNight=day;
            c.emit("phantom_risk","warn","已连续三个游戏日没有睡眠记录","rest_ticks",n.vital().restTicks());
        }
    }
}
