//File A.java
import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

public interface A {
   @NotNull
   String f();

   @NotNull
   String g();
}


//File Main.kt
// IGNORE_BACKEND_FIR: JVM_IR
// TARGET_BACKEND: JVM

// WITH_RUNTIME

import kotlin.test.assertEquals

fun foo(block: () -> String) = block()

fun box(): String {
    val x: A = object : A {
        private inline fun <reified T : Any> localClassName(): String = T::class.java.getName()
        override fun f(): String = foo { localClassName<String>() }
        override fun g(): String = foo { localClassName<Int>() }
    }

    assertEquals("java.lang.String", x.f())
    assertEquals("java.lang.Integer", x.g())

    return "OK"
}

