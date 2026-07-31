package evola.core.kernel

enum class CefrLevel(val code: String) {
    A2_1("A2.1"),
    A2_2("A2.2"),
    B1("B1"),
    A1("A1"),
    A2("A2"),
    B2("B2"),
    C1("C1"),
    C2("C2"),
    UNKNOWN("UNKNOWN");

    companion object {
        /** Lenient — vocabulary can now be AI-extracted from arbitrary text, so an unrecognized
         * code must never crash the pipeline; it falls back to [UNKNOWN] instead of throwing. */
        fun fromCode(code: String): CefrLevel =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
