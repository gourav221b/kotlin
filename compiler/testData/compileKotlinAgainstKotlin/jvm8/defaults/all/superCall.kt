// JVM_TARGET: 1.8
// WITH_RUNTIME
// FILE: 1.kt
// !JVM_DEFAULT_MODE: all-no-default
interface Test {
    fun test(): String {
        return "OK"
    }
}

// FILE: 2.kt
// !JVM_DEFAULT_MODE: all
class TestClass : Test {
    override fun test(): String {
        return super.test()
    }
}

fun box(): String {
    return TestClass().test()
}
