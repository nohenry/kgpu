package io.github.kgpu

import kotlinx.coroutines.*
import kotlin.js.Promise
import kotlin.browser.window as jsWindow
import kotlin.browser.document as jsDocument

actual object Kgpu {
    actual val backendName: String = "Web"

    actual fun runLoop(window: Window, func: () -> Unit) {
        func();

        jsWindow.requestAnimationFrame {
            runLoop(window, func)
        };
    }

}

actual class Window actual constructor() {

    actual fun setTitle(title: String) {
        jsDocument.title = title
    }

    actual fun isCloseRequested(): Boolean {
        return false
    }

    actual fun update() {

    }

    actual suspend fun requestAdapterAsync(preference: PowerPreference): Adapter {
        return Adapter((js("navigator.gpu.requestAdapter()") as Promise<GPUAdapter>).await())
    }
}

open external class GPUObjectBase {
    val label: String
}

open external class GPUObjectDescriptorBase {
    val label: String
}


actual class Adapter(val jsType: GPUAdapter) {

    actual suspend fun requestDeviceAsync(): Device {
        return Device(jsType.requestDevice().await())
    }

    override fun toString(): String {
        return "Adapter($jsType)"
    }

}

open external class GPUAdapter {
    val name: String
    val extensions: List<GPUExtensionName>

    fun requestDevice(): Promise<GPUDevice>
}

/**
 * Eventually will be external once implemented in browsers
 */
enum class GPUExtensionName {
    TextureCompressionBC,
    PipelineStatisticsQuery,
    TimestampQuery,
    DepthClamping
}

actual enum class PowerPreference(jsType: GPUPowerPreference?) {
    LOW_POWER(GPUPowerPreference.LOW_POWER),
    DEFAULT(null),
    HIGH_PERFORMANCE(GPUPowerPreference.HIGH_PERFORMANCE)
}

/**
 * Eventually will be external once implemented in browsers
 */
enum class GPUPowerPreference {
    LOW_POWER, HIGH_PERFORMANCE
}

actual class Device(val jsType: GPUDevice) {

    override fun toString(): String {
        return "Device($jsType)"
    }

    actual fun createShaderModule(data: ByteArray): ShaderModule {
        val desc = asDynamic()
        desc.code = data;

        val moduleJs = jsType.createShaderModule(desc)

        return ShaderModule(moduleJs)
    }

    actual fun createRenderPipeline(desc: RenderPipelineDescriptor) : RenderPipeline {
        TODO("Not implemented yet")
    }

    actual fun createPipelineLayout(desc: PipelineLayoutDescriptor): PipelineLayout {
        TODO("Not implemented yet")
    }

}

open external class GPUDevice {
    val adapter: GPUAdapter
    val extensions: List<GPUExtensionName>
    val limits: Any
    val defaultQueue: Any

    fun createShaderModule(desc: dynamic): GPUShaderModule
}

external class GPUShaderModule : GPUObjectBase {
    val compilationInfo: Any
}

actual class ShaderModule(val jsType: GPUShaderModule) {

    override fun toString(): String {
        return "ShaderModule($jsType)"
    }

}

actual class ProgrammableStageDescriptor actual constructor(module: ShaderModule, entry: String) {

    init {
        TODO("Not implemented")
    }

}

actual enum class PrimitiveTopology {
    POINT_LIST, LINE_LIST, LINE_STRIP, TRIANGLE_LIST, TRIANGLE_STRIP,
}

actual enum class FrontFace {
    CCW, CW,
}

actual enum class CullMode {
    NONE, FRONT, BACK,
}

actual class RasterizationStateDescriptor actual constructor(
        val frontFace: FrontFace,
        val cullMode: CullMode,
        val clampDepth: Boolean,
        val depthBias: Long,
        val depthBiasSlopeScale: Float,
        val depthBiasClamp: Float);

