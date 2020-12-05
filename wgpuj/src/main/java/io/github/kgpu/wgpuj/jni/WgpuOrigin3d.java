package io.github.kgpu.wgpuj.jni;

import io.github.kgpu.wgpuj.util.WgpuJavaStruct;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WgpuOrigin3d extends WgpuJavaStruct {

    private final Struct.Unsigned32 x = new Struct.Unsigned32();
    private final Struct.Unsigned32 y = new Struct.Unsigned32();
    private final Struct.Unsigned32 z = new Struct.Unsigned32();

    protected WgpuOrigin3d(boolean direct) {
        if (direct) {
            useDirectMemory();
        }
    }

    @Deprecated
    public WgpuOrigin3d(Runtime runtime) {
        super(runtime);
    }

    /**
     * Creates this struct on the java heap. In general, this should <b>not</b> be used because
     * these structs cannot be directly passed into native code.
     */
    public static WgpuOrigin3d createHeap() {
        return new WgpuOrigin3d(false);
    }

    /**
     * Creates this struct in direct memory. This is how most structs should be created (unless,
     * they are members of a nothing struct)
     *
     * @see WgpuJavaStruct#useDirectMemory
     */
    public static WgpuOrigin3d createDirect() {
        return new WgpuOrigin3d(true);
    }

    public long getX() {
        return x.get();
    }

    public void setX(long x) {
        this.x.set(x);
    }

    public long getY() {
        return y.get();
    }

    public void setY(long x) {
        this.y.set(x);
    }

    public long getZ() {
        return z.get();
    }

    public void setZ(long x) {
        this.z.set(x);
    }
}
