package nl.architolk.ldtcmd;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

import java.util.HashMap;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.StringBufferInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream; 
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/*
	The CreatePage class give an example of a "pipe" of transformations and sparql requests
	This example is only for architectural purposes, it's not production ready
	
	One obvious functional improvement whould be creating a multithreaded application, to allow actual streaming of input to output data
	At this moment, although PipedInputStream/PipedOutputStream are used, a large buffer is necessary to prevent deadlock of the application
	
*/

public class CreatePage {
	
	private static final String CONFIG_TEMPLATE = "<input><stage>http://localhost:8080/stage</stage><representation>http://localhost:8080/stage#REPRESENTATION</representation></input>";
	private static final String CONTEXT = "<context staticroot='.'><title>Command line LDT</title></context>";
	private static int PIPE_BUFFER = 1000000; // Large buffer size to prevent deadlock. Better solution would be a multi-treaded application
	private static final String SPARQL_ENDPOINT = "http://localhost:8890/sparql";
	private static final String LOCAL_FLAG = "local";
	
	private static final TransformerFactory tfactory = TransformerFactory.newInstance();

	private static void transform(StreamSource source, String xslResource, StreamResult result) throws TransformerConfigurationException,TransformerException { 

		// Create input stream for the actual resource
		InputStream xslStream = CreatePage.class.getClassLoader().getResourceAsStream(xslResource);
	
		// Create a transformer for the stylesheet. 
		Transformer transformer = tfactory.newTransformer(new StreamSource(xslStream)); 

		// Transform the source XML 
		transformer.transform(source, result); 
		
	}

	private static NodeList getQueries(InputStream input) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder;
		Document doc = null;
		XPathExpression expr = null;
		builder = factory.newDocumentBuilder();
		doc = builder.parse(input);

