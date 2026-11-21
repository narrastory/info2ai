package com.example.info2ai.staticworld;

/** Immutable owner-thread identity. All seed bits stay in a decimal string. */
public record StaticQuery(long epoch,String worldId,String seed,String mc,String dimension,String generation,
        int x,int y,int z,int radius,boolean enabled,GenerationHandle context) {
    public record Key(long epoch,String worldId,String seed,String mc,String dimension,String generation,
                      int bucketX,int y,int bucketZ,int radius,boolean enabled) { }
    public Key key() {return new Key(epoch,worldId,seed,mc,dimension,generation,Math.floorDiv(x,128),y,
            Math.floorDiv(z,128),radius,enabled);}
    public int sampleX() {return Math.floorDiv(x,128)*128+64;}
    public int sampleZ() {return Math.floorDiv(z,128)*128+64;}
    public int candidateRadius() {return Math.min(20000,radius+91);}
    public String shortDimension() {
        return switch(dimension) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end" -> "end";
            case null, default -> "unsupported";
        };
    }
    public boolean supported() {return enabled&&context!=null&&!"unsupported".equals(shortDimension());}
}
