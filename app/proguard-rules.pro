# Keep model classes used by kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.audionotes.app.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.audionotes.app.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
