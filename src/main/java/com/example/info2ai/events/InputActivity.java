package com.example.info2ai.events;
/** 键、字符、按钮、滚轮、光标五条回调均被实见后才允许证明无输入。 */
public final class InputActivity {
    private int confirmed;
    private long last;
    public void input(int channel,long tick) {
        if(channel<0||channel>=5)throw new IllegalArgumentException("输入通道越界");
        confirmed|=1<<channel;
        last=tick;
    }
    public Long idleTicks(long tick) {
        return confirmed==31?Math.max(0,tick-last):null;
    }
    public void reset() {
        confirmed=0;
        last=0;
    }
}
