# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.example.mahjong.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.mahjong.** {
    kotlinx.serialization.KSerializer serializer(...);
}
