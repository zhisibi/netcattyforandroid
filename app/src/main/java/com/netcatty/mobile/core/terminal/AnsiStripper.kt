package com.netcatty.mobile.core.terminal

/**
 * 简易 ANSI 转义码剥离器。
 * 在没有 Termux TerminalEmulator 的情况下，
 * 用于清理 SSH 输出中的控制序列，只保留可读文本。
 */
object AnsiStripper {

    private val ANSI_PATTERN = Regex("""\x1B\[[0-9;]*[A-Za-z]|\x1B\][^\x07]*\x07|\x1B\[[\?]?[0-9;]*[A-Za-z]|\x1B[()][AB012]|\x1B\[[0-9;]*m|\r|\x07""")

    /**
     * 剥离 ANSI 转义序列，保留可读文本。
     * 保留换行符 \n。
     */
    fun strip(input: String): String {
        return ANSI_PATTERN.replace(input, "")
    }

    /**
     * 处理退格 (0x08) 和回车 (\r)
     */
    fun processBackspace(input: String): String {
        val result = StringBuilder()
        for (ch in input) {
            if (ch == '\b') {
                if (result.isNotEmpty()) {
                    result.deleteCharAt(result.length - 1)
                }
            } else {
                result.append(ch)
            }
        }
        return result.toString()
    }
}
