package com.itsJamilAhmed.samples.alibaba.nlp;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TemporaryTopic;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentGroup;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Sends a JMS request message with text for translation (using Apache Qpid JMS 1.1 API over AMQP 1.0) and waits for translated response messages.
 * This is the playing the Requestor role in the Request/Reply messaging pattern.
 */

public class TranslationRequestor {

	final static String DEFAULT_JNDI_FILENAME = "./jndi.properties";
	// The sender and receiver completely decoupled in that they may even look up different destinations in JNDI and let the broker take care of the matching up.
	// e.g. Publish to topic, subscribe from a queue or wildcard pattern.
	final static String JNDI_DESTINATION_NAME = "nlp-translation-requests-send";		
	
	final static String JNDI_CF_NAME = "ConnectionFactory";

    final static String PROGRAM_NAME = "TranslationRequestor.jar";
    
    final static Logger logger = LoggerFactory.getLogger(TranslationRequestor.class);

    final int REPLY_TIMEOUT_MS = 10000; // 10 seconds
    
	/**
	 * Use argparse4j to parse the program arguments and return a map.
	 * Handle arguments validation, set default values, show usage output, etc. 
	 */    
	private static Map<String,Object> parseArgs(String[] args) {
		
		// Where to save the parsed arguments as they go through the parsers?
		Map<String,Object> parsedArgs = new HashMap<String,Object>();
		
		// Build the Argument Parser and Argument Groups before using it
		ArgumentParser myArgParser = ArgumentParsers.newFor(PROGRAM_NAME).defaultFormatWidth(200).addHelp(true).build().defaultHelp(false);
		
		// This program is quite simple in that everything deferred to the file-based JNDI. So just need that file if not at the default location
		ArgumentGroup jndiArgGroup = myArgParser.addArgumentGroup("File-based JNDI Access");
		
		// Just need the one argument of where the file is, then test the argument for being a file, readable, etc.
		// Note: Cannot look for the jndi.properties file in the classpath as the program will be a runnable jar.
		jndiArgGroup.addArgument("-j", "--jndi-properties")
				.type(Arguments.fileType().verifyIsFile().verifyCanRead())
				.required(true)
				.setDefault(DEFAULT_JNDI_FILENAME)
				.help("JNDI Properties file to lookup Connection Factory and Topic Destination. (Was not found at default path: " + DEFAULT_JNDI_FILENAME + ")");		
				
		// Now ready to try and parse the arguments...
		try{				
			myArgParser.parseArgs(args, parsedArgs);
		}
		catch (ArgumentParserException e) {

			// Leaving this one as System.err and not via logger, in case that could not get setup properly and switched to no-op.
			System.err.println("ERROR: Arguments Processing Exception. -> " + e.getMessage() + ".\n");
			myArgParser.printHelp();
			System.exit(0);
		}
		return parsedArgs;
	}
    
    private void run(String fileJNDIpath) {

    	Context jndiContext; 
        ConnectionFactory connectionFactory = null; 
        Connection connection = null; 
        Session session = null; 
        Destination destination = null;
        TemporaryTopic replyToTopic = null;
        MessageProducer producer = null;
        MessageConsumer consumer = null;
        TextMessage request = null;
        Message reply = null;
        String correlationId = null;
                
        logger.info("Using file-based JNDI at: " + fileJNDIpath);
        
        // Do the JNDI lookups
		try {
			
	        InputStream input = new FileInputStream(fileJNDIpath);

	        Properties properties = new Properties();        
	        properties.load(input);
	        
			jndiContext = new InitialContext(properties);
			logger.info("Looked up initial context: {}", jndiContext);
			
			connectionFactory = (ConnectionFactory) jndiContext.lookup(JNDI_CF_NAME);
			logger.info("Looked up connection factory label: {} to: {}", JNDI_CF_NAME, connectionFactory);

			destination = (Destination) jndiContext.lookup(JNDI_DESTINATION_NAME);
			logger.info("Looked up destination label: {} to: {}", JNDI_DESTINATION_NAME, destination);
			
		}
		catch (NameNotFoundException ne) {
			logger.error("Could not find the label '{}' in the JNDI for lookup." , ne.getExplanation()); 
			logger.error("Exiting program.");
			System.exit(1);
		}
		catch (Exception e) {
			logger.error("Error occurred during JNDI lookups: " + e.toString()); 
			logger.error("Exiting program.");
			System.exit(1);
		} 

		// Try connecting and creating the session
		try {
			// Create the connection using the factory
			connection = connectionFactory.createConnection();
			
			// Create a non-transacted, auto ACK session from the connection.
	        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

	        logger.info("### Successfully connected session to the JMS Broker. ###");
			
		} catch (Exception e) {
			logger.error("Could not connect to the JMS Broker: " + e.toString()); 
			logger.error("Exiting program.");
			System.exit(1);
		}
        

        // Create the message producer, consumer, temporary topic
        try {
        	
			producer = session.createProducer(null);
			logger.info("Created the message producer.");
			
			// The response will be received on this temporary topic.
	        replyToTopic = session.createTemporaryTopic();
	        logger.info("Created the temporary topic.");

	        // Create consumer for receiving the request's reply
	        consumer = session.createConsumer(replyToTopic);
	        logger.info("Created the message consumer.");

	        // Start receiving replies
	        connection.start();
			
		} catch (JMSException e) {
			logger.error("Could not setup producer and consumer objects: " + e.toString()); 
			logger.error("Exiting program.");
			System.exit(1);
		}
        
        
        ////////////////////////
        // Collect input and then make requests with each line
        ////////////////////////
        
        logger.info("### Ready to process requests. Waiting for input lines. ###");
        
        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8.name());		// Don't assume default charset to be UTF-8?
        
