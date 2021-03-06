package server;

import java.util.ArrayList;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import common.Timeline;
import common.User;
import common.messages.AckResponse;
import common.messages.ClientRequest;
import common.messages.FollowRequest;
import common.messages.GenericErrorResponse;
import common.messages.ImageRequest;
import common.messages.ImageResponse;
import common.messages.MessageType;
import common.messages.TimelineResponse;

public class RequestHandler extends Handler implements MessageListener, Runnable {

	public RequestHandler(String identity) {
		super(identity);
	}

	private JMSContext jmsContext;
	private JMSConsumer consumer;
	private JMSProducer producer;
	private int count = 0;
	
	@Override
	public void onMessage(Message msg) {
		
		print("onMessage");
		
		count ++;
		
		try {
			Queue replyToQueue = (Queue) msg.getJMSReplyTo();
			ClientRequest request = msg.getBody(ClientRequest.class);
		
			switch(request.getType()){
				case SUBSCRIBE:
					print("subscribe");
					
					if(createSubscription(request)){
						print("new user registered [ " + request.getUsername() + " ]");
						producer.send(replyToQueue, new AckResponse(MessageType.SUBSCRIBE, 1));	
					}else{
						print("user registration error");
						producer.send(replyToQueue, new AckResponse(MessageType.SUBSCRIBE, 0));	
					}
					break;
				case FOLLOW:
					print("follow");
					
					if(follow(request)){
						print("updated following users by user [ " + request.getUsername() + " ]");
						producer.send(replyToQueue, new AckResponse(MessageType.FOLLOW, 1));	
					}else{
						print("following registration error");
						producer.send(replyToQueue, new AckResponse(MessageType.FOLLOW, 0));	
					}
					break;
				case IMAGE:
					print("image");
					byte[] replyImage = getImage(request);
					
					if(replyImage != null){
						print("loaded image requestes by user [ " + request.getUsername() + " ]");
						producer.send(replyToQueue, new ImageResponse(MessageType.IMAGE, 1, replyImage));
					}else{
						print("load image error");
						producer.send(replyToQueue, new ImageResponse(MessageType.IMAGE, 0, replyImage));
					}
					break;
				case TIMELINE:
					print("timeline");
					Timeline replyTimeline = getTimeline(request);
					
					if(replyTimeline != null){
						print("loaded image requestes by user [ " + request.getUsername() + " ]");
						producer.send(replyToQueue, new TimelineResponse(MessageType.TIMELINE, 1, replyTimeline));
					}else{
						print("load image error");
						producer.send(replyToQueue, new TimelineResponse(MessageType.TIMELINE, 0, replyTimeline));
					}
					break;
				default:
					print("the system can't detect the type of the ClientRequest, generic error is sent back");
					
					producer.send(replyToQueue, new GenericErrorResponse());
					break;
			}
		} catch (JMSException e) {
			e.printStackTrace();
		}		
	}
	
	/** Function that handles the subscription of a user, 
	 * adding it to the Resources users
	 */
	private boolean createSubscription(ClientRequest request) {
		
		print("createSubscription");
		
		User newUser = new User(request.getUsername());
		
		return Resources.RS.addUser(newUser);	
		
	}
	
	/** Function that handles the follow operation 
	 * adding user to the followers of toFollow user
	 * return false if a user(sender or toFollow) doesn't exists
	 */  
	private boolean follow(ClientRequest request){  
		
		print("follow");
		
		User user = Resources.RS.getUserById(request.getUsername());
		ArrayList<String> usersToAdd = ((FollowRequest)request).getUsers();
		if(user != null && !usersToAdd.isEmpty()){
			ArrayList<User> tmpUsers = new ArrayList<User>();
			for(String toAdd: usersToAdd) {
				User userToAdd = Resources.RS.getUserById(toAdd);
				if(userToAdd == null)
					return false;
				else
					tmpUsers.add(userToAdd);
			}
			for (User u: tmpUsers){
				u.addFollower(user);
			}
			return true;
			
		} else {
			
			return false;
			
		}
		
	}
	
	/** Function that handles the request to get a Timenline 
	 *  return a null if user doesn't exists
	 */ 
	private Timeline getTimeline(ClientRequest request){
		
		print("getTimeline");
		
		User user = Resources.RS.getUserById(request.getUsername());
		if(user != null){
			return user.getMytimeline();
		}
		return null;
	}

	/** Function that handles the request to get an image
	 *  return a null if the image doesn't exist
	 */ 
	private byte[] getImage(ClientRequest request){
		
		print("getImage");
		
		User user = Resources.RS.getUserById(request.getUsername());
		String name = ((ImageRequest)request).getImageID();
		
		if(user != null){
			byte[] img = Resources.RS.getImage(name);
			return img;
		}
		return null;
	}

	@Override
	public void run() {
		Context initialContext;
		try {
			initialContext = getContext();
			jmsContext = ((ConnectionFactory) initialContext.lookup("java:comp/DefaultJMSConnectionFactory")).createContext();
			
			//lookup tweetQueue and set messageListener
			String requestQueueName ="requestQueue";
			Queue requestQueue = (Queue) initialContext.lookup(requestQueueName);
			consumer = jmsContext.createConsumer(requestQueue);
			consumer.setMessageListener(this);
			
			producer = jmsContext.createProducer();
			
		} catch (NamingException e) {
			e.printStackTrace();
		}

		print("started");
	}
	
	public void stopListening(){
		print("Stopping by browser, elaborated " + count);
		consumer.close();
	}
	
	private Context getContext() throws NamingException {
		
		Properties props = new Properties();
		props.setProperty("java.naming.factory.initial", "com.sun.enterprise.naming.SerialInitContextFactory");
		props.setProperty("java.naming.factory.url.pkgs", "com.sun.enterprise.naming");
		props.setProperty("java.naming.provider.url", "iiop://localhost:3700");
		
		return new InitialContext(props);
	}	
}
