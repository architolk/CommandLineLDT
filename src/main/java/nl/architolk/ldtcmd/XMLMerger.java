package nl.architolk.ldtcmd;

import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import javax.xml.transform.stream.StreamSource;

import java.io.OutputStream;

public class XMLMerger {

	private static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
	private static final XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
	private static final XMLInputFactory inputFactory = XMLInputFactory.newFactory();

	private static final XMLEvent NEWLINE = eventFactory.createDTD("\n");

	private XMLEventWriter eventWriter;
	private String rootName = "";
	private boolean started = false;
	private boolean finished = false;

	public static void merge(String rootName, OutputStream result, StreamSource... source) throws XMLStreamException {
		
		XMLMerger merger = new XMLMerger(result);
		merger.startMerging(rootName);
		merger.addXML(source);
		merger.finishMerging();
	}
	
	public static void copy(OutputStream result, StreamSource source) throws XMLStreamException {
		
		XMLMerger merger = new XMLMerger(result);
		merger.copyXML(source);
	}
	
    public XMLMerger(OutputStream result) throws XMLStreamException {
		eventWriter = outputFactory.createXMLEventWriter(result,"UTF-8");
    }

	public void startMerging(String aRootName) throws XMLStreamException {

		if (started) throw new XMLStreamException("Merging already started");
		started = true;
		
		rootName = aRootName;

		// Create the root header of the document
		eventWriter.add(eventFactory.createStartDocument());
		eventWriter.add(NEWLINE);
		eventWriter.add(eventFactory.createStartElement("","",rootName));
		eventWriter.add(NEWLINE);
	}
	
	public void addXML(StreamSource... source) throws XMLStreamException {

		if (!started) throw new XMLStreamException("Merging not started");
		if (finished) throw new XMLStreamException("Merging already finished");
		
		// Copy original sources, without document begin and end
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
			eventWriter.add(NEWLINE);
		}
	}
	
	public void finishMerging() throws XMLStreamException {
		
		if (!started) throw new XMLStreamException("Merging not started");
		if (finished) throw new XMLStreamException("Merging already finished");
		finished = true;
		
        eventWriter.add(eventFactory.createEndElement("", "", rootName));
        eventWriter.add(NEWLINE);
        eventWriter.add(eventFactory.createEndDocument());
		
        eventWriter.close();
	}
	
	public void copyXML(StreamSource source) throws XMLStreamException {
		
		if (started) throw new XMLStreamException("Merging already started");
		if (finished) throw new XMLStreamException("Merging already finished");
		started = true;
		finished = true;
		
		XMLEventReader test = inputFactory.createXMLEventReader(source);
		while (test.hasNext()) {
			XMLEvent event= test.nextEvent();
			eventWriter.add(event);
			test.close();
		}

        eventWriter.close();
	}
	
}
