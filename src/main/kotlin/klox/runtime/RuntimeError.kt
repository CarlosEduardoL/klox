package klox.runtime

import klox.compiletime.Token

class RuntimeError(val token: Token, message: String) : RuntimeException(message)