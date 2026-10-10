# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.artem.gigatv.**$$serializer { *; }
-keepclassmembers class com.artem.gigatv.** {
    *** Companion;
}
-keepclasseswithmembers class com.artem.gigatv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
