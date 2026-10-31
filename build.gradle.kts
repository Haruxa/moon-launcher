plugins {
    // AGP 9 несёт собственный Kotlin и сам подключает compose-компилятор
    // (buildFeatures.compose = true), отдельные KGP-плагины больше не нужны.
    id("com.android.application") version "9.1.1" apply false
}