		//Create namespaces
		HashMap<String, String> prefMap = new HashMap<String, String>() {{
			put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
			put("elmo", "http://bp4mc2.org/elmo/def#");
		}};
		SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefMap);
		
		// create an XPathFactory
		XPathFactory xFactory = XPathFactory.newInstance();
		// create an XPath object
		XPath xpath = xFactory.newXPath();
		//Set namespaces
		xpath.setNamespaceContext(namespaces);
		// compile the XPath expression
		expr = xpath.compile("//rdf:Description/elmo:query/text()");
		// run the query and get a nodeset
		Object result = expr.evaluate(doc, XPathConstants.NODESET);
		// cast the result to a DOM NodeList and return
		return (NodeList) result;
	
	}
	
	public static void main(String[] args) {
		
		boolean remoteConfig = true;

		if (args.length==0) {
			System.out.println("Usage: ldtcmd <Representation> (remote|local)");

		} else {
			if (args.length==2) {
				if (LOCAL_FLAG.equals(args[1])) {
					remoteConfig = false;
				}
			}
			System.out.println("Creating page: " + args[0]);
			if (remoteConfig) {
				System.out.println("Using remote configuration (" + SPARQL_ENDPOINT + ")");
			} else {
				System.out.println("Using local configuration (in the configuration folder).");
			}
			
			try {
				// set the TransformFactory to use the Saxon TransformerFactoryImpl method  
				System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
				
				/*
					First part input:
					- the representation name in args[0]
					
					First part execution:
					- retrieve the configuration RDF/XML from the triplestore
					- retrieve the data as RDF/XML from the triplestore, using the queries from the configuration
					
					This part is the "InformationProduct" part in the dotwebstack
				*/
				
				System.out.println("Create representation query (using xslt)");
				StreamSource inputSource = new StreamSource(new StringReader(CONFIG_TEMPLATE.replaceAll("REPRESENTATION",args[0])));
				ByteArrayOutputStream configQueryStream = new ByteArrayOutputStream();
				transform(inputSource, "xsl/configuration.xsl", new StreamResult(configQueryStream));

				InputStream response;
				if (remoteConfig) {
					System.out.println("Execute SPARQL query (result = the LDT configuration in RDF/XML)");
					response = Sparql.executeRequest(SPARQL_ENDPOINT,configQueryStream.toString());
				} else {
					System.out.println("Retrieve configuration from filesystem");
					response = new FileInputStream("configuration/"+args[0]+".xml");
				}

				//Store the configuration, to be used again
				ByteArrayOutputStream configuration = new ByteArrayOutputStream();
				XMLMerger.copy(configuration, new StreamSource(response));
				
				//Save configuration to file, if remoteConfig - to be used again
				if (remoteConfig) {
					configuration.writeTo(new FileOutputStream("configuration/"+args[0]+".saved.xml"));
				}
				
				//Create output stream for rdf results, more than one result is possible, so create merger
				PipedInputStream rdfdata = new PipedInputStream(PIPE_BUFFER); 
				PipedOutputStream rdfdataOutput = new PipedOutputStream(rdfdata);
				XMLMerger merger = new XMLMerger(rdfdataOutput);
				merger.startMerging("results");
				
				System.out.println("Retrieve data from configuration, execute SPARQL query (result = RDF/XML)");
				NodeList queries = getQueries(new ByteArrayInputStream(configuration.toByteArray()));
				for (int i=0; i<queries.getLength();i++){
					// The query
					String query = queries.item(i).getNodeValue();
					if (query!=null) {
						//Execute sparql query
						InputStream data = Sparql.executeRequest(SPARQL_ENDPOINT,query.replaceAll("@STAGE@","http://localhost:8080/stage"));

						//Transform from sparql result to rdf (cleaned)
						ByteArrayOutputStream dataEnriched = new ByteArrayOutputStream();
						XMLMerger.merge("root",dataEnriched,new StreamSource(data));
						ByteArrayOutputStream cleanedData = new ByteArrayOutputStream();
						transform(new StreamSource(new ByteArrayInputStream(dataEnriched.toByteArray())), "xsl/sparql2rdfa.xsl", new StreamResult(cleanedData));

						//Merge data
						merger.addXML(new StreamSource(new ByteArrayInputStream(cleanedData.toByteArray())));
					}
				}
				
				//Finish merging, outputstream is complete
				merger.finishMerging();
				rdfdataOutput.close();
				
				/*
					Second part, input:
					- configuration: an OutputStream containing the RDF/XML configuration
					- rdfdata:  an InputStream containing the RDF/XML data (streamed and cleaned from http)
					
					This part is the "Representation" part in the dotwebstack, creating html from rdf
				*/
				
				//XMLMerger.copy(new FileOutputStream("output.xml"),new StreamSource(rdfdata));
				
				System.out.println("Merge configuration result with context");
				PipedInputStream configPackage = new PipedInputStream(PIPE_BUFFER); 
				PipedOutputStream configPackageOutput = new PipedOutputStream(configPackage);
				XMLMerger.merge("root", configPackageOutput, new StreamSource(new ByteArrayInputStream(configuration.toByteArray())));
				configPackageOutput.close(); //Close of outputstream is necessary to prevent deadlock because we don't have a multi-treaded application

				System.out.println("rdf2view.xsl (create configuration XML from RDF)");
				PipedInputStream view = new PipedInputStream(PIPE_BUFFER);
				PipedOutputStream viewOutput = new PipedOutputStream(view);
				transform(new StreamSource(configPackage), "xsl/rdf2view.xsl", new StreamResult(viewOutput));
				viewOutput.close();
				
				System.out.println("Merge view with context and original data");
				PipedInputStream dataPackage = new PipedInputStream(PIPE_BUFFER);
				PipedOutputStream dataPackageOutput = new PipedOutputStream(dataPackage);
				XMLMerger.merge("root", dataPackageOutput, new StreamSource(new StringReader(CONTEXT)), new StreamSource(view),new StreamSource(rdfdata));
				dataPackageOutput.close();

				System.out.println("rdf2rdfa.xsl (create RDF annotated with UI declarations)");
				PipedInputStream rdfa = new PipedInputStream(PIPE_BUFFER);
				PipedOutputStream rdfaOutput = new PipedOutputStream(rdfa);
				transform(new StreamSource(dataPackage), "xsl/rdf2rdfa.xsl", new StreamResult(rdfaOutput));
				rdfaOutput.close();
				
				System.out.println("rdf2html.xsl (create HTML from RDF annotated with UI declarations)");
				PipedInputStream html = new PipedInputStream(PIPE_BUFFER);
				PipedOutputStream htmlOutput = new PipedOutputStream(html);
				transform(new StreamSource(rdfa), "xsl/rdf2html.xsl", new StreamResult(htmlOutput));
				htmlOutput.close();
				
				System.out.println("convert xml to html (using xslt)");
				FileOutputStream htmlFile = new FileOutputStream("html/"+args[0]+".html");
				transform(new StreamSource(html), "xsl/to-html.xsl", new StreamResult(htmlFile));
				
			} catch (Exception ex) {
				System.out.println("EXCEPTION: " + ex); 
				ex.printStackTrace(); 
			}
		}
	}
}
