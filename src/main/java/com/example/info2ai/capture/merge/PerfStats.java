package com.example.info2ai.capture.merge;

import java.util.Arrays;

/** 精确的300次滚动统计：读端只拿最后完成的不可变视图，不参与生产者锁。 */
public final class PerfStats {
    public record Sample(int samples,double avgMs,double maxMs) { }

    // 在任何实例能被HTTP读取前显式构造，避免首次snapshot在共享监视器中加载Sample。
    private static final Sample EMPTY=new Sample(0,0,0);
    private final Object producerLock=new Object();
    private final long[] values=new long[300];
    private int next,count;
    private long sum,maximum;
    private volatile Sample completed;

    public PerfStats() {
        completed=EMPTY;
    }

    public void record(long microseconds) {
        synchronized(producerLock) {
            long value=Math.max(0,microseconds);
            long evicted=count==values.length?values[next]:-1;
            if(count==values.length)sum-=evicted;
            else count++;
            values[next]=value;
            sum+=value;
            next=(next+1)%values.length;
            if(value>=maximum)maximum=value;
            else if(evicted==maximum) {
                // 仅最大值被淘汰时重查有限窗口，重复最大值不能提前消失。
                maximum=0;
                for(int i=0;i<count;i++)maximum=Math.max(maximum,values[i]);
            }
            completed=new Sample(count,sum/(double)count/1000,maximum/1000.0);
        }
    }

    /** 一次volatile读取；HTTP格式化、调度或类加载不会持有producerLock。 */
    public Sample snapshot() {
        return completed;
    }

    public void reset() {
        synchronized(producerLock) {
            next=0;
            count=0;
            sum=0;
            maximum=0;
            Arrays.fill(values,0);
            completed=EMPTY;
        }
    }
}
