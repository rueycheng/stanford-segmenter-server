//--------------------------------------------------
// StanfordChineseSegmenterServer
// 
// -- Ruey-Cheng Chen <cobain@turing.csie.ntu.edu.tw>
//-------------------------------------------------- 

package tw.edu.ntu.csie;

import java.io.*;
import java.net.*;
import java.util.*;
import com.sun.net.httpserver.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.objectbank.ObjectBank;

public class StanfordChineseSegmenterServer {
    public class SystemOutKeeper {
	private PrintStream out = System.out;

	public void finalize() { 
	    if (System.out != out) System.setOut(out); 
	}
    }

    public class ClassifierHandler implements HttpHandler {
	protected CRFClassifier classifier;

	public ClassifierHandler(CRFClassifier classifier) {
	    this.classifier = classifier;
	}

	public void handle(HttpExchange exchange) {
	    PrintStream out = null;
	    SystemOutKeeper keeper = new SystemOutKeeper();

	    try {
		// Turn down all the non-POST requests 
		if (!exchange.getRequestMethod().equals("POST")) {
		    exchange.sendResponseHeaders(501, 0);
		    exchange.close();
		    return;
		}

		Headers headers = exchange.getResponseHeaders();
		headers.add("Content-Type", "text/plain");

		// Slurp all the input
		File workFile = File.createTempFile("stanford-chinese-segmenter-server-", null);
		if (!saveRequestBodyToFile(exchange, workFile)) throw new IOException();

		exchange.sendResponseHeaders(200, 0);
		out = new PrintStream(exchange.getResponseBody());

		// Override the stdout (so that the client get to access the result)
		System.setOut(out);
		classifier.testAndWriteAnswers(workFile.getAbsolutePath());

		// I'm not sure this is the right thing to do
		// (Can we have a TempFileManager that keep only the last 100 files in the filesystem?)
		workFile.deleteOnExit(); 
		workFile.delete();
	    }
	    catch (Exception e) {}
	    finally {
		try {
		    if (exchange.getResponseCode() == -1) 
			exchange.sendResponseHeaders(500, 0);
		    if (out != null) out.close();
		}
		catch (IOException e) { e.printStackTrace(); }
		finally { exchange.close(); }
	    }
	}

	public boolean saveRequestBodyToFile(HttpExchange exchange, File file) {
	    boolean success = true;
	    InputStream in = null;
	    OutputStream out = null;

	    try {
		in = exchange.getRequestBody();
		out = new FileOutputStream(file);
		byte[] buf = new byte[4096]; // Magic number, huh.
		int byteRead = 0;
		while ((byteRead = in.read(buf)) >= 0) out.write(buf, 0, byteRead);
	    }
	    catch (IOException e) { 
		success = false;
		e.printStackTrace(); 
	    }
	    finally {
		try { if (in != null) in.close(); } catch (IOException e) { }
		try { if (out != null) out.close(); } catch (IOException e) { }
	    }

	    return success;
	}
    }

    protected HttpServer httpServer;
    protected CRFClassifier classifier;

    public StanfordChineseSegmenterServer(CRFClassifier classifier, int port) throws IOException {
	httpServer = HttpServer.create(new InetSocketAddress(port), 0);
	httpServer.createContext("/", new ClassifierHandler(classifier));
    }

    public void start() { httpServer.start(); }
    public void stop(int delay) { httpServer.stop(delay); }

    public static void main(String[] args) {
	if (args.length != 2) {
	    PrintStream err = System.err;
	    err.println("usage: StanfordChineseSegmenterServer DATADIR PORT");
	    err.println();
	    err.println("    DATADIR    Path to the stanford chinese segmenter data directory");         
	    err.println("               i.e., 'stanford-chinese-segmenter-2008-05-21/data'");         
	    err.println("    PORT       The server port number");
	    return;
	}

	File dataDir = new File(args[0]);
	if (!dataDir.exists() || !dataDir.isDirectory()) {
	    System.err.println("Data directory does not exist");
	    System.exit(1);
	}

	File dictFile = new File(dataDir, "dict-chris6.ser.gz");
	File ctbFile = new File(dataDir, "ctb.gz");

	if (!dictFile.exists()) {
	    System.err.println("File " + dictFile.getName() + " does not exist");
	    System.exit(1);
	} 
	else if (!ctbFile.exists()) {
	    System.err.println("File " + ctbFile.getName() + " does not exist");
	    System.exit(1);
	}

	int port = Integer.parseInt(args[1]);

	// Now, set things up
	try {
	    Properties props = new Properties();
	    props.setProperty("sighanCorporaDict", dataDir.getCanonicalPath());
	    props.setProperty("serDictionary", dictFile.getCanonicalPath());
	    props.setProperty("inputEncoding", "UTF-8");
	    props.setProperty("sighanPostProcessing", "true");

	    CRFClassifier classifier = new CRFClassifier(props);
	    classifier.loadClassifierNoExceptions(ctbFile.getCanonicalPath(), props);
	    classifier.flags.setProperties(props); // flags must be re-set after data is loaded

	    StanfordChineseSegmenterServer server = 
		new StanfordChineseSegmenterServer(classifier, port);
	    server.start();
	}
	catch (IOException e) { e.printStackTrace(); }
    }
}
