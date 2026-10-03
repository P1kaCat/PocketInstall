plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("app.pocketinstall.server.DevMainKt") }
dependencies { testImplementation("junit:junit:4.13.2") }
