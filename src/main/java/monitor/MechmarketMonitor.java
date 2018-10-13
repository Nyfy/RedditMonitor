package monitor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.SubredditSort;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.pagination.DefaultPaginator;
import net.dean.jraw.references.InboxReference;
import net.dean.jraw.references.OtherUserReference;
import net.dean.jraw.references.SubredditReference;

public class MechmarketMonitor {
    private static String CONFIG = "resources/config.properties";
    
    private static String PLATFORM_PROPERTY = "username";
    private static String APPID_PROPERTY = "appId";
    private static String VERSION_PROPERTY = "version";
    private static String USERNAME_PROPERTY = "username";
    private static String PASSWORD_PROPERTY = "password";
    private static String CLIENTID_PROPERTY = "clientId";
    private static String CLIENTSECRET_PROPERTY = "clientSecret";
    private static String ALERT_ACCOUNT = "alertAccount";
    
    private static String SUBREDDIT_BASE = "subreddit";
    private static String SEARCHSTRINGS_BASE = "searchStrings";
    private static String[] SUFFIXES = {"A","B","C","D","E","F","G","H","I","J"};
    
    private static String username;
    private static String password;
    private static String clientId;
    private static String clientSecret;

    private static String targetUser;
    
    private static String platform;
    private static String appId;
    private static String version;
    
    private Properties props;
    private RedditClient redditClient;
    
    private Map<SubredditReference,List<String>> searchStrings;
    
    public static void main(String[] args) throws InterruptedException, FileNotFoundException, IOException {  
        MechmarketMonitor monitor = new MechmarketMonitor();
        monitor.startMonitoring();
    }
    
    public MechmarketMonitor() throws FileNotFoundException, IOException {
        initializeProperties();
        initializeRedditClient();
        initializeSubredditSearches();
    }
    
    private void initializeProperties() throws FileNotFoundException, IOException {
        props = new Properties();
        props.load(new FileInputStream(CONFIG));
        
        username = props.getProperty(USERNAME_PROPERTY);
        password = props.getProperty(PASSWORD_PROPERTY);
        clientId = props.getProperty(CLIENTID_PROPERTY);
        clientSecret = props.getProperty(CLIENTSECRET_PROPERTY);
        platform = props.getProperty(PLATFORM_PROPERTY);
        appId = props.getProperty(APPID_PROPERTY);
        version = props.getProperty(VERSION_PROPERTY);
        targetUser = props.getProperty(ALERT_ACCOUNT);
    }
    
    private void initializeRedditClient() {
        Credentials oauthCreds = Credentials.script(username, password, clientId, clientSecret);
        UserAgent userAgent = new UserAgent(platform, appId, version, username);
        
        redditClient = OAuthHelper.automatic(new OkHttpNetworkAdapter(userAgent), oauthCreds);
    }
    
    private void initializeSubredditSearches() {
        searchStrings = new HashMap<SubredditReference,List<String>>();
        
        for (String suffix : SUFFIXES) {
            String subreddit = props.getProperty(SUBREDDIT_BASE+suffix);
            String[] searches = props.getProperty(SEARCHSTRINGS_BASE+suffix).split(";");
            
            if (!subreddit.isEmpty()) {
                searchStrings.put(redditClient.subreddit(subreddit), new ArrayList<String>(Arrays.asList(searches)));
            }
        }
    }
    
    private void startMonitoring() throws InterruptedException {
        List<String> permalinksVisited = new ArrayList<String>();
        
        while (true) {
            while (permalinksVisited.size() > searchStrings.size() * 10) {
                permalinksVisited.remove(0);
            }
            
            for (SubredditReference subredditRef : searchStrings.keySet()) {
                DefaultPaginator<Submission> paginator = subredditRef.posts()
                        .limit(10)
                        .sorting(SubredditSort.NEW)
                        .build();
                
                Iterator<Submission> submissions = paginator.next().iterator();
                
                while (submissions.hasNext()) {
                    Submission submission = submissions.next();
                    String permalink = "https://www.reddit.com"+submission.getPermalink();
                     
                    if (!permalinksVisited.contains(permalink)) {
                        permalinksVisited.add(permalink);
                        
                        String submissionTitle = submission.getTitle().toLowerCase();
                        String submissionBody = submission.getSelfText().toLowerCase();
                        
                        for (String search : searchStrings.get(subredditRef)) {
                            if (submissionBody.contains(search) || submissionTitle.contains(search)) {
                                alert(search, permalink);
                            }
                        }
                    }
                }
            }
            Thread.sleep(10000);
        }
    }
    
    private void alert(String matchedString, String permalink) {
        System.out.println(matchedString);
        InboxReference inbox = redditClient.me().inbox();
        inbox.compose(targetUser, "Alert for "+matchedString, "New match found for `"+matchedString+"` found at "+permalink);
    }
}
