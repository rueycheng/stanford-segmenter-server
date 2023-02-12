package tw.edu.ntu.csie;

import java.io.*;
import java.net.*;
import java.util.*;
import com.sun.net.httpserver.*;

import edu.stanford.nlp.ie.crf.CRFClassifier;

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
		workFile.deleteOnExit();

		if (!saveRequestBodyToFile(exchange, workFile)) throw new IOException();

		exchange.sendResponseHeaders(200, 0);
		out = new PrintStream(exchange.getResponseBody());

		// Override the stdout (so that the client get to access the result)
		System.setOut(out);
		classifier.testAndWriteAnswers(workFile.getAbsolutePath());
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

    public static CRFClassifier createDefaultCRFClassifier() {
	Properties props = new Properties();
	props.setProperty("sighanCorporaDict", "data");
	props.setProperty("serDictionary","data/dict-chris6.ser.gz");
	props.setProperty("inputEncoding", "UTF-8");
	props.setProperty("sighanPostProcessing", "true");

	CRFClassifier classifier = new CRFClassifier(props);
	classifier.loadClassifierNoExceptions("data/ctb.gz", props);
	classifier.flags.setProperties(props); // flags must be re-set after data is loaded
	return classifier;
    }

    public StanfordChineseSegmenterServer(int port) throws IOException {
	this(createDefaultCRFClassifier(), port);
    }

    public StanfordChineseSegmenterServer(CRFClassifier classifier, int port) throws IOException {
	httpServer = HttpServer.create(new InetSocketAddress(port), 0);
	httpServer.createContext("/", new ClassifierHandler(classifier));
    }

    public void start() { httpServer.start(); }
    public void stop(int delay) { httpServer.stop(delay); }

    public static void main(String[] args) {
	try {
	    StanfordChineseSegmenterServer server = new StanfordChineseSegmenterServer(8080);
	    server.start();
	}
	catch (IOException e) { e.printStackTrace(); }
    }
}
