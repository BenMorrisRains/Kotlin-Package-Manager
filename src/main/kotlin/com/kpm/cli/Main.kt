package com.kpm.cli

import com.kpm.cli.commands.*

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        showHelp()
        return
    }
    
    val commandName = args[0]
    val commandArgs = args.drop(1).toTypedArray()
    
    val command = when (commandName) {
        "init" -> InitCommand()
        "new" -> NewCommand()
        "add" -> AddCommand()
        "remove" -> RemoveCommand()
        "install" -> InstallCommand()
        "update" -> UpdateCommand()
        "build" -> BuildCommand()
        "test" -> TestCommand()
        "run" -> RunCommand()
        "search" -> SearchCommand()
        "info" -> InfoCommand()
        "doctor" -> DoctorCommand()
        "android" -> AndroidCommand()
        "config" -> ConfigCommand()
        "sync" -> SyncCommand()
        "--help", "-h" -> {
            showHelp()
            return
        }
        else -> {
            echo("Unknown command: $commandName", err = true)
            echo("Run 'kpm --help' for usage information", err = true)
            return
        }
    }
    
    if (commandArgs.contains("--help") || commandArgs.contains("-h")) {
        command.showHelp()
        return
    }
    
    try {
        command.parse(commandArgs)
        command.run()
    } catch (e: Exception) {
        echo("Error: ${e.message}", err = true)
    }
}

private fun showHelp() {
    println("Usage: kpm [<options>] <command> [<args>]...")
    println()
    println("Kotlin Package Manager")
    println()
    println("Options:")
    println("  -h, --help  Show this message and exit")
    println()
    println("Commands:")
    println("  init     Initialize a new KPM project")
    println("  new      Create a new project with smart defaults")
    println("  add      Add a dependency to the project")
    println("  remove   Remove a dependency from the project")
    println("  install  Install dependencies")
    println("  update   Update dependencies")
    println("  sync     Regenerate build files from kpm.toml")
    println("  build    Build the project")
    println("  test     Run tests")
    println("  run      Run the application")
    println("  search   Search Maven Central for dependencies")
    println("  info     Show information about a dependency")
    println("  doctor   Check project health and configuration")
    println("  android  Android SDK management")
    println("  config   Manage global KPM configuration")
    println()
    println("Examples:")
    println("  kpm new MyApp --android --compose    # Create Android app with Compose")
    println("  kpm add picasso                      # Add latest Picasso (npm-like)")
    println("  kpm add retrofit --test              # Add as test dependency")
    println("  kpm search image                     # Search for image libraries")
}
