package com.example.info2ai.events;
import java.util.function.IntFunction;
/** 有限增量扫描：任意格未知或尚未扫完，都不能证明不存在。 */
public final class PresenceScan {
    private final int size;
    private int cursor;
    private boolean unknown,found;
    public PresenceScan(int size) {
        if(size<1)throw new IllegalArgumentException("扫描不能为空");
        this.size=size;
    }
    public void step(int budget,IntFunction<Boolean> cell) {
        for(int used=0;used<budget&&cursor<size&&!found;used++) {
            Boolean value=cell.apply(cursor++);
            if(value==null)unknown=true;
            else if(value)found=true;
        }
    }
    public Boolean result() {
        return found?Boolean.TRUE:cursor<size||unknown?null:Boolean.FALSE;
    }
    public boolean complete() {
        return found||cursor==size;
    }
    public void reset() {
        cursor=0;
        unknown=false;
        found=false;
    }
}
