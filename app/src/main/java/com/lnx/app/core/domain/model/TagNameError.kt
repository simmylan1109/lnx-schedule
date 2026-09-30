package com.lnx.app.core.domain.model

/**
 * 标签名不合法的原因(spec §3.10)。
 *
 * **数据层只报"哪种错",不报中文句子**:文案归界面按当前语言渲染
 * (中文/英文,见 `tag_error_*`),否则换个语言就还是中文报错。
 */
enum class TagNameError {
    EMPTY,
    DUPLICATE,
}

/** 建标签失败;`name` 只在 [TagNameError.DUPLICATE] 时有值,供文案里回显用户输入的名字 */
class TagNameException(
    val kind: TagNameError,
    val name: String? = null,
) : IllegalArgumentException("tag name rejected: $kind")
