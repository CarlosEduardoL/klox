# KLox
KLox is an interpreter for the Lox language, which I implemented in Kotlin following the instructions in the second part of the book "Crafting Interpreters". I highly recommend this book to anyone interested in learning how to create programming language interpreters.

## Getting Started
To try out the interpreter, you can use the scripts in the lox_scripts directory. I modified the scanner to wait until a } or ) is encountered in interactive mode before evaluating the entire context to avoid errors.

To build the jar file, you can use Gradle. Run the following command in the root directory of the project:

bash
```bash
./gradlew jar
```

This will generate a jar file in the build/libs directory. To run the CLI, you can use the following command:

```bash
java -jar build/libs/klox-0.1.0-all.jar [script]
```

If you provide a script file as an argument, the interpreter will run the script. Otherwise, it will enter interactive mode.

## Conclusion
I really enjoyed working through "Crafting Interpreters" and creating KLox in Kotlin. I highly recommend this book to anyone interested in learning about programming language interpreters.

