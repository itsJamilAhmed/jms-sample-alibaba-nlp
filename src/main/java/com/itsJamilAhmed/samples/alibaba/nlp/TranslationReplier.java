package com.itsJamilAhmed.samples.alibaba.nlp;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
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




/**
 * Receives a JMS request message with text for translation (using Apache Qpid JMS 1.1 API over AMQP 1.0) and sends the response message with translation.
 * This is the playing the Replier role in the Request/Reply messaging pattern.
 */

public class TranslationReplier {

	final static String DEFAULT_JNDI_FILENAME = "./jndi.properties";
	final static String DEFAULT_ALIBABASERVICE_FILENAME = "./alibaba-mt.properties";
	
	// The sender and receiver completely decoupled in that they may even look up different destinations in JNDI and let the broker take care of the matching up.
	// e.g. Publish to topic, subscribe from a queue or wildcard pattern.
	final static String JNDI_DESTINATION_NAME = "nlp-translation-requests-receive";		
	final static String JNDI_CF_NAME = "ConnectionFactory";

    final static String PROGRAM_NAME = "TranslationReplier.jar";
    
    final static Logger logger = LoggerFactory.getLogger(TranslationRequestor.class);
    
    // Latch used for synchronizing between threads
    final CountDownLatch latch = new CountDownLatch(1);
    
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
		
		ArgumentGroup serviceArgGroup = myArgParser.addArgumentGroup("Alibaba Machine Translation Service");
		
		// Just need the one argument of where the file is, then test the argument for being a file, readable, etc.
		// Note: Cannot look for the jndi.properties file in the classpath as the program will be a runnable jar.
		serviceArgGroup.addArgument("-a", "--alibaba-properties")
				.type(Arguments.fileType().verifyIsFile().verifyCanRead())
				.required(true)
				.setDefault(DEFAULT_ALIBABASERVICE_FILENAME)
				.help("A properties file to lookup service-region, access-key-ID and access-key-secret (Was not found at default path: " + DEFAULT_ALIBABASERVICE_FILENAME + ")");		
		
	
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
	

    public void run(String fileJNDIpath, MachineTranslationService mtService) throws JMSException {
    	
    	
    	Context jndiContext; 
        ConnectionFactory connectionFactory = null; 
        Connection connection = null; 
        Destination destination = null;
        MessageConsumer consumer = null;
        
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
	        System.exit(1); 
		}
		catch (Exception e) {
			logger.error("Error occurred during JNDI lookups: " + e.toString()); 
	        System.exit(1); 
		}

		// Create the connection using the factory
		connection = connectionFactory.createConnection();
		
		// Create a non-transacted, auto ACK session from the connection.
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        logger.info("### Successfully connected session to the JMS Broker. ###");
    	
        // Create consumer for receiving the requests on the topic destination
        consumer = session.createConsumer(destination);
        logger.info("Created the message consumer.");

    	// Create producer for sending the reply
		final MessageProducer producer = session.createProducer(null);
		logger.info("Created the message producer.");

