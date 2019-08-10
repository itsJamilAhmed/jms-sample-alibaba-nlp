# JMS Samples using Alibaba Cloud's Natural Language Processing (NLP) SDK: Machine Translation (a.k.a Aliyun MT)

## What does this demonstrate?

There are two applications coded to the JMS API in a Requestor and Replier role, respectively. (i.e. Implementing the [request-reply](https://www.enterpriseintegrationpatterns.com/patterns/messaging/RequestReplyJmsExample.html) enterprise integration pattern.)

The JMS code has been kept completely provider agnostic to demonstrate how one might switch JMS providers without needing application code change. The programs lean heavily on a [file-based JNDI](https://en.wikipedia.org/wiki/Java_Naming_and_Directory_Interface) to look up aspects such as the provider connection factory and JMS destinations to send and receive from. The program code therefore remains shielded from any provider specific implementation details - such as how destinations may be named and delimited.

Finally, the request-reply interaction is around the translation of English-language text to Chinese, leveraging the [Machine Translation](https://www.alibabacloud.com/products/machine-translation) SDK from Alibaba Cloud / Aliyun. 

The requestor program collects user input from the console and submits each line for translation as a JMS request message. The replier program receives the JMS request message, calls the Machine Translation API to translate the text payload to Chinese, and responds with the result as a new JMS reply message. The requestor program outputs to screen the Chinese-language translation.

## Contents

This repository contains:

1. **AlibabaNLPRequestor.jar**: A runnable JAR of the requesting program, collects user input and submits for translation to Chinese.
2. **AlibabaNLPReplier.jar**: A runnable JAR of the replying program, receives English text and replies with Chinese translation.
3. Source code for the two programs as a Gradle project
4. Sample JNDI and properties files to connect to a JMS broker and connect to the Machine Translation SDK

## Checking out

To check out the project, clone this GitHub repository:

```
git clone https://github.com/itsJamilAhmed/jms-sample-alibaba-nlp
cd jms-sample-alibaba-nlp/
```

## Running the Samples

### Pre-requisites:

1. Get access to connection details for a running JMS broker supporting AMQP. 

You may wish to download and run ActiveMQ locally following instructions [here](https://activemq.apache.org/getting-started).
Alternatively you could access a free hosted JMS broker instance via [Solace Cloud](https://solace.com/cloud/). 

2. Activate the Machine Translation service in your Alibaba Cloud / Aliyun subscription and collect the AccessID and Access-Secret information as described [here](https://www.alibabacloud.com/help/doc-detail/96384.htm). 

If you want to just test the JMS connectivity, the replier program can be run in an offline "simulation" mode. Details on this available further below.

### Step 1: Enter your JMS broker's connectivity details in the JNDI file:

The project currently uses the [Apache Qpid JMS client](https://qpid.apache.org/components/jms/index.html). This implements the [AMQP1.0](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol) open standard wire-line protocol - which has the advantage of being able to connect to any AMQP1.0 supported JMS broker.
[ActiveMQ](https://activemq.apache.org/) or [Solace PubSub+](https://solace.com/) both support the AMQP1.0 protocol so you can get the connection details for one of those.

Update the file [jndi.properties](jndi.properties) for the connection factory line to add details such as your AMQP URI hostname and port, username and password to connect to the JMS broker:

```
connectionfactory.ConnectionFactory = amqp://HOSTNAME-HERE:5672?jms.username=USERNAME-HERE&jms.password=PASSWORD-HERE&jms.clientIDPrefix=NLPTranslationSample-&
```


### Step 2: Enter your Machine Translation service's connectivity details in the properties file:

Once you have the access key and access secret on how to connect to your Machine Translation subscription, enter the details into the [alibaba-mt.properties](alibaba-mt.properties) file like so:

```
service-region=cn-hangzhou
access-key-id=IDGoesHere
access-key-secret=SecretGoesHere
```

_(Currently, the service is only available from a single region of "cn-hangzhou" so that detail is already filled in.)_

If you do not have access to the service yet and want to test the programs, the following property will run it in an offline simulation mode where JMS messages are passed between the applications, just the translation result is a placeholder:

```
# Optional parameter to start the Machine Translation functionality in a offline/simulation mode:
simulation-mode=true
```

### Step 3: Start the replier program

The replier program can be started first. It takes two arguments: the path to the JNDI file and the path to the MT service properties file. At the root of the checked out project:

```
java -jar AlibabaNLPReplier.jar -j ./jndi.properties -a ./alibaba-mt.properties
```

### Step 4: Start the requestor program

In another terminal start the requestor program. It just takes one argument: the path to the JNDI file for the JMS details:

```
java -jar AlibabaNLPRequestor.jar -j ./jndi.properties
```

### Step 5: Get translating!

In the requestor program enter text at the prompt and each new will result in a request being sent for translation. The next line response will show you the result.

Example output from requestor program:

![Console output from AlibabaNLPRequestor.jar](images/requestor-console.jpg)


Example output from replier program:

![Console output from AlibabaNLPReplier.jar](images/replier-console.jpg)

## Additional Information

### Running against alternative JMS Providers 

While the gradle project currently runs with the Qpid JMS Client and that is flexible enough to connect to multiple JMS providers, this project can very easily be updated to connect using other clients such as [ActiveMQ JMS Client](https://mvnrepository.com/artifact/org.apache.activemq/activemq-client) or [Solace PubSub+ native-protocol Client](https://mvnrepository.com/artifact/com.solacesystems/sol-jms).

The [build.gradle](build.gradle) file has placeholders in the "JMS Providers" section shown below.
To run the programs with another provider, comment the Qpid client entry and add the alternative provider's client.

```
	// JMS Provider's:
		// Apache Qpid JMS Client (connect to any AMQP1.0 supporting broker)
		compile group: 'org.apache.qpid', name: 'qpid-jms-client', version: '0.44.0'
		
		// Alternative Provider: ActiveMQ JMS Client
		//compile group: 'org.apache.activemq', name: 'activemq-client', version: '5.15.9'
		
		// Alternative Provider: Solace PubSub+ JMS Client
		//compile group: 'com.solacesystems', name: 'sol-jms', version: '10.6.3'
		
		// Alternative Provider: RabbitMQ JMS Client
		//compile group: 'com.rabbitmq.jms', name: 'rabbitmq-jms', version: '1.12.0'

```

The alternative JMS providers also have differences in the required contents for the JNDI properties file.
Example files have been included in the project on how to connect successfully to those alternatives.

- [solacepubsub-jndi.properties](solacepubsub-jndi.properties)
- [activemq-jndi.properties](activemq-jndi.properties)











