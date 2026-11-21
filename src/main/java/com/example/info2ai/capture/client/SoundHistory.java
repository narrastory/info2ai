package com.example.info2ai.capture.client;
import java.util.*;

/** 有界的纯值声音历史。未收到 hook 时返回未知，不把未知伪装为安静。 */
public final class SoundHistory {
    public record Sound(String id, double x, double y, double z, long at) { }
    private final ArrayDeque<Sound> sounds = new ArrayDeque<>();
    private boolean confirmed;
    public synchronized void observe(String id, boolean music, boolean relative, double x, double y, double z, long now) {
        confirmed = true;
        if (music || relative || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        for (var sound : sounds) if (sound.id().equals(id) && now - sound.at() < 500) return;
        if (sounds.size() == 128) sounds.removeFirst();
        sounds.addLast(new Sound(id,x,y,z,now));
    }
    public synchronized List<Sound> recent(double x, double y, double z, long now) {
        if (!confirmed) return null;
        while (!sounds.isEmpty() && now - sounds.getFirst().at() > 10_000) sounds.removeFirst();
        List<Sound> result = new ArrayList<>();
        var iterator = sounds.descendingIterator();
        while (iterator.hasNext() && result.size() < 12) {
            var sound = iterator.next();
            double dx = sound.x()-x, dy = sound.y()-y, dz = sound.z()-z;
            if (dx*dx+dy*dy+dz*dz <= 32*32) result.add(sound);
        }
        return List.copyOf(result);
    }
    public synchronized void clear() { sounds.clear(); confirmed = false; }
}
