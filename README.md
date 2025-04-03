This project demonstrates a thread-safety issue that was discovered when using EclipseLink's default JMS cache coordination with ActiveMQ Artemis.

The problematic EclipseLink code is in `JMSTopicRemoteConnection`. A JMS `Message` is received on one looping thread, but will be read and handled in a background thread. The code looks roughly like this:

```java
 Message message = subscriber.receive();
 ...
 rcm.getServerPlatform().launchContainerRunnable(new JMSOnMessageHelper(message));
```

When using ActiveMQ Artemis, any messages larger than 100kb will be treated as 'large messages' and the body read is deferred. When the body read happens on a different thread than the receiver there is a risk of message corruption or failure.

The code in this repository mimics the EclipseLink behaviour by creating a large Java serialized object, sending it as a `BytesMessage` once *every second*, then receiving it, and finally processing it on a different thread. For small serialized objects (less than 100kb) there is no problem, but for larger objects the data is corrupted or messages are not received.

To run this project:

1. Run `mvn exec:java`
2. See that messages are sent, but are sometimes received corrupted (`java.io.StreamCorruptedException: invalid stream header`) or not received at all
3. Change `ActiveMQTest.MESSAGE_BYTES` to 50kb or change `ActiveMQTest.USE_ARTEMIS` to false, and re-run to see that messages are then received reliably

A typical output of this code would show a message sent every second, but it is either received corrupted (`invalid stream header` during deserialization) or read successfully or received with a significant delay:

```
[pool-1-thread-2] INFO ActiveMQTest - Messages will have length of 204827 bytes
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7a59698d-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7a59698d-10aa-11f0-809e-26c81327d92e has been received
[pool-2-thread-1] INFO ActiveMQTest - Message ID:7a59698d-10aa-11f0-809e-26c81327d92e has length of 204827 bytes
[pool-2-thread-1] ERROR ActiveMQTest - Message ID:7a59698d-10aa-11f0-809e-26c81327d92e threw an exception
java.io.StreamCorruptedException: invalid stream header: 00C7F900
	at java.base/java.io.ObjectInputStream.readStreamHeader(ObjectInputStream.java:989)
	at java.base/java.io.ObjectInputStream.<init>(ObjectInputStream.java:416)
	at ActiveMQTest.readSerializedObject(ActiveMQTest.java:182)
	at ActiveMQTest.evaluate(ActiveMQTest.java:152)
	at ActiveMQTest.lambda$2(ActiveMQTest.java:112)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	at java.base/java.lang.Thread.run(Thread.java:1583)
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7af5345e-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7af5345e-10aa-11f0-809e-26c81327d92e has been received
[pool-2-thread-1] INFO ActiveMQTest - Message ID:7af5345e-10aa-11f0-809e-26c81327d92e has length of 204827 bytes
[pool-2-thread-1] INFO ActiveMQTest - Message ID:7af5345e-10aa-11f0-809e-26c81327d92e was read successfully
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7b8edc4f-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7c28d260-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7cc3b2d1-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7d5d81d2-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7df777e3-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7e916df4-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7f2b6405-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:7fc50bf6-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:805eb3e7-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:80f85bd8-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:819278f9-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:822c47fa-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:82c6da4b-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8360f76c-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:83fac66d-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8494474e-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:852e164f-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:85c7be40-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:86613f21-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:86fb3532-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:87952b43-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:882eac24-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:88c82d05-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8961ade6-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:89fb55d7-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8a94afa8-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8b2ea5b9-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8bc874ba-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8c626acb-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7b8edc4f-10aa-11f0-809e-26c81327d92e has been received
[pool-2-thread-1] INFO ActiveMQTest - Message ID:7b8edc4f-10aa-11f0-809e-26c81327d92e has length of 204827 bytes
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7c28d260-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7cc3b2d1-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7d5d81d2-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7df777e3-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7e916df4-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7f2b6405-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:7fc50bf6-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:805eb3e7-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:80f85bd8-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:819278f9-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:822c47fa-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:82c6da4b-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8360f76c-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:83fac66d-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8494474e-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:852e164f-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:85c7be40-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:86613f21-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:86fb3532-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:87952b43-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:882eac24-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:88c82d05-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8961ade6-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:89fb55d7-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8a94afa8-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8b2ea5b9-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8bc874ba-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8c626acb-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8cfc12bc-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8cfc12bc-10aa-11f0-809e-26c81327d92e has been received
[pool-1-thread-2] INFO ActiveMQTest - Message ID:8d95e1bd-10aa-11f0-809e-26c81327d92e has been sent
[pool-1-thread-1] INFO ActiveMQTest - Message ID:8d95e1bd-10aa-11f0-809e-26c81327d92e has been received

```

It's not clear  whether the EclipseLink code is incorrect by using background threads for processing messages, or if ActiveMQ Artemis is violating thread-safety requirements in the Jakarta Messaging spec. The specification says:

> the Jakarta Messaging design restricts its requirement for concurrent access to those objects that would naturally be shared by a multi-threaded client. The remaining objects are designed to be accessed by one logical thread of control at a time.

and EclipseLink is clearly using "one logical thread of control at a time" for the `Message`. Perhaps the `Message` and `Session` are required to use the same thread?

Known workarounds are:

* specify `&minLargeMessageSize=...` in the ActiveMQ Artemis URL, using some large number
* use ActiveMQ Classic
