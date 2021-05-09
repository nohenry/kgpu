package io.github.kgpu

import io.github.kgpu.wgpuj.wgpu_h
import io.github.kgpu.wgpuj.wgpu_h.*
import jdk.incubator.foreign.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

object Platform {
    val isWindows = System.getProperty("os.name").contains("Windows")
    val isLinux = System.getProperty("os.name").contains("Linux")
    val isMac = System.getProperty("os.name").contains("Mac")
}

object CUtils {
    val NULL: MemoryAddress = wgpu_h.NULL()!!

    fun copyToNativeArray(values: LongArray, scope: NativeScope): MemoryAddress {
        if (values.isEmpty())
            return NULL

        return scope.allocateArray(MemoryLayouts.JAVA_LONG, values).address()
    }
}

fun Boolean.toNativeByte(): Byte {
    return if (this) {
        0x01
    } else {
        0x00
    }
}

actual object Kgpu {
    actual val undefined = null

    fun initGlfw() {
        GlfwHandler.glfwInit()
    }

    // TODO: Create proper logging API
    fun initializeLogging() {
        val callback = WGPULogCallback.allocate { level, msg ->
            val msgJvm = CLinker.toJavaStringRestricted(msg, StandardCharsets.UTF_8)
            val levelStr = when (level) {
                WGPULogLevel_Error() -> "Error"
                WGPULogLevel_Warn() -> "Warn"
                WGPULogLevel_Info() -> "Info"
                WGPULogLevel_Debug() -> "Debug"
                WGPULogLevel_Trace() -> "Trace"
                else -> "UnknownLevel($level)"
            }
            println("$levelStr: $msgJvm")
        }
        wgpuSetLogCallback(callback)
        wgpuSetLogLevel(WGPULogLevel_Warn())
    }

    /**
     * Extracts wgpu-native from the classpath and loads it for the
     * JVM to use. For this function to work, there must be a library
     * called "wgpu_native" in the root of the classpath
     */
    fun loadNativesFromClasspath() {
        val library = SharedLibraryLoader().load("wgpu_native")
        System.load(library.absolutePath)
    }

    actual fun runLoop(window: Window, func: () -> Unit) {
        while (!window.isCloseRequested()) {
            window.update()
            func()
        }

        GlfwHandler.terminate()
    }

    actual suspend fun requestAdapterAsync(window: Window?): Adapter {
        val output = AtomicLong()

        NativeScope.unboundedScope().use { scope ->
            val options = WGPURequestAdapterOptions.allocate(scope)
            val callback = WGPURequestAdapterCallback.allocate({ result: MemoryAddress, _: MemoryAddress? ->
                output.set(result.toRawLongValue())
            }, scope)

            WGPURequestAdapterOptions.`compatibleSurface$set`(options, window?.surface?.address() ?: CUtils.NULL)
            WGPURequestAdapterOptions.`nextInChain$set`(options, CUtils.NULL)
            wgpuInstanceRequestAdapter(CUtils.NULL, options, callback, CUtils.NULL)
        }

        return Adapter(Id(output.get()))
    }
}

actual class Adapter(val id: Id) {

    override fun toString(): String {
        return "Adapter$id"
    }

    actual suspend fun requestDeviceAsync(): Device {
        val tracePath = System.getenv("KGPU_TRACE_PATH") ?: null
        val output = AtomicLong()

        NativeScope.unboundedScope().use { scope ->
            val desc = WGPUDeviceDescriptor.allocate(scope)
            val deviceExtras = WGPUDeviceExtras.allocate(scope)
            val chainedStruct = WGPUDeviceExtras.`chain$slice`(deviceExtras)
            val callback = WGPURequestDeviceCallback.allocate({ result, _ ->
                output.set(result.toRawLongValue())
            }, scope)

            WGPUChainedStruct.`sType$set`(chainedStruct, WGPUSType_DeviceExtras())
            WGPUDeviceExtras.`maxBindGroups$set`(deviceExtras, 1)
            WGPUDeviceDescriptor.`nextInChain$set`(desc, deviceExtras.address())

            if (tracePath != null) {
                println("Trace Path Set: $tracePath")
                WGPUDeviceExtras.`tracePath$set`(deviceExtras, CLinker.toCString(tracePath).address())
            }

            wgpuAdapterRequestDevice(id.address(), desc, callback, CUtils.NULL)
        }

        return Device(Id(output.get()))
    }
}

