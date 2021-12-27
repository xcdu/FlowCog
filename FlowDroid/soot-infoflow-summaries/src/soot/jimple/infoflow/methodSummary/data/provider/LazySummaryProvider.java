package soot.jimple.infoflow.methodSummary.data.provider;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * This class loads method summary xml files on demand.
 *
 */
public class LazySummaryProvider extends XMLSummaryProvider {

	/**
	 * Loads a summary from a folder within the StubDroid jar file.
	 * 
	 * @param folderInJar
	 *            The folder in the JAR file from which to load the summary files
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	public LazySummaryProvider(String folderInJar) throws URISyntaxException, IOException {
		super(folderInJar);
	}

	/**
	 * Loads a file or all files in a dir (not recursively)
	 * 
	 * @param source
	 */
	public LazySummaryProvider(File source) {
		super(source);
	}

	public LazySummaryProvider(List<File> files) {
		super(files);
	}
}
