package octo.cli

import com.github.ajalt.clikt.core.main
import octo.core.paths.currentHostOs

fun main(args: Array<String>) =
    Octo(System.getenv(), System.getProperty("user.home"), System.getProperty("user.dir"), currentHostOs()).main(args)