actual class Device(val id: Id) {

    override fun toString(): String {
        return "Device$id"
    }

    actual fun createShaderModule(src: String): ShaderModule {
        return NativeScope.unboundedScope().use { scope ->
            val desc = WGPUShaderModuleDescriptor.allocate(scope)
            val wgsl = WGPUShaderModuleWGSLDescriptor.allocate(scope)
            val wgslChain = WGPUShaderModuleWGSLDescriptor.`chain$slice`(wgsl)

            WGPUChainedStruct.`next$set`(wgslChain, CUtils.NULL)
            WGPUChainedStruct.`sType$set`(wgslChain, WGPUSType_ShaderModuleWGSLDescriptor())
            WGPUShaderModuleWGSLDescriptor.`source$set`(wgsl, CLinker.toCString(src, scope).address())
            WGPUShaderModuleDescriptor.`nextInChain$set`(desc, wgsl.address())

            ShaderModule(Id(wgpuDeviceCreateShaderModule(id, desc)))
        }
    }

    actual fun createRenderPipeline(desc: RenderPipelineDescriptor): RenderPipeline {
        return RenderPipeline(Id(NativeScope.unboundedScope().use { scope ->
            val fragmentDesc = if (desc.fragment != null) {
                val fragmentDesc = WGPUFragmentState.allocate(scope)
                val targets = WGPUColorTargetState.allocateArray(desc.fragment.targets.size, scope)
                desc.fragment.targets.forEachIndexed { index, target ->
                    if (target.blendState == null)
                        TODO("Null blend states are currently not supported")

                    val blendState = WGPUBlendState.allocate(scope)
                    val colorBlend = WGPUBlendState.`color$slice`(blendState)
                    val alphaBlend = WGPUBlendState.`alpha$slice`(blendState)
                    WGPUBlendComponent.`srcFactor$set`(colorBlend, target.blendState.color.srcFactor.nativeVal)
                    WGPUBlendComponent.`dstFactor$set`(colorBlend, target.blendState.color.dstFactor.nativeVal)
                    WGPUBlendComponent.`operation$set`(colorBlend, target.blendState.color.operation.nativeVal)
                    WGPUBlendComponent.`srcFactor$set`(alphaBlend, target.blendState.alpha.srcFactor.nativeVal)
                    WGPUBlendComponent.`dstFactor$set`(alphaBlend, target.blendState.alpha.dstFactor.nativeVal)
                    WGPUBlendComponent.`operation$set`(alphaBlend, target.blendState.alpha.operation.nativeVal)

                    WGPUColorTargetState.`format$set`(targets, index.toLong(), target.format.nativeVal)
                    WGPUColorTargetState.`writeMask$set`(targets, index.toLong(), target.writeMask.toInt())
                    WGPUColorTargetState.`blend$set`(targets, index.toLong(), blendState.address())
                }
                WGPUFragmentState.`entryPoint$set`(fragmentDesc, CLinker.toCString(desc.fragment.entryPoint).address())
                WGPUFragmentState.`module$set`(fragmentDesc, desc.fragment.module.id.address())
                WGPUFragmentState.`targets$set`(fragmentDesc, targets.address())
                WGPUFragmentState.`targetCount$set`(fragmentDesc, desc.fragment.targets.size)

                fragmentDesc
            } else {
                CUtils.NULL
            }

            val descriptor = WGPURenderPipelineDescriptor.allocate(scope)
            val vertexState = WGPURenderPipelineDescriptor.`vertex$slice`(descriptor)
            val primitiveState = WGPURenderPipelineDescriptor.`primitive$slice`(descriptor)
            val multisampleState = WGPURenderPipelineDescriptor.`multisample$slice`(descriptor)

            WGPURenderPipelineDescriptor.`label$set`(descriptor, CUtils.NULL)
            WGPURenderPipelineDescriptor.`layout$set`(descriptor, desc.layout.id.address())
            WGPUVertexState.`module$set`(vertexState, desc.vertex.module.id.address())
            WGPUVertexState.`entryPoint$set`(vertexState, CLinker.toCString(desc.vertex.entryPoint).address())
            // TODO: Buffers
            WGPUPrimitiveState.`topology$set`(primitiveState, desc.primitive.topology.nativeVal)
            WGPUPrimitiveState.`stripIndexFormat$set`(
                primitiveState,
                (desc.primitive.stripIndexFormat?.nativeVal ?: WGPUIndexFormat_Undefined())
            )
            WGPUPrimitiveState.`frontFace$set`(primitiveState, WGPUFrontFace_CCW())
            WGPUPrimitiveState.`cullMode$set`(primitiveState, desc.primitive.cullMode.nativeVal)

            WGPUMultisampleState.`count$set`(multisampleState, desc.multisample.count)
            WGPUMultisampleState.`mask$set`(multisampleState, desc.multisample.mask)
            WGPUMultisampleState.`alphaToCoverageEnabled$set`(
                multisampleState,
                desc.multisample.alphaToCoverageEnabled.toNativeByte()
            )

            WGPURenderPipelineDescriptor.`fragment$set`(descriptor, fragmentDesc.address())

            wgpuDeviceCreateRenderPipeline(id, descriptor)
        }))
    }

    actual fun createPipelineLayout(desc: PipelineLayoutDescriptor): PipelineLayout {
        return PipelineLayout(Id(NativeScope.unboundedScope().use {scope ->
            val descriptor = WGPUPipelineLayoutDescriptor.allocate(scope)
            WGPUPipelineLayoutDescriptor.`bindGroupLayouts$set`(descriptor, CUtils.copyToNativeArray(desc.ids, scope))
            WGPUPipelineLayoutDescriptor.`bindGroupLayoutCount$set`(descriptor, desc.ids.size)

            wgpuDeviceCreatePipelineLayout(id, descriptor)
        }))
    }

    actual fun createTexture(desc: TextureDescriptor): Texture {
        TODO()
    }

    actual fun createCommandEncoder(): CommandEncoder {
        return CommandEncoder(Id(NativeScope.unboundedScope().use { scope ->
            val desc = WGPUCommandEncoderDescriptor.allocate(scope)
            WGPUCommandEncoderDescriptor.`label$set`(desc, CLinker.toCString("CommandEncoder", scope).address())
            wgpuDeviceCreateCommandEncoder(id, desc.address())
        }))
    }

    actual fun getDefaultQueue(): Queue {
        return Queue(Id(wgpuDeviceGetQueue(id)))
    }

    actual fun createBuffer(desc: BufferDescriptor): Buffer {
        return Buffer(Id(NativeScope.unboundedScope().use { scope ->
            val descriptor = WGPUBufferDescriptor.allocate(scope)
            WGPUBufferDescriptor.`nextInChain$set`(descriptor, CUtils.NULL)
            WGPUBufferDescriptor.`usage$set`(descriptor, desc.usage)
            WGPUBufferDescriptor.`size$set`(descriptor, desc.size)
            WGPUBufferDescriptor.`mappedAtCreation$set`(descriptor, desc.mappedAtCreation.toNativeByte())

            wgpuDeviceCreateBuffer(id, descriptor)
        }), desc.size)
    }

    actual fun createBindGroupLayout(desc: BindGroupLayoutDescriptor): BindGroupLayout {
        TODO()

    }

    actual fun createBindGroup(desc: BindGroupDescriptor): BindGroup {
        TODO()
    }

    actual fun createSampler(desc: SamplerDescriptor): Sampler {
        TODO()
    }

    actual fun createComputePipeline(desc: ComputePipelineDescriptor): ComputePipeline {
        TODO()
    }
}

