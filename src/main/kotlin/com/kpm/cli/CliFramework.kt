package com.kpm.cli

abstract class Command(
    val name: String,
    val help: String
) {
    protected val arguments = mutableListOf<ArgumentDefinition>()
    protected val options = mutableListOf<OptionDefinition>()
    
    abstract fun run()
    
    protected fun argument(name: String, help: String = ""): ArgumentDelegate {
        val arg = ArgumentDefinition(name, help)
        arguments.add(arg)
        return ArgumentDelegate(arg)
    }
    
    protected fun option(name: String, help: String = ""): OptionDelegate {
        val opt = OptionDefinition(name, help)
        options.add(opt)
        return OptionDelegate(opt)
    }
    
    fun parse(args: Array<String>): Boolean {
        val argsList = args.toMutableList()
        
        // Parse options first
        val iterator = argsList.iterator()
        while (iterator.hasNext()) {
            val arg = iterator.next()
            if (arg.startsWith("--")) {
                val optionName = arg.substring(2)
                val option = options.find { it.name == optionName }
                if (option != null) {
                    iterator.remove()
                    if (option.hasValue && iterator.hasNext()) {
                        option.value = iterator.next()
                        iterator.remove()
                    } else if (!option.hasValue) {
                        option.value = "true"
                    }
                }
            }
        }
        
        // Parse positional arguments
        arguments.forEachIndexed { index, argDef ->
            if (index < argsList.size) {
                argDef.value = argsList[index]
            }
        }
        
        // Store remaining arguments for flexible parsing
        if (argsList.size > arguments.size) {
            for (i in arguments.size until argsList.size) {
                val extraArg = ArgumentDefinition("extra_$i", "Extra argument")
                extraArg.value = argsList[i]
                arguments.add(extraArg)
            }
        }
        
        return true
    }
    
    fun showHelp() {
        println("Usage: kpm $name [options] ${arguments.joinToString(" ") { "<${it.name}>" }}")
        println()
        println(help)
        
        if (arguments.isNotEmpty()) {
            println()
            println("Arguments:")
            arguments.forEach { arg ->
                println("  ${arg.name.padEnd(20)} ${arg.help}")
            }
        }
        
        if (options.isNotEmpty()) {
            println()
            println("Options:")
            options.forEach { opt ->
                println("  --${opt.name.padEnd(18)} ${opt.help}")
            }
        }
    }
}

data class ArgumentDefinition(
    val name: String,
    val help: String,
    var value: String? = null
)

data class OptionDefinition(
    val name: String,
    val help: String,
    var hasValue: Boolean = true,
    var value: String? = null
)

class ArgumentDelegate(private val arg: ArgumentDefinition) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String {
        return arg.value ?: error("Required argument '${arg.name}' not provided")
    }
}

class OptionDelegate(private val opt: OptionDefinition) {
    fun flag(): FlagDelegate {
        opt.hasValue = false
        return FlagDelegate(opt)
    }
    
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String? {
        return opt.value
    }
}

class FlagDelegate(private val opt: OptionDefinition) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Boolean {
        return opt.value == "true"
    }
}

fun echo(message: String, err: Boolean = false) {
    if (err) {
        System.err.println(message)
    } else {
        println(message)
    }
}
