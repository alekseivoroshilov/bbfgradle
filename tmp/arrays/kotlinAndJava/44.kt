//File Main.kt
// IGNORE_BACKEND_FIR: JVM_IR
// IGNORE_BACKEND: NATIVE
// WITH_RUNTIME

import kotlin.reflect.KClass
import kotlin.test.assertEquals

fun check(klass: KClass<*>, expectedName: String) {
    assertEquals(expectedName, klass.simpleName)
}

fun localInMethod() {
    fun localInMethod(unused: Any?) {
        class Local
        check(Local::class, "Local")

        class `Local$With$Dollars`
        check(`Local$With$Dollars`::class, "Local\$With\$Dollars")
    }
    localInMethod(null)

    class Local
    check(Local::class, "Local")

    class `Local$With$Dollars`
    check(`Local$With$Dollars`::class, "Local\$With\$Dollars")
}

fun box(): String {
    localInMethod()
    LocalInConstructor()
    return "OK"
}



//File LocalInConstructor.java
import kotlin.Metadata;
import kotlin.jvm.internal.Reflection;

public final class LocalInConstructor {
   public LocalInConstructor() {
      final class Local {
         public Local() {
         }
      }

      MainKt.check(Reflection.getOrCreateKotlinClass(Local.class), "Local");

      final class Local$With$Dollars {
         public Local$With$Dollars() {
         }
      }

      MainKt.check(Reflection.getOrCreateKotlinClass(Local$With$Dollars.class), "Local$With$Dollars");
   }
}
