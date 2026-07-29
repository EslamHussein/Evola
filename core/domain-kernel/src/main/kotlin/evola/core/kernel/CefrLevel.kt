package evola.core.kernel

enum class CefrLevel(val code: String) {
    A2_1("A2.1"),
    A2_2("A2.2"),
    B1("B1");

    companion object {
        fun fromCode(code: String): CefrLevel =
            entries.first { it.code == code }
    }
}
