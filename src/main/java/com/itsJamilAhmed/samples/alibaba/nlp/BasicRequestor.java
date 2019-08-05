/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 *  Apache Qpid JMS 1.1 Solace AMQP Examples: BasicRequestor
 */

package com.itsJamilAhmed.samples.alibaba.nlp;

import org.apache.qpid.jms.JmsConnectionFactory;

import java.util.UUID;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * Sends a request message using Apache Qpid JMS 1.1 API over AMQP 1.0 and receives a reply to it. Solace messaging
 * is used as the message broker.
 * 
 * This is the Requestor in the Request/Reply messaging pattern.
 */
public class BasicRequestor {

    final String REQUEST_TOPIC_NAME = "T/GettingStarted/requests";

    final int REPLY_TIMEOUT_MS = 10000; // 10 seconds

    private void run(String... args) throws Exception {
        String solaceHost = args[0];
        String solaceUsername = args[1];
        String solacePassword = args[2];

        System.out.printf("BasicRequestor is connecting to Solace messaging at %s...%n", solaceHost);

        // Programmatically create the connection factory using default settings
        ConnectionFactory connectionFactory = new JmsConnectionFactory(solaceUsername, solacePassword, solaceHost);

        // Create connection to the Solace messaging
        Connection connection = connectionFactory.createConnection();

        // Create a non-transacted, auto ACK session.
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        System.out.printf("Connected to the Solace messaging with client username '%s'.%n", solacePassword);

        // Create the request topic programmatically
        Topic requestTopic = session.createTopic(REQUEST_TOPIC_NAME);

        // Create the message producer
        MessageProducer requestProducer = session.createProducer(null);

        // The response will be received on this temporary queue.
        TemporaryQueue replyToQueue = session.createTemporaryQueue();

        // Create consumer for receiving the request's reply
        MessageConsumer replyConsumer = session.createConsumer(replyToQueue);

        // Start receiving replies
        connection.start();

        // Create a request.
        TextMessage request = session.createTextMessage("Sample Request");
        // The application must put the destination of the reply in the replyTo field of the request
        request.setJMSReplyTo(replyToQueue);
        // The application must put a correlation ID in the request
        String correlationId = UUID.randomUUID().toString();
        request.setJMSCorrelationID(correlationId);

        System.out.printf("Sending request '%s' to topic '%s'...%n", request.getText(), requestTopic.toString());

        // Send the request
        requestProducer.send(requestTopic, request, DeliveryMode.NON_PERSISTENT,
                Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);

        System.out.println("Sent successfully. Waiting for reply...");

        // the main thread blocks at the next statement until a message received or the timeout occurs
        Message reply = replyConsumer.receive(REPLY_TIMEOUT_MS);

        if (reply == null) {
            throw new Exception("Failed to receive a reply in " + REPLY_TIMEOUT_MS + " msecs");
        }

        // Process the reply
        if (reply.getJMSCorrelationID() == null) {
            throw new Exception(
                    "Received a reply message with no correlationID. This field is needed for a direct request.");
        }

        // Apache Qpid JMS prefixes correlation ID with string "ID:" so remove such prefix for interoperability
        if (!reply.getJMSCorrelationID().replaceAll("ID:", "").equals(correlationId)) {
            throw new Exception("Received invalid correlationID in reply message.");
        }

        if (reply instanceof TextMessage) {
            System.out.printf("TextMessage response received: '%s'%n", ((TextMessage) reply).getText());
        } else {
            System.out.println("Message response received.");
        }

        System.out.printf("Message Content:%n%s%n", reply.toString());

        connection.stop();
        // Close everything in the order reversed from the opening order
        // NOTE: as the interfaces below extend AutoCloseable,
        // with them it's possible to use the "try-with-resources" Java statement
        // see details at https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        replyConsumer.close();
        requestProducer.close();
        session.close();
        connection.close();
    }

    public static void main(String... args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: BasicRequestor amqp://<msg_backbone_ip:amqp_port> <username> <password>");
            System.exit(-1);
        }
        new BasicRequestor().run(args);
    }
}
