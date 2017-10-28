package nl.architolk.ldtcmd;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream; 
import java.io.FileOutputStream;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.List;
import java.util.ArrayList;

/*
	The CreatePage class give an example of a "pipe" of transformations and sparql requests
	This example is only for architectural purposes, it's not production ready
	
	One obvious functional improvement whould be creating a multithreaded application, to allow actual streaming of input to output data
	At this moment, although PipedInputStream/PipedOutputStream are used, a large buffer is necessary to prevent deadlock of the application
	
*/

public class CreatePage {
	
	private static final String CONFIG_TEMPLATE = "<input><stage>http://localhost:8080/stage</stage><representation>http://localhost:8080/stage#REPRESENTATION</representation></input>";
	private static final String CONTEXT = "<context staticroot='.'><title>Command line LDT</title></context>";
	private static final String SPARQL_ENDPOINT = "http://localhost:8890/sparql";
	private static int PIPE_BUFFER = 1000000; // Large buffer size to prevent deadlock. Better solution would be a multi-treaded application
	
	// Factories
	private static final TransformerFactory tfactory = TransformerFactory.newInstance();
	private static final XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
	private static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
	private static final XMLInputFactory inputFactory = XMLInputFactory.newFactory();

	private static void transform(StreamSource source, String xslResource, StreamResult result) throws TransformerConfigurationException,TransformerException { 

		// Create input stream for the actual resource
		InputStream xslStream = CreatePage.class.getClassLoader().getResourceAsStream(xslResource);
	
		// Create a transformer for the stylesheet. 
		//Transformer transformer = tfactory.newTransformer(new StreamSource(new File(xslFile))); 
		Transformer transformer = tfactory.newTransformer(new StreamSource(xslStream)); 

		// Transform the source XML 
		transformer.transform(source, result); 
		
	}

	private static void mergeXML(String rootName, OutputStream result, StreamSource... source) throws XMLStreamException {

		XMLEvent newLine = eventFactory.createDTD("\n");
	
		// Register stream for output
		XMLEventWriter eventWriter = outputFactory.createXMLEventWriter(result);
	
		// Create the root header of the document
		eventWriter.add(eventFactory.createStartDocument());
		eventWriter.add(newLine);
		eventWriter.add(eventFactory.createStartElement("","",rootName));
		eventWriter.add(newLine);

		// Copy original sources
		for (int i = 0; i < source.length; i++) {
			XMLEventReader test = inputFactory.createXMLEventReader(source[i]);
			while (test.hasNext()) {
				XMLEvent event= test.nextEvent();
				//avoiding start(<?xml version="1.0"?>) and end of the documents;
				if (event.getEventType()!= XMLEvent.START_DOCUMENT && event.getEventType() != XMLEvent.END_DOCUMENT) {
					eventWriter.add(event);
				}
				test.close();
			}
			eventWriter.add(newLine);
		}

		// Create the root footer of the document
        eventWriter.add(eventFactory.createEndElement("", "", rootName));
        eventWriter.add(newLine);
        eventWriter.add(eventFactory.createEndDocument());
		
        eventWriter.close();

	}
	
	private static InputStream executeSparqlRequest(String query) throws UnsupportedEncodingException, IOException {
		
		//Create the client
		CloseableHttpClient httpclient = HttpClients.createDefault();
		
		//Create the request
		HttpPost httpRequest = new HttpPost(SPARQL_ENDPOINT);
		
		//Add post name/value pairs: one name/value containing the query parameter
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("query", query));
		httpRequest.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		httpRequest.addHeader("accept","application/rdf+xml");
		
		//Execute the request
		CloseableHttpResponse response = httpclient.execute(httpRequest);

		int status = response.getStatusLine().getStatusCode();
		
		if (status < 200 || status >= 300) throw new IOException(response.getStatusLine().toString());
		HttpEntity entity = response.getEntity();
		if (entity==null) throw new IOException("No content http error");
		
		return entity.getContent();
	}

	public static void main(String[] args) {

		if (args.length!=1) {
			System.out.println("Usage: ldtcmd <Representation>");
		} else {
			System.out.println("Creating page: " + args[0]);
			
			try {
				// set the TransformFactory to use the Saxon TransformerFactoryImpl method  
				System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
				
				System.out.println("Create representation query (using xslt)");
				StreamSource inputSource = new StreamSource(new StringReader(CONFIG_TEMPLATE.replaceAll("REPRESENTATION",args[0])));
				ByteArrayOutputStream configQueryStream = new ByteArrayOutputStream();
				transform(inputSource, "xsl/configuration.xsl", new StreamResult(configQueryStream));

				System.out.println("Execute SPARQL query (result = the LDT configuration in RDF/XML)");
				InputStream response = executeSparqlRequest(configQueryStream.toString());

				System.out.println("Merge configuration result with context");
				PipedInputStream configPackage = new PipedInputStream(PIPE_BUFFER); 
				PipedOutputStream configPackageOutput = new PipedOutputStream(configPackage);
				mergeXML("root", configPackageOutput, new StreamSource(response));
				configPackageOutput.close(); //Close of outputstream is necessary to prevent deadlock because we don't have a multi-treaded application

				System.out.println("rdf2view.xsl (create configuration XML from RDF)");
				ByteArrayOutputStream view = new ByteArrayOutputStream();
				transform(new StreamSource(configPackage), "xsl/rdf2view.xsl", new StreamResult(view));
				
				System.out.println("Merge view with context and original data");
				ByteArrayOutputStream dataPackage = new ByteArrayOutputStream();
				mergeXML("root", dataPackage, new StreamSource(new StringReader(CONTEXT)), new StreamSource(new StringReader(view.toString())),new StreamSource(new File(args[0]+".xml")));
				
				dataPackage.writeTo(new FileOutputStream("page.xml"));
				
			} catch (Exception ex) {
				System.out.println("EXCEPTION: " + ex); 
				ex.printStackTrace(); 
			}
		}
	}
}
