package com.example.info2ai.history;

import com.example.info2ai.model.*;

/** Immutable committed values, ordered under SnapshotStore's publication lock. */
public record Commit(long order,long elapsedNanos,long epoch,Snapshot snapshot,GameEvent event) {
    public Commit(long order,long elapsedNanos,Snapshot snapshot,GameEvent event) {this(order,elapsedNanos,0,snapshot,event);}
    public String worldId() {
        if(snapshot!=null)return snapshot.session()==null?null:snapshot.session().worldId();
        var data=event==null||event.data()==null?null:event.data().toJson();
        return data==null||!data.has("world_id")||data.get("world_id").isJsonNull()?null:data.get("world_id").getAsString();
    }
}