        String line = "";
        String translationResponse = "";
        
        while (in.hasNextLine()) {		// in.hasNextLine() and in.nextLine() if empty lines were to be preserved. Not needed in this case.
            line = in.nextLine();

            // If empty line, want to preserve its place in the output but no need to translate it.... Say if multiple paragraphs of text pasted in.
            if (line.isEmpty()) {
            	System.out.println();
            	// Continue loop to next line input
            	continue;
            }
            
            
            // (1) Create and send the request.        
            try {
    			request = session.createTextMessage(line);
    			
    			// The application must put the destination of the reply in the replyTo field of the request
    	        request.setJMSReplyTo(replyToTopic);
    	        
    	        // The application must put a correlation ID in the request
    	        correlationId = UUID.randomUUID().toString();
    	        request.setJMSCorrelationID(correlationId);

    	        logger.debug("Sending request '" + request.getText() + "' to destination '" + destination.toString() + "'...");

    	        // Send the request
    	        producer.send(destination, request, 
    	        		DeliveryMode.NON_PERSISTENT,
    	                Message.DEFAULT_PRIORITY,
    	                Message.DEFAULT_TIME_TO_LIVE);

    	        logger.debug("Sent successfully. Waiting for reply...");
    				
    		} catch (JMSException e) {
    			logger.error("Error occurred during request message sending: " + e.toString()); 
    			logger.error("Exiting program.");
    			System.exit(1);
    		}
            
            
            // (2) Try to receive the response. 
            // Notes: 
            //	Synchronous, blocking call to receive in this program since it is processing requests in a very simple sequence without out-of-order response handling logic
            //	Receiver side program can be asynch with callbacks to respond to requests from multiple senders in any order
            try {
            	
    			// the main thread blocks at the next statement until a message received or the timeout occurs
    			reply = consumer.receive(REPLY_TIMEOUT_MS);

    			// Did anything arrive?
    			if (reply == null) {
    				translationResponse = "[No translation response. Timed Out.]";
    			    logger.debug("Failed to receive a reply in " + REPLY_TIMEOUT_MS + " msecs");
    			}
    			// Expected correlation ID is present?
    			else if (reply.getJMSCorrelationID() == null) {
    				// May be a malformed response from the replier
    				translationResponse = "[No translation response. Missing ID.]";
    			    logger.debug("Received a reply message with no correlationID. This field is needed for a direct request.");
    			}
    			else
    			{
    				// All good to try and process it...
    				
    				// Apache Qpid JMS prefixes correlation ID with string "ID:" so remove such prefix for interoperability across JMS providers
    				if (!reply.getJMSCorrelationID().replaceAll("ID:", "").equals(correlationId)) {
    					// May be a stray or delayed response that is no longer useful
    					translationResponse = "[No translation response. Mismatched ID.]";
    				    logger.debug("Received invalid correlationID in reply message. Expecting {} and got {}", correlationId, reply.getJMSCorrelationID());
    				}
    				else
    				{
    					// Check if the expected type of message was received
    					if (reply instanceof TextMessage) {
    						translationResponse = ((TextMessage) reply).getText();
    					    logger.debug("TextMessage response received: '" + ((TextMessage) reply).getText() + "'");
    					} else {
    						// Replier not coordinated as expected on message type
    						translationResponse = "[No translation response. Incorrect type.]";
    					    logger.debug("Message response received but not expected TextMessage type.");
    					}
    				}
    			}
    			
    		} catch (JMSException e) {
    			logger.error("Error occurred during reply message receive: " + e.toString()); 
    			logger.error("Exiting program.");
    			System.exit(1);
    		}     
            
            // This output goes to stdout?
            System.out.printf("%s\t->\t%s\n", line, translationResponse);
            
        
        }
        
   		// All done, close the objects and shutdown
		try {
			
			// Close the input scanner
			in.close();
			
			// Stop the connection, then close the JMS with the order reversed from opening order
			connection.stop();
			consumer.close();
			producer.close();
			session.close();
			connection.close();
		} catch (JMSException e) {
			logger.error("Error occurred during the shutdown process: " + e.toString()); 
			logger.error("Exiting program.");
			System.exit(1);
		}
  
        
    }

    public static void main(String... args) throws Exception {
    	
        
    	// Parse the program arguments. (Just one arg at the moment but structure is there for future development.)
    	Map<String,Object> parameters = parseArgs(args);
    	
        logger.info("###### Translation Requestor Program Started ######");

    	// Start the thread with the collected parameters
        new TranslationRequestor().run(
        		parameters.get("jndi_properties").toString());
    }
}
