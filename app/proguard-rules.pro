# kotlinx.serialization and Room ship their own consumer rules; these are belt-and-braces.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.evolet.tachyon.**$$serializer { *; }
-keepclassmembers class com.evolet.tachyon.** { *** Companion; }
-keepclasseswithmembers class com.evolet.tachyon.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp optional platform classes
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
