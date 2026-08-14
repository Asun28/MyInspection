package nz.myinspection.core

/**
 * :core 模块存在性标记（T0-TOOLCHAIN 骨架卡）。
 * 本模块是纯 JVM 领域层：model / db(SQLDelight) / template / compliance / report / backup / canon
 * 均落于此，禁止依赖任何 android.* 包（见 CLAUDE.md 架构大图）。业务实现由后续任务卡填充。
 */
object CoreModule {
    const val NAME: String = "core"
}
