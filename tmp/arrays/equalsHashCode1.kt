// IGNORE_BACKEND_FIR: JVM_IR
// IGNORE_BACKEND: JS

fun box(): String {
    fun bar() {}
    fun baz() {}

    if (!::bar.equals(::bar)) return "Fail 1"
    if (::bar.hashCode() != ::bar.hashCode()) return "Fail 2"

    if (::bar == ::baz) return "Fail 3"

    return "OK"
}
