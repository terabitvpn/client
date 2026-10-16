# Xray core (gomobile bindings are looked up from native code by name)
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keepclassmembers class * implements libv2ray.CoreCallbackHandler { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp optional deps
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
