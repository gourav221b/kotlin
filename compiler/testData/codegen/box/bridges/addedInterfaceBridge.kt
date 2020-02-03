interface A<T> {
    fun f(x: T): T
}

open class B {
    open fun f(x: String): String = x
}

open class C : B(), A<String>

// class D should not have an additional bridge
class D : C()

fun box(): String {
    return (D() as A<String>).f("OK")
}
