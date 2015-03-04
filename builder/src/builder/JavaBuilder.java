package builder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;



public class JavaBuilder {
	public static class BuildException extends Exception {
		private static final long serialVersionUID = 1L;

	}

	private JavaCompiler javac;
	private Pattern writtenFilePattern = Pattern.compile("\\[wrote RegularFileObject\\[([^\\]]+)\\]\\]");
	private Pattern dependencyPattern = Pattern.compile("\\s*(\\S+)\\.class\\s+\\->\\s+(\\S+)\\.class\\s*");

	public JavaBuilder() {
		javac = ToolProvider.getSystemJavaCompiler();
	}

	public HashSet<String> buildFull(Path src, Path dest, Map<String, Set<String>> invertedDependencies) throws IOException, BuildException {
		Set<String> sourceFiles = Files.walk(src).filter(p -> !Files.isDirectory(p)).map(p -> p.toString()).collect(Collectors.toSet());
		return build(src, sourceFiles, dest, invertedDependencies);
	}

	public HashSet<String> build(Path src, Set<String> sourceFiles, Path dest, Map<String, Set<String>> invertedDependencies)
			throws IOException, BuildException {
		Files.createDirectories(dest);
		Map<String, Set<String>> copyOfInvertedDependencies = new HashMap<>(invertedDependencies); 
		HashSet<String> writtenFiles = new HashSet<>();
		boolean dependenciesDetected = false;
		do {
			dependenciesDetected = false;
			HashSet<String> allDiscoveredDependants = new HashSet<>();
			for (String sourceFile : sourceFiles) {
				Set<String> dependants = copyOfInvertedDependencies.get(sourceFile);
				if (dependants != null && !dependants.isEmpty()) {
					dependenciesDetected = true;
					allDiscoveredDependants.addAll(dependants);
					copyOfInvertedDependencies.remove(sourceFile);
				}
			}
			sourceFiles.addAll(allDiscoveredDependants);
		}
		while(dependenciesDetected);
		
		
		StandardJavaFileManager standardFileManager = javac.getStandardFileManager(null, null, null);
		Iterable<? extends JavaFileObject> javaFileObjects = standardFileManager.getJavaFileObjectsFromStrings(sourceFiles);
		DiagnosticCollector<JavaFileObject> dCollect = new DiagnosticCollector<JavaFileObject>();
		StringWriter stringWriter = new StringWriter();
		CompilationTask task = 
				javac.getTask(
						stringWriter, 
						standardFileManager, 
						dCollect, 
						Arrays.asList(
								"-verbose", 
								"-cp", dest.toString(), 
								"-d", dest.toString()), 
						null, 
						javaFileObjects);
		task.call();
//		System.out.println(stringWriter.toString());
		new BufferedReader(new StringReader(stringWriter.toString())).lines().forEach(l -> {
			Matcher matcher = writtenFilePattern.matcher(l);
			if (matcher.matches()){
				String classFilename = matcher.group(1);
				writtenFiles.add(classFilename);
//				System.out.println(classFilename);
			}
		});
		if (!dCollect.getDiagnostics().isEmpty()) {
			throw new BuildException();
		}
		stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		String[] array = writtenFiles.toArray(new String[writtenFiles.size() + 1]);
		array[array.length-1] = array[0];
		array[0] = "-v";
		com.sun.tools.jdeps.Main.run(array, printWriter);
//		System.out.println(stringWriter.toString());
		String destPrefixPattern = dest.toString();
		String srcPrefix = src.toString();
//		System.out.println(srcPrefix + " " + destPrefixPattern);
		new BufferedReader(new StringReader(stringWriter.toString())).lines().forEach(l -> {
			Matcher matcher = dependencyPattern.matcher(l);
			if (matcher.matches()){
				String classFilename = srcPrefix + matcher.group(1).substring(destPrefixPattern.length());
				String dependencyClassFilename = srcPrefix + matcher.group(2).substring(destPrefixPattern.length());
				String key = dependencyClassFilename + ".java";
				Set<String> set = copyOfInvertedDependencies.get(key);
				if (set == null) { 
					set = new HashSet<>();
					copyOfInvertedDependencies.put(key, set);
				}
				set.add(classFilename + ".java");
//				System.out.println(classFilename);
			}
		});
//		System.out.println(invertedDependencies);
		invertedDependencies.clear();
		invertedDependencies.putAll(copyOfInvertedDependencies);
		return writtenFiles;
	}
}
