package app.pocketinstall.server

import java.io.File

/** CI media inspection. Does not execute, redistribute, modify or install the source ISO. */
fun main(args: Array<String>) {
    require(args.size == 2)
    val source=File(args[0]); val output=File(args[1])
    WindowsIso.extract(source,output)
    val info=WindowsImageInfo(output.length(),WindowsImage.hash(output),WindowsImage.inspect(output))
    check(info.selected(WindowsSelection(edition=WindowsEdition.HOME)).index > 0)
    check(info.selected(WindowsSelection(edition=WindowsEdition.PRO)).index > 0)
    println("Windows 11 Home and Pro x64 detected. bytes=${info.bytes} sha256=${info.sha256}")
}
