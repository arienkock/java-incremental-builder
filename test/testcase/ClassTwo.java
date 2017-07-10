package testcase;

public class ClassTwo {
	// Class two depends on class one
	{
		new ClassOne().placeholderMethod();
	}
}
