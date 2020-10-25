package io.github.kgpu.wgpuj.jni;

import io.github.kgpu.wgpuj.util.WgpuJavaStruct;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WgpuPassChannel_u32 extends WgpuJavaStruct {

    private final Struct.Enum<WgpuLoadOp> loadOp = new Struct.Enum<>(WgpuLoadOp.class);
    private final Struct.Enum<WgpuStoreOp> storeOp = new Struct.Enum<>(WgpuStoreOp.class);
    private final Struct.Unsigned32 clearValue = new Struct.Unsigned32();
    private final Struct.Boolean readOnly = new Struct.Boolean();

    protected WgpuPassChannel_u32(boolean direct){
         if(direct){
             useDirectMemory();
        }
    }

    @Deprecated
    public WgpuPassChannel_u32(Runtime runtime){
        super(runtime);
    }

    /**
    * Creates this struct on the java heap.
    * In general, this should <b>not</b> be used because these structs
    * cannot be directly passed into native code. 
    */
    public static WgpuPassChannel_u32 createHeap(){
        return new WgpuPassChannel_u32(false);
    }

    /**
    * Creates this struct in direct memory.
    * This is how most structs should be created (unless, they
    * are members of a nothing struct)
    * 
    * @see WgpuJavaStruct#useDirectMemory
    */
    public static WgpuPassChannel_u32 createDirect(){
        return new WgpuPassChannel_u32(true);
    }


    public WgpuLoadOp getLoadOp(){
        return loadOp.get();
    }

    public void setLoadOp(WgpuLoadOp x){
        this.loadOp.set(x);
    }

    public WgpuStoreOp getStoreOp(){
        return storeOp.get();
    }

    public void setStoreOp(WgpuStoreOp x){
        this.storeOp.set(x);
    }

    public long getClearValue(){
        return clearValue.get();
    }

    public void setClearValue(long x){
        this.clearValue.set(x);
    }

    public boolean getReadOnly(){
        return readOnly.get();
    }

    public void setReadOnly(boolean x){
        this.readOnly.set(x);
    }

}