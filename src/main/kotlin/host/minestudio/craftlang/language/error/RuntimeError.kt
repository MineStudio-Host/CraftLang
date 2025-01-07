package host.minestudio.craftlang.language.error

import host.minestudio.craftlang.language.scanning.Token

class RuntimeError(val token: Token, message: String) : RuntimeException(message)