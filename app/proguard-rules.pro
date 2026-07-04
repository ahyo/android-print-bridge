# SDK Epson ePOS2 memuat kode native (JNI) yang memanggil balik kelas Java-nya;
# jangan diobfuscate/di-strip.
-keep class com.epson.epos2.** { *; }
-keep class com.epson.eposprint.** { *; }