actual class CommandEncoder(val id: Id) {

    override fun toString(): String {
        return "CommandEncoder$id"
    }

    actual fun beginRenderPass(desc: RenderPassDescriptor): RenderPassEncoder {
        return RenderPassEncoder(NativeScope.unboundedScope().use { scope ->
            val descriptor = WGPURenderPassDescriptor.allocate(scope)
            val colorAttachments =
                WGPURenderPassColorAttachmentDescriptor.allocateArray(desc.colorAttachments.size, scope)
            val colors = WGPURenderPassColorAttachmentDescriptor.`clearColor$slice`(colorAttachments)

            desc.colorAttachments.forEachIndexed { indexInt, attachment ->
                val index = indexInt.toLong()
                WGPURenderPassColorAttachmentDescriptor.`attachment$set`(
                    colorAttachments,
                    index,
                    attachment.attachment.id.address()
                )
                WGPURenderPassColorAttachmentDescriptor.`resolveTarget$set`(
                    colorAttachments,
                    index,
                    attachment.resolveTarget?.id?.address() ?: CUtils.NULL
                )
                WGPURenderPassColorAttachmentDescriptor.`loadOp$set`(
                    colorAttachments,
                    index,
                    attachment.loadOp.nativeVal,
                )
                WGPURenderPassColorAttachmentDescriptor.`storeOp$set`(
                    colorAttachments,
                    index,
                    attachment.storeOp.nativeVal
                )
                WGPUColor.`r$set`(colors, index, attachment.clearColor?.r ?: 0.0)
                WGPUColor.`g$set`(colors, index, attachment.clearColor?.g ?: 0.0)
                WGPUColor.`b$set`(colors, index, attachment.clearColor?.b ?: 0.0)
                WGPUColor.`a$set`(colors, index, attachment.clearColor?.a ?: 0.0)
            }

            WGPURenderPassDescriptor.`colorAttachments$set`(descriptor, colorAttachments.address())
            WGPURenderPassDescriptor.`colorAttachmentCount$set`(descriptor, desc.colorAttachments.size)

            wgpuCommandEncoderBeginRenderPass(id, descriptor.address())
        })
    }

    actual fun finish(): CommandBuffer {
        return CommandBuffer(Id(NativeScope.unboundedScope().use { scope ->
            val descriptor = WGPUCommandBufferDescriptor.allocate(scope)
            //TODO: Support labels
            WGPUCommandBufferDescriptor.`label$set`(descriptor, CUtils.NULL)
            wgpuCommandEncoderFinish(id, descriptor)
        }))
    }

    actual fun copyBufferToTexture(
        source: BufferCopyView, destination: TextureCopyView, copySize: Extent3D
    ) {
        TODO()
    }

    actual fun beginComputePass(): ComputePassEncoder {
        TODO()
    }

    actual fun copyBufferToBuffer(
        source: Buffer, destination: Buffer, size: Long, sourceOffset: Int, destinationOffset: Int
    ) {
        TODO()
    }

    actual fun copyTextureToBuffer(source: TextureCopyView, dest: BufferCopyView, size: Extent3D) {
        TODO()
    }
}

