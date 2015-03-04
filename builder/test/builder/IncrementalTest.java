package builder;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import builder.JavaBuilder.BuildException;

public class IncrementalTest {
	Path workDir = Paths.get("temp");
	Path classOnePath;
	Path classTwoPath;
	Path unrelatedClassPath;
	
	@Before
	public void setup() throws IOException, InterruptedException {
		Files.walkFileTree(workDir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
		Path src = Paths.get("test","testcase");
		Path dest = workDir.resolve("src").resolve("testcase");
		Files.createDirectories(dest);
		Files.createDirectories(workDir);
		classOnePath = dest.resolve("ClassOne.java");
		Files.copy(src.resolve("ClassOne.java"), classOnePath);
		classTwoPath = dest.resolve("ClassTwo.java");
		Files.copy(src.resolve("ClassTwo.java"), classTwoPath);
		unrelatedClassPath = dest.resolve("UnrelatedClass.java");
		Files.copy(src.resolve("UnrelatedClass.java"), unrelatedClassPath);
	}
	
	@Test
	public void incrementalTest() throws IOException, BuildException {
		JavaBuilder jb = new JavaBuilder();
		Path srcDir = workDir.resolve("src");
		
		/*
		 * First build, gather and check dependency info 
		 */
		HashMap<String, Set<String>> invertedDeps = new HashMap<String, Set<String>>();
		HashSet<String> writtenFiles = jb.buildFull(srcDir, workDir.resolve("build"), invertedDeps);
		assertEquals("three files written", 3, writtenFiles.size());
		Set<String> dependantsOfClassOne = invertedDeps.get(classOnePath.toString());
		assertTrue("ClassTwo should depend on ClassOne", dependantsOfClassOne.contains(classTwoPath.toString()));
		assertTrue("UnrelatedClass isn't dependant on anything", !invertedDeps.containsKey(unrelatedClassPath.toString()));
		
		/*
		 * Second build, don't use dependency info, explicitly compile ClassOne and make sure it's the only one rebuilt
		 */
		Map<String, FileTime> timeMap = getTimeMap(writtenFiles);
		Set<String> sources = new HashSet<String>(Arrays.asList(srcDir.resolve("testcase").resolve("ClassOne.java").toString()));
		// pass empty inverted dependency map, so ClassTwo will be ignored
		HashSet<String> writtenFilesSecondTime = jb.build(srcDir, sources, workDir.resolve("build"), new HashMap<String, Set<String>>());
		Map<String, FileTime> secondTimeMap = getTimeMap(writtenFiles);
		assertEquals("one file written", 1, writtenFilesSecondTime.size());
		assertNotEquals("testing -Xprefer:NEWER only ClassOne should be built", timeMap.get("ClassOne"), secondTimeMap.get("ClassOne"));
		
		/*
		 * Break the build, but don't detect any changes because without dependency info the builder doesn't know that ClassTwo is affected
		 */
		breakTheBuild();
		// pass empty inverted dependency map, so ClassTwo will be ignored
		HashSet<String> writtenFilesThirdTime = jb.build(srcDir, sources, workDir.resolve("build"), new HashMap<String, Set<String>>());
		Map<String, FileTime> thirdTimeMap = getTimeMap(writtenFiles);
		assertEquals("one file written and no break detected", 1, writtenFilesThirdTime.size());
		assertEquals("testing -Xprefer:NEWER ClassTwo should not be built", timeMap.get("ClassTwo"), thirdTimeMap.get("ClassTwo"));
		
		/*
		 * Build again but with dependency map, so ClassOne will be recompiled and break detected
		 */
		boolean failed = false;
		try {
			jb.build(srcDir, sources, workDir.resolve("build"), invertedDeps);
		} catch (BuildException e) {
			failed = true;
		}
		assertTrue("Build should fail", failed);

		/*
		 * Fix compile error in ClassTwo and use dependency graph to detect the needed recompile
		 */
		fixTheBuild();
		HashSet<String> writtenFilesFourthTime = jb.build(srcDir, sources, workDir.resolve("build"), invertedDeps);
		assertEquals("two files written", 2, writtenFilesFourthTime.size());
	}

	private void fixTheBuild() throws IOException {
		Files.write(
				classTwoPath, 
				Files.readAllLines(classTwoPath).stream().map(l -> l.contains("new ClassOne().placeholderMethod();") ? "\tnew ClassOne().breakingMethod();" : l ).collect(Collectors.toList())
			);
	}

	private void breakTheBuild() throws IOException {
		Files.write(
				classOnePath, 
				Files.readAllLines(classOnePath).stream().map(l -> l.contains("placeholderMethod") ? "\tpublic void breakingMethod() {}" : l ).collect(Collectors.toList())
			);
	}

	private Map<String, FileTime> getTimeMap(HashSet<String> writtenFiles) {
		return writtenFiles.stream().collect(
				Collectors.toMap(s -> {
					return s.substring(s.lastIndexOf("\\")+1, s.lastIndexOf(".class"));
				}, 
				v -> {
					try {
						return Files.getLastModifiedTime(Paths.get(v));
					} catch (Exception e) {
					}
					return null;
				}));
	}
}
