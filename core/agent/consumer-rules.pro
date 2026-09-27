# JNA：本地库按字段名与类名反射（`Pointer.peer`、`Structure` 的字段与 `getFieldOrder`），
# R8 全量模式一改名，构造任何 JNA 结构就崩：
#   UnsatisfiedLinkError: Can't obtain peer field ID for class com.sun.jna.Pointer
# 本模块依赖 JNA（UniFFI 生成的绑定跑在它上面），所以规则归本模块所有，
# 任何消费它的应用都会自动带上。
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-dontwarn java.awt.**
