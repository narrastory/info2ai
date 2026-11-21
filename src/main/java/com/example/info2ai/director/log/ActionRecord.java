package com.example.info2ai.director.log;

import com.example.info2ai.api.DirectorRequests.ActionSummary;
import com.example.info2ai.api.DirectorRequests.Changes;
import com.example.info2ai.api.DirectorRequests.RunStatus;
import com.example.info2ai.api.DirectorRequests.Visibility;

import java.util.List;

/** 可跨线程发布的动作记录。运行时撤销数据由服务端另行持有。 */
public record ActionRecord(long actionId, long t, RunStatus status, String intent, String command,
                           Visibility visibility, Changes changes, List<String> notReverted, boolean undoable) {
    public ActionRecord {
        notReverted = List.copyOf(notReverted);
    }

    public ActionSummary summary() {
        return new ActionSummary(actionId, t, status, intent, command, visibility, changes.blocks(),
            changes.spawned().size(), undoable, false);
    }
}
