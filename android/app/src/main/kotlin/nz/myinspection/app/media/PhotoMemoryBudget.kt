package nz.myinspection.app.media

/**
 * 本次 ingest 允许用于瞬时位图和有界编码流的字节数，喂给 `ImportBounds.check`：取**当前堆余量**
 * （`maxMemory - 已用`）的一半，另一半留给 ingest 期间必须继续活着的东西（Compose UI、CameraX 缓冲）。
 * API 26+ 位图像素在 native 堆，但 ART 仍把它们计入同一个进程堆上限做 OOM 判定，故用 Java 堆余量作
 * 保守上界。[runtime] 可注入，便于上层按设备策略覆盖。
 */
object PhotoMemoryBudget {
    private const val TRANSIENT_FRACTION = 0.5

    fun transientBytes(runtime: Runtime = Runtime.getRuntime()): Long {
        val used = runtime.totalMemory() - runtime.freeMemory()
        val headroom = runtime.maxMemory() - used
        return if (headroom <= 0) 0L else (headroom * TRANSIENT_FRACTION).toLong()
    }
}
