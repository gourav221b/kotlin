// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE -UNUSED_PARAMETER -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-253
 * PLACE: overload-resolution, callables-and-invoke-convention -> paragraph 6 -> sentence 1
 * NUMBER: 1
 * DESCRIPTION: we
 */

// TESTCASE NUMBER: 1

class MyClass {

    //function-like (I prio)
    var fooFun: Boolean = false
    fun foo() { fooFun = true }

    //property-like (II prio)
    companion object foo {
        var fooCompanionObj = false
        operator fun invoke() {
            fooCompanionObj = true
        }
    }

    fun check() : String {
        foo()
        <!DEBUG_INFO_FQ_NAME("")!>foo<!>()

        if (fooFun && !fooCompanionObj)
            return "OK"
        return "NOK"
    }


}