actual class RenderPassEncoder(var pass: MemoryAddress) {

    override fun toString(): String {
        return "RenderPassEncoder"
    }

    actual fun setPipeline(pipeline: RenderPipeline) {
        assertPassStillValid()
        wgpuRenderPassEncoderSetPipeline(pass, pipeline.id)
    }

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        assertPassStillValid()
        wgpuRenderPassEncoderDraw(pass, vertexCount, instanceCount, firstVertex, firstInstance)
    }

    actual fun endPass() {
        wgpuRenderPassEncoderEndPass(pass)
        pass = CUtils.NULL
    }

    actual fun setVertexBuffer(slot: Long, buffer: Buffer, offset: Long, size: Long) {
        assertPassStillValid()
        wgpuRenderPassEncoderSetVertexBuffer(pass, slot.toInt(), buffer.id, offset, size)
    }

    actual fun drawIndexed(
        indexCount: Int, instanceCount: Int, firstVertex: Int, baseVertex: Int, firstInstance: Int
    ) {
        assertPassStillValid()
        TODO()
    }

    actual fun setIndexBuffer(buffer: Buffer, indexFormat: IndexFormat, offset: Long, size: Long) {
        assertPassStillValid()
        TODO()
    }

    actual fun setBindGroup(index: Int, bindGroup: BindGroup) {
        assertPassStillValid()
        TODO()
    }

    private fun assertPassStillValid() {
        if (pass == CUtils.NULL)
            throw RuntimeException("Render Pass Encoder has ended.")
    }
}

actual class ComputePassEncoder() {

    override fun toString(): String {
        return "ComputePassEncoder"
    }

    actual fun setPipeline(pipeline: ComputePipeline) {
        TODO()
    }

    actual fun setBindGroup(index: Int, bindGroup: BindGroup) {
        TODO()
    }

    actual fun dispatch(x: Int, y: Int, z: Int) {
        TODO()
    }

    actual fun endPass() {
        TODO()
    }
}

actual class ShaderModule(val id: Id) {

    override fun toString(): String {
        return "ShaderModule$id"
    }
}

actual class ProgrammableStageDescriptor
actual constructor(val module: ShaderModule, val entryPoint: String) {
}

