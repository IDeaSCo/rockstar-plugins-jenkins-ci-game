package hudson.plugins.cigame;

import hudson.model.User;
import hudson.tasks.MailAddressResolver;

import hudson.model.*;
import org.kohsuke.stapler.DataBoundConstructor;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
/**
 * Created by idnvge on 9/26/2014.
 */
public class GalaxyUpdater {
    private String ideasRockStarURI = "http://localhost:13082/star/trophy/save";

    @DataBoundConstructor
    public GalaxyUpdater(String ideasRockStarURI){
        this.ideasRockStarURI=ideasRockStarURI;
    }
    public GalaxyUpdater(){

    }
    public boolean update(Set<User> players, double score, String reason, BuildListener listener, String badge) throws IOException, ClassNotFoundException {

    	listener.getLogger().append("[ci-game] about to post STAR status\n");
        for(User player:players) {
            try {
        	
            	listener.getLogger().append("[ci-game] finding email address for player: " + player + "\n");     
	            URL obj = new URL(ideasRockStarURI);
	            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
	    		listener.getLogger().append("[ci-game] posting to URL: " + ideasRockStarURI + "\n");
	
	            con.setRequestMethod("POST");
	            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
	            con.setRequestProperty("Content-Type", "application/json");
	
	
	            UserScoreProperty property = player.getProperty(UserScoreProperty.class);
	            String emailId = MailAddressResolver.resolve(player);
	
	            String urlParameters = "{ \"fromUserEmailID\":\"jenkins.user@ideas.com\", \"toUserEmailID\":\"" +
	                    emailId+"\"" +
	                    ",\"trohpies\":" +
	                    score +
                        ",\"badgeName\":" +
                        "\""+badge +"\""+
	                    ",\"reason\":\"Jenkins:"+reason+"\"}";
	
	            con.setRequestProperty("Content-Length", "" + urlParameters.length());
	
	            System.out.println("\nSending 'POST' request to URL : " + ideasRockStarURI);
	            System.out.println("Post parameters : " + urlParameters);
	            listener.getLogger().append("[ci-game] Post parameters: " + urlParameters + "\n");
	
	            // Send post request
	            con.setDoOutput(true);
	            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
	            wr.writeBytes(urlParameters);
	            wr.flush();
	            wr.close();
	
	            int responseCode = con.getResponseCode();
	
	            
	            listener.getLogger().append("[ci-game] Response Code : " + responseCode+"\n");
	
	            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
	            String inputLine;
	            StringBuffer response = new StringBuffer();
	
	            while ((inputLine = in.readLine()) != null) {
	                response.append(inputLine);
	            }
	            in.close();
            }catch (Exception e) {
            	listener.getLogger().append("[ci-game] Could not post stats for player: " + player+"\n");
            }
        }
        return true;
    }
}
