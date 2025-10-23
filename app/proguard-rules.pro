
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Dagger/Hilt (если используете)
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.**
-keep class **$$Module { *; }
-keep class **$$InstanceFactory { *; }
-keep class **$$Component { *; }
-keep class **$$Subcomponent { *; }
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
-keep class androidx.hilt.** { *; }
-dontwarn androidx.hilt.**
-keepclasseswithmembers class * {
    @dagger.hilt.** <methods>;
}


# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Подавление предупреждений для java.beans
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.ScriptEngineFactory

-keep class coil3.RealImageLoader { *; }
-keep class coil3.util.SystemCallbacks { *; }
-keep class coil3.util.AndroidSystemCallbacks { *; }
-keep class coil3.SingletonImageLoader { *; }

-keep class com.example.groviq.** { *; }

-keep class coil3.** { *; }
-dontwarn coil3.**

-keep class coil3.network.okhttp.** { *; }
-dontwarn coil3.network.okhttp.**

-keep class coil3.compose.** { *; }
-dontwarn coil3.compose.**