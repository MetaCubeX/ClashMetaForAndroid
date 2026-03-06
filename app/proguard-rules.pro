-verbose
-allowaccessmodification
-repackageclasses

-keepattributes SourceFile,
                LineNumberTable

-renamesourcefileattribute SourceFile

-dontobfuscate

# Remove some Kotlin overhead
-processkotlinnullchecks remove
