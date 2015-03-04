# Proof-of-concept Incremental Builder
The `jdeps` command is new in Java8. With it you can analyze class dependencies. Using this feature (called programmatically at `com.sun.tools.jdeps.Main`) this builder keeps track of the graph so that subsequent requests to build *.java files will also rebuild dependant source files.

See the unit test for example usage.

# Benefits
1. Detects breaking changes in sources that would normally require a clean full build
2. Is faster because it compiles fewer files

# Improvements on this concept
1. Support for inner classes (Class$names.class), JAR's and irregularities.
2. Don't hold all of the tools' output in memory with `StringWriter`.
3. Persist the dependency graph to disk so jdeps doesn't have to be run again on a fresh JVM (which is probably faster).
4. A WatchService option for continuous rebuilding
5. Auto clean class files on delete (more WatchService)

# Why this isn't very useful
IDE's can already do this