actual class BindGroupLayoutEntry
actual constructor(
    binding: Long,
    visibility: Long,
    type: BindingType,
    hasDynamicOffset: kotlin.Boolean,
    viewDimension: TextureViewDimension?,
    textureComponentType: TextureComponentType?,
    multisampled: kotlin.Boolean,
    storageTextureFormat: TextureFormat?
) {

    actual constructor(binding: Long, visibility: Long, type: BindingType) : this(
        binding, visibility, type, false, null, null, false, null
    )

    actual constructor(
        binding: Long, visibility: Long, type: BindingType, multisampled: kotlin.Boolean
    ) : this(binding, visibility, type, false, null, null, multisampled, null)

    actual constructor(
        binding: Long,
        visibility: Long,
        type: BindingType,
        multisampled: kotlin.Boolean,
        dimension: TextureViewDimension,
        textureComponentType: TextureComponentType
    ) : this(binding, visibility, type, false, dimension, textureComponentType, multisampled, null)
}

actual class BindGroupLayout internal constructor(val id: Long) {

    override fun toString(): String {
        return "BindGroupLayout$id"
    }
}

actual class PipelineLayoutDescriptor internal constructor(val ids: LongArray) {

    actual constructor(vararg bindGroupLayouts: BindGroupLayout) : this(bindGroupLayouts.map { it.id }.toLongArray())
}

actual class PipelineLayout(val id: Id) {

    override fun toString(): String {
        return "PipelineLayout$id"
    }
}

actual class RenderPipeline internal constructor(val id: Id) {

    override fun toString(): String {
        return "RenderPipeline$id"
    }
}

actual class ComputePipeline internal constructor(val id: Long) {

    override fun toString(): String {
        return "ComputePipeline$id"
    }
}

actual class BlendComponent
actual constructor(
    val srcFactor: BlendFactor, val dstFactor: BlendFactor, val operation: BlendOperation
)

actual class Extent3D actual constructor(width: Long, height: Long, depth: Long) {

}

actual class TextureDescriptor
actual constructor(
    size: Extent3D,
    mipLevelCount: Long,
    sampleCount: Int,
    dimension: TextureDimension,
    format: TextureFormat,
    usage: Long
) {
}

actual class TextureViewDescriptor
actual constructor(
    format: TextureFormat,
    dimension: TextureViewDimension,
    aspect: TextureAspect,
    baseMipLevel: Long,
    mipLevelCount: Long,
    baseArrayLayer: Long,
    arrayLayerCount: Long
) {
}

actual class Texture(val id: Long) {

    override fun toString(): String {
        return "Texture$id"
    }

    actual fun createView(desc: TextureViewDescriptor?): TextureView {
        TODO()
    }

    actual fun destroy() {
        TODO()
    }
}

actual class TextureView(val id: Id) : IntoBindingResource {

    actual fun destroy() {
        TODO()

    }

    override fun intoBindingResource() {
        TODO()
    }

    override fun toString(): String {
        return "TextureView$id"
    }
}

actual class SwapChainDescriptor
actual constructor(val device: Device, val format: TextureFormat, val usage: Long)

actual class SwapChain(val id: Id, private val window: Window) {

    private val size = window.windowSize

    override fun toString(): String {
        return "SwapChain$id"
    }

    actual fun getCurrentTextureView(): TextureView {
        return TextureView(Id(wgpuSwapChainGetCurrentTextureView(id)))
    }

    actual fun present() {
        wgpuSwapChainPresent(id)
    }

    actual fun isOutOfDate(): Boolean {
        return window.windowSize != size
    }
}

actual class RenderPassColorAttachmentDescriptor
actual constructor(
    val attachment: TextureView,
    val loadOp: LoadOp,
    val storeOp: StoreOp,
    val clearColor: Color?,
    val resolveTarget: TextureView?,
)

actual class RenderPassDescriptor
actual constructor(vararg val colorAttachments: RenderPassColorAttachmentDescriptor) {
}

actual class CommandBuffer(val id: Id) {

    override fun toString(): String {
        return "CommandBuffer$id"
    }
}

actual class Queue(val id: Id) {

    override fun toString(): String {
        return "Queue$id"
    }

    actual fun submit(vararg cmdBuffers: CommandBuffer) {
        NativeScope.unboundedScope().use { scope ->
            val bufferIds = CUtils.copyToNativeArray(cmdBuffers.map { it.id.id }.toLongArray(), scope)

            wgpuQueueSubmit(id, cmdBuffers.size, bufferIds)
        }
    }

    actual fun writeBuffer(
        buffer: Buffer, data: ByteArray, offset: Long, dataOffset: Long, size: Long
    ) {
        TODO()
    }
}

