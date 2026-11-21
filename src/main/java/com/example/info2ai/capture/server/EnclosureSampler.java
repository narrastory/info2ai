package com.example.info2ai.capture.server;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
/** A negative sample means unavailable chunk, never air. */
public final class EnclosureSampler {
    private String dimension;
    private int x,z,age;
    private Double cached;
    public Double sample(String dimension,int chunkX,int chunkZ,IntUnaryOperator blocked) {
        if(Objects.equals(this.dimension,dimension) && x==chunkX && z==chunkZ && ++age<5)return cached;
        this.dimension=dimension;this.x=chunkX;this.z=chunkZ;age=0;
        int hits=0;boolean known=true;
        for(int i=0;i<48;i++) { int value=blocked.applyAsInt(i);if(value<0)known=false;else hits+=value==0?0:1; }
        cached=known?hits/48.0:null;return cached;
    }
}
