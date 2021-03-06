package client;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import common.Tweet;
import common.messages.ClientRequest;
import common.messages.FollowRequest;
import common.messages.ImageRequest;
import common.messages.ImageResponse;
import common.messages.RegistrationRequest;
import common.messages.ServerResponse;
import common.messages.TimelineRequest;
import common.messages.TimelineResponse;

import javax.imageio.ImageIO;
import javax.jms.ConnectionFactory;

public class Client implements MessageListener {
	
	static String username = null;
	static Boolean control = true;
	static Tweet tweet = null;
	static byte[] fakeImageInByte = null;
	
	public static void main(String[] args) throws NamingException, IOException {
		
		print("starting");
		
		String tweetQueueName = "tweetQueue";
		String requestQueueName = "requestQueue";
		
		Context initialContext = Client.getContext(); 
		JMSContext jmsContext = ((ConnectionFactory) initialContext.lookup("java:comp/DefaultJMSConnectionFactory")).createContext();
		
		Queue tweetQueue = (Queue) initialContext.lookup(tweetQueueName);
		Queue requestQueue = (Queue) initialContext.lookup(requestQueueName);
		Queue subscribeQueue = jmsContext.createTemporaryQueue();
		
		JMSProducer jmsProducer = jmsContext.createProducer().setJMSReplyTo(subscribeQueue);
		
		jmsContext.createConsumer(subscribeQueue).setMessageListener(new Client());
		
		interact(requestQueue, tweetQueue, jmsProducer);
		
	}

	private static void initTweetTemplate(boolean withImage) throws IOException {
		
		//print("initTweetTemplate");
		if (withImage){
			BufferedImage fakeImage = ImageIO.read(new File("resources/sample.jpg"));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(fakeImage, "jpg", baos );
			fakeImageInByte = baos.toByteArray();
			
			tweet = new Tweet(username, fakeImageInByte, null);
		}else{
			tweet = new Tweet(username, null, null);
		}
	}

	private static void interact(Queue requestQueue, Queue tweetQueue, JMSProducer jmsProducer) throws IOException {
		
		print("interact");
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		print("Type your username: ");
		username = br.readLine();
		
		while(control) {
			
			print("Which action do you want to perform ?");
			print("\n[1] Register \n[2] Send Tweet with Image \n[3] Send Tweet without Image \n[4] RequestImage \n[5] Follow \n[6] RequestTimeline \nDEFAULT QUITS");
			
			if(username == null) {
				print("Type your username: ");
				username = br.readLine();
			}
			
	        String choice = br.readLine();	
	        ClientRequest request = null;
	        String caption = "";
	        
	        switch(choice) {
	        	case "1": //Register
	        		request = new RegistrationRequest(username);
	        		sendRequest(jmsProducer, requestQueue, request);
	        		break;
	        	case "2": //Tweet with image
	        		initTweetTemplate(true);
					print("Type tweet caption: ");
					caption = br.readLine();
					
					tweet.setText(caption);
					//print(tweet.toString() + " is going to be sent");
					sendRequest(jmsProducer, tweetQueue, tweet);
					break;
	        	case "3": //Tweet w/o image
	        		initTweetTemplate(false);
					print("Type tweet caption: ");
					caption = br.readLine();
					
					tweet.setText(caption);
					//print(tweet.toString() + " is going to be sent");
					sendRequest(jmsProducer, tweetQueue, tweet);
					break;
	        	case "4": //get image
	        		print("Type the image ID: ");
	        		String imageID = br.readLine();
	        		
	        		request = new ImageRequest(username, imageID);
	        		sendRequest(jmsProducer, requestQueue, request);
	        		break;
	        	case "5": //follow
	        		print("Type the users you want to follow (space separated): ");
	        		String[] followUsers = null;
	        		String input = br.readLine();
	        		
	        		followUsers = input.split("\\s+");
	        		request = new FollowRequest(username, new ArrayList<String>(Arrays.asList(followUsers)));
	        		sendRequest(jmsProducer, requestQueue, request);
	        		break;
	        	case "6": //get timeline
	        		request = new TimelineRequest(username);
	        		sendRequest(jmsProducer, requestQueue, request);
	        		break;
	        	default :
	        		control = false;
	        		break;
	        }
		}
		print("closing");
		
	}

	private static boolean sendRequest(JMSProducer jmsProducer, Queue requestQueue, ClientRequest request) {
		
		print("sendRequest - " + request.getType().toString());
		
		//print("> Sending request");
		
		jmsProducer.send(requestQueue, request);
		
		print("Request sent");
		return true;
		
	}

	private static Context getContext() throws NamingException {
		
		//print("getContext");
		
		Properties props = new Properties();
		props.setProperty("java.naming.factory.initial", "com.sun.enterprise.naming.SerialInitContextFactory");
		props.setProperty("java.naming.factory.url.pkgs", "com.sun.enterprise.naming");
		props.setProperty("java.naming.provider.url", "iiop://localhost:3700");
		
		return new InitialContext(props);
	}

	@Override
	public void onMessage(Message msg) {
		
		//print("onMessage");
		try {
			
			renderResponse(msg.getBody(ServerResponse.class));
			
		} catch (JMSException e) {

			e.printStackTrace();
			
		}
	}

	private void renderResponse(ServerResponse body) {
		
		ServerResponse bd = body;
		//print("renderResponse");
		switch(body.getType()){
			case TIMELINE:
				bd = (TimelineResponse) body;
				break;
			case IMAGE:
				bd =(ImageResponse) body;
			default:
				bd = body;
		}
		print("RESPONSE: " + bd.render());
	}

	public static void print(String s) {
		System.out.println("> Client > " + s);
	}
	
}
