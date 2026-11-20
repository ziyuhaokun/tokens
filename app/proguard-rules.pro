# release 走 R8：保留规则只加真正需要反射 / 序列化的东西。
# Compose、Glance 自带 consumer rules，这里不用重复写。

# Glance 的小组件 ActionCallback 由框架按类名实例化，不能被裁掉或改名。
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }

# Kotlin 协程内部通过反射找 debug 元数据，缺了会有警告噪音。
-dontwarn kotlinx.coroutines.**