actual enum class TextureFormat {
    R8_UNORM, R8_SNORM, R8_UINT, R8_SINT, R16_UINT, R16_SINT, R16_FLOAT, RG8_UNORM, RG8_SNORM, RG8_UINT, RG8_SINT, R32_UINT, R32_SINT, R32_FLOAT, RG16_UINT, RG16_SINT, RG16_FLOAT, RGBA8_UNORM, RGBA8_UNORM_SRGB, RGBA8_SNORM, RGBA8_UINT, RGBA8_SINT, BGRA8_UNORM, BGRA8_UNORM_SRGB, RGB10A2_UNORM, RG11B10_FLOAT, RG32_UINT, RG32_SINT, RG32_FLOAT, RGBA16_UINT, RGBA16_SINT, RGBA16_FLOAT, RGBA32_UINT, RGBA32_SINT, RGBA32_FLOAT, DEPTH32_FLOAT, DEPTH24_PLUS, DEPTH24_PLUS_STENCIL8,
}

actual enum class BlendOperation {
    ADD, SUBTRACT, REVERSE_SUBTRACT, MIN, MAX,
}

actual enum class StencilOperation {
    KEEP, ZERO, REPLACE, INVERT, INCREMENT_CLAMP, DECREMENT_CLAMP, INCREMENT_WRAP, DECREMENT_WRAP,
}

actual enum class BlendFactor {
    ZERO, ONE, SRC_COLOR, ONE_MINUS_SRC_COLOR, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, DST_COLOR, ONE_MINUS_DST_COLOR, DST_ALPHA, ONE_MINUS_DST_ALPHA, SRC_ALPHA_SATURATED, BLEND_COLOR, ONE_MINUS_BLEND_COLOR,
}

actual class ColorStateDescriptor actual constructor(
        val format: TextureFormat,
        val alphaBlend: BlendDescriptor,
        val colorBlend: BlendDescriptor,
        val writeMask: Long)

actual class RenderPipelineDescriptor actual constructor(
        val layout: PipelineLayout,
        val vertexStage: ProgrammableStageDescriptor,
        val fragmentStage: ProgrammableStageDescriptor,
        val primitive: PrimitiveTopology,
        val rasterizationState: RasterizationStateDescriptor,
        val colorStates: Array<ColorStateDescriptor>,
        val depthStencilState: Any?,
        val vertexState: VertexStateDescriptor,
        val sampleCount: Int,
        val sampleMask: Int,
        val alphaToCoverage: Boolean
)

actual enum class IndexFormat {
    UINT16, UINT32,
}

actual enum class VertexFormat {
    UCHAR2, UCHAR4, CHAR2, CHAR4, UCHAR2_NORM, UCHAR4_NORM, CHAR2_NORM, CHAR4_NORM, USHORT2, USHORT4, SHORT2, SHORT4, USHORT2_NORM, USHORT4_NORM, SHORT2_NORM, SHORT4_NORM, HALF2, HALF4, FLOAT, FLOAT2, FLOAT3, FLOAT4, UINT, UINT2, UINT3, UINT4, INT, INT2, INT3, INT4,
}

actual class VertexAttributeDescriptor actual constructor(
        val format: VertexFormat,
        val offset: Long,
        val shaderLocation: Int)

actual enum class InputStepMode {
    VERTEX, INSTANCE,
}

actual class VertexBufferLayoutDescriptor actual constructor(
        val stride: Long,
        val stepMode: InputStepMode,
        val attributes: Array<VertexAttributeDescriptor>)

actual class VertexStateDescriptor actual constructor(
        val indexFormat: IndexFormat,
        val vertexBuffers: Array<VertexBufferLayoutDescriptor>)

actual class BindGroupLayoutEntry {
    init {
        TODO();
    }
}

actual class BindGroupLayout {

    init {
        TODO()
    }

}

actual class PipelineLayoutDescriptor actual constructor(val bindGroupLayouts: Array<BindGroupLayout>)
actual class PipelineLayout {
    init {
        TODO()
    }
}

actual class RenderPipeline