actual class BufferDescriptor
actual constructor(
    val label: String, val size: Long, val usage: Int, val mappedAtCreation: Boolean
) {
}

actual class Buffer(val id: Id, actual val size: Long) : IntoBindingResource {

    override fun intoBindingResource() {
        TODO()
    }

    override fun toString(): String {
        return "Buffer$id"
    }

    actual fun getMappedData(start: Long, size: Long): BufferData {
        val ptr = wgpuBufferGetMappedRange(id, start, size)


        return BufferData(ptr.asSegmentRestricted(size))
    }

    actual fun unmap() {
        wgpuBufferUnmap(id)
    }

    actual fun destroy() {
        wgpuBufferDestroy(id)
    }

    actual suspend fun mapReadAsync(device: Device): BufferData {
        TODO("mapReadAsync not implemented in KGPU.")
    }
}

actual class BufferData(val data: MemorySegment) {

    actual fun putBytes(bytes: ByteArray, offset: Int) {
        data.asByteBuffer().put(offset, bytes)
    }

    actual fun getBytes(): ByteArray {
        val buffer = ByteArray(data.byteSize().toInt())
        data.asByteBuffer().get(buffer)

        return buffer
    }
}

actual class BindGroupLayoutDescriptor actual constructor(vararg entries: BindGroupLayoutEntry) {

}

actual class BindGroupEntry actual constructor(binding: Long, resource: IntoBindingResource) {

}

actual class BindGroupDescriptor
actual constructor(layout: BindGroupLayout, vararg entries: BindGroupEntry) {
}

actual class BindGroup(val id: Long) {

    override fun toString(): String {
        return "BindGroup$id"
    }
}

actual interface IntoBindingResource {

    fun intoBindingResource()
}

actual class Origin3D actual constructor(x: Long, y: Long, z: Long) {

}

actual class TextureCopyView
actual constructor(texture: Texture, mipLevel: Long, origin: Origin3D) {
}

actual class BufferCopyView
actual constructor(buffer: Buffer, bytesPerRow: Int, rowsPerImage: Int, offset: Long) {

}

actual class SamplerDescriptor
actual constructor(
    compare: CompareFunction?,
    addressModeU: AddressMode,
    addressModeV: AddressMode,
    addressModeW: AddressMode,
    magFilter: FilterMode,
    minFilter: FilterMode,
    mipmapFilter: FilterMode,
    lodMinClamp: kotlin.Float,
    lodMaxClamp: kotlin.Float,
    maxAnisotrophy: Short
) {
}

actual class Sampler(val id: Long) : IntoBindingResource {

    override fun intoBindingResource() {
        TODO()
    }

    override fun toString(): String {
        return "Sampler$id"
    }
}

actual class ComputePipelineDescriptor
actual constructor(layout: PipelineLayout, computeStage: ProgrammableStageDescriptor) {
}

actual class FragmentState actual constructor(
    val module: ShaderModule,
    val entryPoint: String,
    val targets: Array<ColorTargetState>
)

actual class BlendState actual constructor(val color: BlendComponent, val alpha: BlendComponent)

actual class ColorTargetState actual constructor(
    val format: TextureFormat,
    val blendState: BlendState?,
    val writeMask: Long
)

actual class MultisampleState actual constructor(
    val count: Int,
    val mask: Int,
    val alphaToCoverageEnabled: Boolean
)

actual class RenderPipelineDescriptor actual constructor(
    val layout: PipelineLayout,
    val vertex: VertexState,
    val primitive: PrimitiveState,
    val depthStencil: Any?,
    val multisample: MultisampleState,
    val fragment: FragmentState?
)

actual class VertexState actual constructor(
    val module: ShaderModule,
    val entryPoint: String,
    vararg val buffers: VertexBufferLayout
)

actual class PrimitiveState actual constructor(
    val topology: PrimitiveTopology,
    val stripIndexFormat: IndexFormat?,
    val frontFace: FrontFace,
    val cullMode: CullMode
)

actual class VertexAttribute actual constructor(
    val format: VertexFormat,
    val offset: Long,
    val shaderLocation: Int
)

actual class VertexBufferLayout actual constructor(
    val arrayStride: Long,
    val stepMode: InputStepMode,
    vararg attributes: VertexAttribute
)