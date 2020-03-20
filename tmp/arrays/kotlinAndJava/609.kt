//File Main.kt
// IGNORE_BACKEND_FIR: JVM_IR
// TARGET_BACKEND: JVM

// WITH_REFLECT

fun box(): String {
    val l = object : C({}) {
    }

    val javaClass = l.a.javaClass
    if (javaClass.getEnclosingConstructor() != null) return "ctor should be null"

    val enclosingMethod = javaClass.getEnclosingMethod()!!.getName()
    if (enclosingMethod != "box") return "method: $enclosingMethod"

    val enclosingClass = javaClass.getEnclosingClass()!!.getName()
    if (enclosingClass != "LambdaInObjectLiteralSuperCallKt" || enclosingClass != l.javaClass.getEnclosingClass()!!.getName())
        return "enclosing class: $enclosingClass"

    val declaringClass = javaClass.getDeclaringClass()
    if (declaringClass != null) return "anonymous function has a declaring class: $declaringClass"

    return "OK"
}



//File C.java
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

public class C {
   @NotNull
   private final Object a;

   @NotNull
   public final Object getA() {
      return this.a;
   }

   public C(@NotNull Object a) {
      super();
      this.a = a;
   }
}