        // Start receiving requests
        connection.start();
		
		
        // Use the anonymous inner class for receiving request messages asynchronously
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message request) {
                try {
                	logger.debug("Received request message, processing...");
                	
                	// Check if the expected type of message was received
					if (request instanceof TextMessage) {
						String translationRequest = ((TextMessage) request).getText();
					    logger.debug("TextMessage request received. Content: '{}', Destination: '{}', ReplyTo: '{}', CorrelationID: '{}', MessageID: '{}'", 
					    		translationRequest, request.getJMSDestination(), request.getJMSReplyTo(), request.getJMSCorrelationID(), request.getJMSMessageID());
					    
					    // Extract the reply-to destination and send a response
					    Destination replyDestination = request.getJMSReplyTo();
	                    if (replyDestination != null) {
	                        
	                        // workaround as the Apache Qpid JMS API always sets JMSReplyTo as non-temporary
	                        //String replyDestinationName = ((Destination) replyDestination).toString();
	                        //replyDestination = new TemporaryTopic(replyDestinationName);

	                        TextMessage reply = session.createTextMessage();
	                        String translationResponse = mtService.translateEnglishToChinese(translationRequest);
	                        reply.setText(translationResponse);

	                        // Copy the correlation ID from the request to the reply if one is present, otherwise use the MessageID as an alternative
	                        if (request.getJMSCorrelationID() == null) {
	                        	reply.setJMSCorrelationID(request.getJMSMessageID());
	                        }
	                        else {
	                        	reply.setJMSCorrelationID(request.getJMSCorrelationID());
	                        }

	                        // Send the reply
	                        producer.send(replyDestination, reply, DeliveryMode.NON_PERSISTENT,
	                                Message.DEFAULT_PRIORITY,
	                                Message.DEFAULT_TIME_TO_LIVE);
	                        
	                        logger.info("Processed a request on destination '{}': '{}' -> '{}'", request.getJMSDestination(), translationRequest, translationResponse );

	                    } else {
	                    	// Nowhere to send the reply to!
	                        logger.debug("Received message without reply-to field.");
	                    }
					    
					} else {
						// Replier not coordinated as expected on message type, nothing to do.
						logger.debug("Message request received but not expected TextMessage type.");
					}
                	
                    
                } catch (Exception ex) {
                    logger.error("Error occurred during processing of incoming request message.");
                }
            }
        });
        
		////////////////////////
		// Ready to start receiving requests and process them
		////////////////////////
        connection.start();
		logger.info("### Ready to process requests. Waiting for messages. ###");
        
        // the main thread blocks at the next statement the latch is changed elsewhere
        try {
			latch.await();
		} catch (InterruptedException e) {
			
		}

        // Stop the connection, then close the JMS with the order reversed from opening order
        connection.stop();
        producer.close();
        consumer.close();
        session.close();
        connection.close();
    }

    public static void main(String... args) throws Exception {
    	
    	logger.info("###### Translation Replier Program Started ######");
        
    	// Parse the program arguments.
    	Map<String,Object> parameters = parseArgs(args);
    			
    	// Read the properties and setup the machine translation service
    	MachineTranslationService mtService = null;
    	
    	try (InputStream input = new FileInputStream(parameters.get("alibaba_properties").toString())) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);
 
            // Special property in the file that can also put the translation element into simulation mode...
            if (prop.containsKey("simulation-mode") && prop.getProperty("simulation-mode").equalsIgnoreCase("true")) {
            	logger.info("Machine Translation Service instantiated in simulation mode");
            	mtService = new MachineTranslationService(true);
            }
            else {
         
            	// Read the properties from the file and instantiate the service
                String[] expectedProps = { "service-region", "access-key-id", "access-key-secret"};
                for (String property : expectedProps ) {
                	if (prop.getProperty(property) == null)
                	{
                		logger.error("Expected property '{}' not found in properties file! Exiting.", property);
                		System.exit(1);
                	}
                }
            
	            logger.debug("Instantiating Machine Translation Service with Service Region: '{}', Access Key ID: '{}', Access Key Secret: '{}'", 
	            		prop.getProperty("service-region"), 
	            		prop.getProperty("access-key-id"),
	            		prop.getProperty("access-key-secret"));
	            
	            try {
					mtService = new MachineTranslationService(
							prop.getProperty("service-region"), 
							prop.getProperty("access-key-id"),
							prop.getProperty("access-key-secret"));
					
					// The instantiation will throw an error if the parameters are found to be invalid following a "self-test" request.
					logger.info("Machine Translation Service instantiated");
					
				} catch (Exception e) {
					logger.error("Error occurred during Machine Translation Service instantiation (Service Region: '{}', Access Key ID: '{}', Access Key Secret: '{}'). Error message: {}", 
	            		prop.getProperty("service-region"), 
	            		prop.getProperty("access-key-id"),
	            		prop.getProperty("access-key-secret"),
	            		e.getMessage());
					
					System.exit(1);
				}
            }
                      
        } catch (Exception e) {
            logger.error("Error occurred while processing the properties file." + e.getMessage());
            System.exit(1);
        }
    	
    	// Start the thread with the jndi file path and the MT Service to utilise.
        new TranslationReplier().run(
        		parameters.get("jndi_properties").toString(),
        		mtService);
    }
}
