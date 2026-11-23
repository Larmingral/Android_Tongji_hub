# Keep Kotlin metadata and kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.campushub.**$$serializer { *; }
-keepclassmembers class com.example.campushub.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.campushub.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep JavaMail / JAF classes loaded dynamically via ServiceLoader & MailcapCommandMap
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.activation.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn java.beans.**

# Suppress optional JVM platform warnings for OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
