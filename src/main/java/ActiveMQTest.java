import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.Topic;

/**
 * Demonstrates that {@link BytesMessage} bodies may be corrupted when using
 * ActiveMQ Artemis, the size exceeds the 'large message' threshold (default is
 * 100kb), and the message body is read on a background thread. This mimics the
 * behaviour of EclipseLink's default JMS cache coordination code which is where
 * this issue was discovered.
 */
public class ActiveMQTest {
	private static final Logger LOG;

	/** Configure the Logger, and only show ActiveMQ errors. */
	static {
		System.setProperty(org.slf4j.simple.SimpleLogger.LOG_KEY_PREFIX + "org.apache", "ERROR");
		LOG = LoggerFactory.getLogger(ActiveMQTest.class);
	}

	/** ActiveMQ URL */
	private static final String URL = "tcp://localhost:61616";

	/** ActiveMQ topic */
	private static final String TOPIC = "random.topic";

	/** Approximate desired JMS message size in bytes */
	private static final int MESSAGE_BYTES = 200 * 1024;

	/** Indicates whether to test using ActiveMQ Artemis or ActiveMQ Classic. */
	private static final boolean USE_ARTEMIS = true;

	/**
	 * Main method to start an ActiveMQ server, then start sending and receiving
	 * messages, with received messages processed on a single background thread.
	 * <p>
	 * If using Artemis and the messages are 'large' (more than 100kb) then the
	 * message runs a risk of being corrupted.
	 */
	public static void main(String[] args) throws Exception {
		startServer();
		Executor executor = Executors.newFixedThreadPool(2);
		executor.execute(ActiveMQTest::runReader);
		executor.execute(ActiveMQTest::runWriter);
	}

	/** Loop that sends one BytesMessage every second. */
	private static void runWriter() {
		try {
			ConnectionFactory connectionFactory = createConnectionFactory();
			Connection connection = connectionFactory.createConnection();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Topic topic = session.createTopic(TOPIC);
			connection.start();

			byte[] bytes = createSerializedObject(MESSAGE_BYTES);
			LOG.info("Messages will have length of {} bytes", bytes.length);

			MessageProducer producer = session.createProducer(topic);
			while (true) {
				Thread.sleep(1000);
				BytesMessage message = session.createBytesMessage();
				message.writeBytes(bytes);
				producer.send(message);
				LOG.info("Message {} has been sent", message.getJMSMessageID());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Loop that reads every message and processes it. */
	private static void runReader() {
		try {
			ConnectionFactory connectionFactory = createConnectionFactory();
			Connection connection = connectionFactory.createConnection();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Topic topic = session.createTopic(TOPIC);
			connection.start();

			Executor executor = Executors.newFixedThreadPool(1);
			MessageConsumer consumer = session.createConsumer(topic, null, true);

			/*
			 * THE CRITICAL LOOP. This loop receives a Message on the current thread, but
			 * immediately passes evaluation of the loop to
			 */
			while (true) {
				Message message = consumer.receive();
				LOG.info("Message {} has been received", message.getJMSMessageID());
				executor.execute(() -> evaluate(message));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Helper method to start an ActiveMQ server (Artemis or Classic). */
	private static void startServer() throws Exception {
		if (USE_ARTEMIS) {
			Configuration config = new ConfigurationImpl();
			config.addAcceptorConfiguration("tcp", URL);
			config.setSecurityEnabled(false);
			EmbeddedActiveMQ embedded = new EmbeddedActiveMQ();
			embedded.setConfiguration(config);
			embedded.start();
		} else {
			BrokerService broker = new BrokerService();
			broker.addConnector(URL);
			broker.start();
		}
	}

	/** Helper to creates an ActiveMQ ConnectionFactory (Artemis or Classic). */
	private static ConnectionFactory createConnectionFactory() {
		if (USE_ARTEMIS) {
			return new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory(URL);
		} else {
			return new org.apache.activemq.ActiveMQConnectionFactory(URL);
		}
	}

	/** Helper method that takes a Message and deserializes the body. */
	private static void evaluate(Message message) {
		try {
			if (message instanceof BytesMessage) {
				BytesMessage msg = (BytesMessage) message;
				byte[] array = new byte[(int) msg.getBodyLength()];
				LOG.info("Message {} has length of {} bytes", message.getJMSMessageID(), array.length);
				msg.readBytes(array);
				Object object = readSerializedObject(array);
				if (object instanceof int[]) {
					LOG.info("Message {} was read successfully", message.getJMSMessageID());
				} else {
					LOG.info("Message {} was read as {}", message.getJMSMessageID(), object);
				}
			} else {
				LOG.error("Message {} was unexpectedly a ", message.getJMSMessageID(), message.getClass());
			}
		} catch (Exception e) {
			try {
				LOG.error("Message {} threw an exception", message.getJMSMessageID(), e);
			} catch (JMSException e1) {
				LOG.error("?????? --> threw an exception", e);
			}
		}
	}

	/** Helper method to create a serialized object of a rough size. */
	private static byte[] createSerializedObject(int roughBytes) throws Exception {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream os = new ObjectOutputStream(bos)) {
			Object obj = IntStream.range(0, roughBytes / 4).toArray();
			os.writeObject(obj);
			os.flush();
			return bos.toByteArray();
		}
	}

	/** Helper method to read a serialized object from a byte array. */
	private static Object readSerializedObject(byte[] array) throws Exception {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(array); ObjectInputStream is = new ObjectInputStream(bis)) {
			return is.readObject();
		}
	}
}
