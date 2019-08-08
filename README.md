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
```

## Running the Samples

### Pre-requisites:

1. Get access to connection details for a running JMS broker supporting AMQP. 
e.g. You may wish to download and run ActiveMQ locally following instructions [here](https://activemq.apache.org/getting-started).
Alternatively you could access a free hosted JMS broker instance via [Solace Cloud](https://solace.com/cloud/). 

2. Activate the Machine Translation service in your Alibaba Cloud / Aliyun subscription and collect the AccessID and Access-Secret information as described [here](https://www.alibabacloud.com/help/doc-detail/96384.htm). 

### Set up your JMS details in the JNDI file:

### Set up your Machine Translation service's connectivity in the properties file:

### Start the replier program

### Start the requestor program

## Further Information

















