package sansonetti.controller;

import com.google.common.collect.Lists;
import com.mongodb.MongoWriteException;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sansonetti.model.Connection;
import sansonetti.model.Message;
import sansonetti.model.Profile;
import sansonetti.model.TwitterRequestBody;
import sansonetti.repository.ConnectionCollection;
import sansonetti.repository.MessageCollection;
import sansonetti.repository.ProfileCollection;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
public class Crawler {

    private TwitterFactory twitterFactory;

    //Extracts content from Twitter
    private Twitter twitter;
    //Used to retrieve information from Profile collection
    @Autowired
    private ProfileCollection profileCollection;
    //Used to retrieve information from Connection collection
    @Autowired
    private ConnectionCollection connectionCollection;
    //Used to retrieve information from Message collection
    @Autowired
    private MessageCollection messageCollection;
    //Contains hate terms related to the respective categories
    private HashMap<String, List<String>> vocabulary;
    //Contains the amount of tweet to be extracted
    private static int max_tweet_extraction;
    //Reports the crawler's execution status
    private static boolean RUNNING_STATE = false;
    //Used for read JSON response of a sentiment analysis algorithm
    private String JSON_CHILD_INDEX;
    //Used for read JSON response of a sentiment analysis algorithm
    private String JSON_SCORE_INDEX;
    //Used for read JSON response of a sentiment analysis algorithm
    private String JSON_SCORE_NEG_VALUE;
    //Contains the sentiment analysis algorithm used for processing the content of tweet
    private String SENTIMENT_ANALYSIS_ALGORITHM;

    private final int MAX_CONNECTIONS_ALLOWED_IN_DB = 5000000;

    private File file = new File("risultati_mappa_nuovo2");

    private FileWriter outputWriter;

    {
        try {
            outputWriter = new FileWriter(file, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private CSVWriter writer = new CSVWriter(outputWriter);

    private long start_application_time = System.nanoTime();
    private int profili_analizzati = 0;
    private int max_days_profile;

    public void start() {
        searchTwitterUser("TizianaVerdini");
        /*List<Profile> list = profileCollection.findBySourceAndConnections("twitter", false);
        for (int i = 0; i < list.size(); ) {
            System.out.flush();
            if (Crawler.RUNNING_STATE) {
                Profile profile = list.get(i);
                searchTwitterUser(profile.getScreenName());
                i++;
            }
        }*/
    }

    //Endpoint that provides the crawler execution status
    @GetMapping("/getState")
    public boolean getState() {
        return Crawler.RUNNING_STATE;
    }

    //Endpoint that allows to change crawler execution status
    @PostMapping("/setState")
    public void setState(@RequestBody TwitterRequestBody twitterRequestBody) {
        Crawler.RUNNING_STATE = twitterRequestBody.state;
        if (Crawler.RUNNING_STATE) {
            System.out.println("Il Crawler sta elaborando la coda");
        } else {
            System.out.println("Il Crawler è stato messo in pausa");
        }
    }

    //Start a user search on Twitter
    private void searchTwitterUser(String screenName) {
        System.out.println("Iniziata l'elaborazione per " + screenName);
        searchUser(screenName);
    }

    //Memorize an analyzed user with an overview of the intolerant tweets identified by category
    private void insertUser(User user, HashMap<String, Integer> categorie, boolean connections) {
        Profile profile = new Profile();
        profile.setScreenName(user.getScreenName());
        try {
            profile.setName(user.getName());
            profile.setDescription(user.getDescription());
            profile.setLocation(user.getLocation());
            profile.setWebsite(user.getURL());
            profile.setCreated_at(user.getCreatedAt());
            profile.setFollowing_count(user.getFriendsCount());
            profile.setFollower_count(user.getFollowersCount());
            profile.setSource("twitter");
            profile.setCategorie(categorie);
            profile.setConnections(connections);
            profile.setAggiornatoIl(LocalDate.now());
            //Verifico se il profilo è già presente per poter prendere se così, i valori della categorie vecchie
            Profile temp = profileCollection.findByScreenNameAndSource(profile.getScreenName(), "twitter");
            if (temp != null) {
                for (String categoria : temp.getCategorie().keySet()) {
                    profile.getCategorie().put(categoria, profile.getCategorie().get(categoria) + temp.getCategorie().get(categoria));
                }
            }
            profileCollection.deleteByScreenNameAndSource(user.getScreenName(), "twitter");
            profileCollection.insert(profile);
        } catch (MongoWriteException e) {
            System.out.println(e.getMessage());
        }
    }
    //Extracts and stores the connections of a profile
    private void insertTwitterConnections(String screenName, List<Long> following, List<Long> follower) {
        int total_connection_in_db = connectionCollection.countAllByContactIDNotNull();
        if (total_connection_in_db > MAX_CONNECTIONS_ALLOWED_IN_DB) {
            int total_connections_deleted = 0;
            Profile oldest_profile = profileCollection.findFirstByOrderByAggiornatoIlAsc();
            int deleted = connectionCollection.deleteByUsername(oldest_profile.getScreenName());
            total_connections_deleted = total_connections_deleted + deleted;
            while(total_connections_deleted<500000){
                oldest_profile = profileCollection.findFirstByOrderByAggiornatoIlAsc();
                deleted = connectionCollection.deleteByUsername(oldest_profile.getScreenName());
                total_connections_deleted = total_connections_deleted + deleted;
            }
        }
            Set<Long> following_connection_already_inserted = new HashSet<>();
            connectionCollection.findByUsernameAndType(screenName, "following").iterator().forEachRemaining(e -> following_connection_already_inserted.add(e.getContactID()));
            Set<Long> follower_connection_already_inserted = new HashSet<>();
            connectionCollection.findByUsernameAndType(screenName, "follower").iterator().forEachRemaining(e -> follower_connection_already_inserted.add(e.getContactID()));
            List<Connection> connectionList = new ArrayList<>();
            for (Long id : following) {
                if (!following_connection_already_inserted.contains(id)) {
                    Connection connection = new Connection();
                    connection.setUsername(screenName);
                    connection.setSource("twitter");
                    connection.setType("following");
                    connection.setContactID(id);
                    connectionList.add(connection);
                }
            }
            for (Long id : follower) {
                if (!follower_connection_already_inserted.contains(id)) {
                    Connection connection = new Connection();
                    connection.setUsername(screenName);
                    connection.setSource("twitter");
                    connection.setType("follower");
                    connection.setContactID(id);
                    connectionList.add(connection);
                }
            }
            connectionCollection.insert(connectionList);
    }
    // Recalls sentiment analysis services for extracted tweets
    private void insertTweet(String screenName, List<Status> statuses, User user, boolean connections) {
        Set<Long> message_already_inserted = new HashSet<>();
        messageCollection.findByFromUserAndSource(screenName, "twitter").iterator().forEachRemaining(e -> message_already_inserted.add(e.getoId()));
        List<Message> message_to_be_insert = new ArrayList<>();
        for (Status status : statuses) {
            Message message_tmp = new Message();
            message_tmp.setFromUser(status.getUser().getScreenName());
            message_tmp.setDate(status.getCreatedAt());
            try {
                message_tmp.setLatitude(status.getGeoLocation().getLatitude());
            } catch (NullPointerException e) {
            }
            try {
                message_tmp.setLongitude(status.getGeoLocation().getLongitude());
            } catch (NullPointerException e) {
            }
            message_tmp.setLanguage(status.getLang());
            message_tmp.setText(status.getText());
            if (status.isRetweet()) {
                message_tmp.setFavs(status.getRetweetedStatus().getFavoriteCount());
            } else {
                message_tmp.setFavs(status.getFavoriteCount());
            }
            message_tmp.setShares(status.getRetweetCount());
            message_tmp.setToUsers(status.getInReplyToScreenName());
            message_tmp.setoId(status.getId());
            message_tmp.setSource("twitter");
            if (!message_already_inserted.contains(status.getId())) {
                message_to_be_insert.add(message_tmp);
            }
        }
        //Divides the array containing the tweets to be submitted to the sentiment analysis service in groups of 50
        List<List<Message>> smallerLists = Lists.partition(message_to_be_insert, 50);
        HashMap<String, Integer> categorie = new HashMap<>();
        categorie.put("homophobia", 0);
        categorie.put("xenophobia", 0);
        categorie.put("disability", 0);
        categorie.put("sexism", 0);
        categorie.put("anti-semitism", 0);
        categorie.put("racism", 0);
        for (List<Message> temp : smallerLists) {
            scoreRequest(temp);
            temp.forEach(message -> message.getCategory().forEach(cat -> {
                categorie.put(cat, categorie.get(cat) + 1);
            }));
            try {
                messageCollection.insert(temp);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        //L'utente viene inserito con categorie basate solo sui tweet nuovi
        insertUser(user, categorie, connections);
    }

    //Used to send tweet to HanSEL sentiment analysis service
    private String sendPostRequest(String requestUrl, String payload) {
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            writer.write(payload);
            writer.close();
            System.out.println("Response Code : " + connection.getResponseCode());
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer jsonString = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
            connection.disconnect();
            return jsonString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }
    //Apply the sentiment analysis to tweets and at the end label each of these intolerants with the reference hate category
    private void scoreRequest(List<Message> message) {
        String json_string = null;
        try {
            if (SENTIMENT_ANALYSIS_ALGORITHM.equals("sentipolc")) {
                HttpClient client = new DefaultHttpClient();
                HttpResponse response;
                JSONObject post_data = new JSONObject();
                HttpPost post = new HttpPost("http://193.204.187.210:9009/sentipolc/v1/classify");
                JSONArray texts_data = new JSONArray();
                for (Message message_temp : message) {
                    JSONObject text = new JSONObject();
                    text.put("id", String.valueOf(message_temp.getoId()));
                    text.put("text", message_temp.getText());
                    texts_data.put(text);
                }
                post_data.put("texts", texts_data);
                StringEntity se = new StringEntity(post_data.toString());
                post.setEntity(se);
                response = client.execute(post);
                System.out.println("Response Code : " +
                        response.getStatusLine().getStatusCode());
                json_string = EntityUtils.toString(response.getEntity());
            } else if (SENTIMENT_ANALYSIS_ALGORITHM.equals("HanSEL")) {
                JSONArray payload = new JSONArray();
                for (Message message_temp : message) {
                    String tmp = message_temp.getText();
                    tmp = tmp.replaceAll("[^a-zA-Z0-9]", " ");
                    payload.put(tmp);
                }
                String quello_che_invio = "{\"tweets\":"+ payload.toString() + " }";
                System.out.println(quello_che_invio);
                try {
                    json_string = sendPostRequest("http://90.147.170.25:5000/api/hatespeech", "{\"tweets\":" + payload.toString() + " }");
                }catch(RuntimeException e){
                    System.out.println("IL SERVIZIO DI SENTIMENT HanSEL NON E' RAGGIUNGIBILE, SI STA UTILIZZANDO SENTIPOLC");
                    SENTIMENT_ANALYSIS_ALGORITHM = "sentipolc";
                    this.JSON_CHILD_INDEX = "results";
                    this.JSON_SCORE_INDEX = "polarity";
                    this.JSON_SCORE_NEG_VALUE = "neg";
                    scoreRequest(message);
                    return;
                }
                } else {
                System.out.println("Algoritmo di sentiment configurato non riconosciuto");
                System.exit(0);
            }
            System.out.println(json_string);
            JSONObject temp = new JSONObject(json_string);
            for (int i = 0; i < temp.getJSONArray(JSON_CHILD_INDEX).length(); i++) {
                if (temp.getJSONArray(JSON_CHILD_INDEX).getJSONObject(i).get(JSON_SCORE_INDEX).equals(JSON_SCORE_NEG_VALUE)) {
                    message.get(i).setScore(1);
                    //Ciclo sugli insulti
                    for (String insult : vocabulary.keySet()) {
                        //Ciclo su categoria
                        for (String category : vocabulary.get(insult)) {
                            String[] tweet_arr = message.get(i).getText().toLowerCase().split(" ");
                            String[] insult_arr = insult.split(" ");
                            if (insult_arr.length != 1) {
                                if (message.get(i).getText().replaceAll("[^a-zA-Z0-9]", " ").toLowerCase().contains(insult)) {
                                    if (insult.trim().equalsIgnoreCase("troia") || insult.trim().equalsIgnoreCase("puttana")) {
                                        if (cleanTweet(message.get(i).getText())) {
                                            message.get(i).getCategory().add(category);
                                            printLog(message.get(i).getText(), category, insult);
                                        }
                                    } else {
                                        message.get(i).getCategory().add(category);
                                        printLog(message.get(i).getText(), category, insult);
                                    }
                                }
                            } else {
                                for (String s : tweet_arr) {
                                    if (s.replaceAll("[^a-zA-Z0-9]", " ").trim().equals(insult)) {
                                        if (insult.trim().equalsIgnoreCase("troia") || insult.trim().equalsIgnoreCase("puttana")) {
                                            if (cleanTweet(message.get(i).getText())) {
                                                message.get(i).getCategory().add(category);
                                                printLog(message.get(i).getText(), category, insult);
                                            }
                                        } else {
                                            message.get(i).getCategory().add(category);
                                            printLog(message.get(i).getText(), category, insult);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    message.get(i).setScore(0);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private void printLog(String tweet, String category, String insult) {
        System.err.println("Termine sensibile trovato: " + insult);
        System.err.println(tweet + " ASSOCIATO ALLA CATEGORIA " + category);
        writer.writeNext(new String[]{tweet, insult, category});
        try {
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Report false intolerant tweets
    private boolean cleanTweet(String tweet) {
        return !tweet.toLowerCase().contains("porca puttana") && !tweet.toLowerCase().contains("porca troia") && !tweet.toLowerCase().contains("figlio di puttana") && !tweet.toLowerCase().contains("figlio di troia") &&
                !tweet.toLowerCase().contains("figli di puttana") && !tweet.toLowerCase().contains("figli di troia") && !tweet.toLowerCase().contains("cavallo di troia");
    }
    //Build a list containing the ids of followers
    private List<Long> getFollowersList(IDs ids_followers) {
        List<Long> follower_list = new ArrayList<>();
        for (long id : ids_followers.getIDs()) {
            follower_list.add(id);
        }
        return follower_list;
    }
    //Build a list containing the ids of following
    private List<Long> getFriendsList(IDs ids_following) {
        List<Long> following_list = new ArrayList<>();
        for (long id : ids_following.getIDs()) {
            following_list.add(id);
        }
        return following_list;
    }
    //Check API limits for each category and call handleRateLimit
    private void checkApiLimit() {
        Map<String, RateLimitStatus> rateLimitStatus = null;
        try {
            rateLimitStatus = twitter.getRateLimitStatus();
        } catch (TwitterException e) {
            e.printStackTrace();
        }
        for (String endpoint : rateLimitStatus.keySet()) {
            RateLimitStatus status = rateLimitStatus.get(endpoint);
            handleRateLimit(status);
        }
    }

    //manages API limits by pausing the main thread for the remaining time to request others
    private void handleRateLimit(RateLimitStatus rateLimitStatus) {
        int remaining = rateLimitStatus.getRemaining();
        if (remaining == 0) {
            int resetTime = rateLimitStatus.getSecondsUntilReset() + 5;
            int sleep = (resetTime * 1000);
            try {
                System.out.println("Mi addormento per " + TimeUnit.MINUTES.convert(sleep, TimeUnit.MILLISECONDS) + " minuti");
                System.out.flush();
                Thread.sleep(sleep > 0 ? sleep : 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //Extracts tweets from the platform
    private List<Status> searchUserTweet(String username) {
        Twitter twitter = twitterFactory.getInstance();
        int page_number = 1;
        List<Status> statuses = new ArrayList<>();
        while (true) {
            try {
                int size = statuses.size();
                Paging page = new Paging(page_number++, 100);
                ResponseList<Status> responseList = twitter.getUserTimeline(username, page);
                if ((statuses.size() + responseList.size()) < max_tweet_extraction) {
                    statuses.addAll(responseList);
                } else {
                    int temp = max_tweet_extraction - statuses.size();
                    statuses.addAll(responseList.subList(0, temp));
                }
                if (statuses.size() == size)
                    break;
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        }
        System.out.println("SONO STATI ESTRATTI " + statuses.size() + " tweet di " + username);
        return statuses;
    }

    private void searchUser(String username) {
        User user;
        try {
            user = twitter.showUser(username);
            if (user.getStatus() != null) {
                handleRateLimit(user.getRateLimitStatus());
                checkApiLimit();
                IDs ids_followers = twitter.getFollowersIDs(username, -1);
                handleRateLimit(ids_followers.getRateLimitStatus());
                IDs ids_following = twitter.getFriendsIDs(username, -1);
                handleRateLimit(ids_following.getRateLimitStatus());
                String searchedUserScreenName = user.getScreenName();
                insertTweet(username, searchUserTweet(username), user, true);
                long[] temp_following = ids_following.getIDs();
                for (int i = 0; i < temp_following.length; ) {
                    if (Crawler.RUNNING_STATE) {
                        long id = temp_following[i];
                        doContactExtraction(id, searchedUserScreenName, "following");
                        i++;
                    } else {
                        System.out.flush();
                    }
                }
                long[] temp_follower = ids_followers.getIDs();
                for (int i = 0; i < temp_follower.length; ) {
                    if (Crawler.RUNNING_STATE) {
                        long id = temp_follower[i];
                        doContactExtraction(id, searchedUserScreenName, "follower");
                        i++;
                    } else {
                        System.out.flush();
                    }
                }
            } else {
                // protected account
                System.out.println("Account protetto : " + user.getScreenName());
            }
        } catch (TwitterException error) {
            error.printStackTrace();
            System.out.println("Failed to get user's info: " + error.getMessage());
            System.exit(-1);
        }
    }
    //Prforms the extraction of the social network of a profile in the queue
    private void doContactExtraction(long id, String searchedUserScreenName, String type) throws TwitterException {
        String contact_screenName = twitter.showUser(id).getScreenName();
        Connection connection = new Connection("twitter", searchedUserScreenName, id, contact_screenName, type);
        connectionCollection.deleteByContactIDAndContactUsernameAndUsernameAndType(id, contact_screenName, searchedUserScreenName, type);
        connectionCollection.insert(connection);
        Profile following_profile = profileCollection.findByScreenNameAndSource(contact_screenName, "twitter");
        checkApiLimit();
        User following_user = twitter.showUser(contact_screenName);
        if (following_profile == null) {
            if (following_user.getStatus() != null) {
                performFollowerFollowingExtraction(contact_screenName, following_user);
                doStats();
            }
        } else {
            if (Period.between(LocalDate.now(), following_profile.getAggiornatoIl()).getDays() > max_days_profile) {
                performFollowerFollowingExtraction(contact_screenName, following_user);
                doStats();
            }
        }
    }

    private void doStats() {
        profili_analizzati++;
        long end = System.nanoTime();
        long elapsedTime = end - start_application_time;
        long minuti = TimeUnit.MINUTES.convert(elapsedTime, TimeUnit.NANOSECONDS);
        if (minuti > 15) {
            //logger.info("Sono stati analizzati " + profili_analizzati + " profili in " + minuti + " minuti");
            profili_analizzati = 0;
            start_application_time = end;
        }
    }
    //Extracts connections related to social network contacts
    private void performFollowerFollowingExtraction(String connectionScreenName, User connection) throws TwitterException {
        IDs ids_followers = twitter.getFollowersIDs(connectionScreenName, -1);
        handleRateLimit(ids_followers.getRateLimitStatus());
        IDs ids_following = twitter.getFriendsIDs(connectionScreenName, -1);
        handleRateLimit(ids_following.getRateLimitStatus());
        insertTwitterConnections(connectionScreenName, getFriendsList(ids_following)
                , getFollowersList(ids_followers));
        handleRateLimit(connection.getRateLimitStatus());
        insertTweet(connectionScreenName, searchUserTweet(connectionScreenName), connection, false);
        handleRateLimit(connection.getRateLimitStatus());
        checkApiLimit();
    }
    //Reads a CSV file containing intolerant terms
    public void readCSVFileVocabulary(String path) {
        vocabulary = new HashMap<>();
        try (CSVReader csvReader = new CSVReader(new FileReader(path))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                for (int i = 0; i < values.length; i++) {
                    if (!values[i].equals("")) {
                        String category = null;
                        switch (i) {
                            case 0:
                                category = "homophobia";
                                break;
                            case 1:
                                category = "racism";
                                break;
                            case 2:
                                category = "disability";
                                break;
                            case 3:
                                category = "sexism";
                                break;
                            case 4:
                                category = "anti-semitism";
                                break;
                            case 5:
                                category = "xenophobia";
                                break;
                        }
                        if (category != null) {
                            if (vocabulary.containsKey(values[i])) {
                                vocabulary.get(values[i]).add(category);
                            } else {
                                List<String> list = new ArrayList<>();
                                list.add(category);
                                vocabulary.put(values[i], list);
                            }
                        }
                    }
                }
            }
            for (String string : vocabulary.keySet()) {
                System.out.println("Per il termine " + string + " sono state trovare le seguenti categorie");
                for (String list : vocabulary.get(string)) {
                    System.out.println(list);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Reads the configuration file in which the configuration of the workflow to be executed is specified
    public void readConfigFile(String path) {
        HashMap<String, String> configField = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(":");
                configField.put(values[0], values[1]);
                System.out.println(values[0] + " " + values[1]);
            }
            ConfigurationBuilder configurationBuilder = new ConfigurationBuilder().setDebugEnabled(true).setOAuthConsumerKey(configField.get("OAuthConsumerKey")).
                    setOAuthConsumerSecret(configField.get("OAuthConsumerSecret")).
                    setOAuthAccessToken(configField.get("OAuthAccessToken")).
                    setOAuthAccessTokenSecret(configField.get("OAuthAccessTokenSecret")).setTweetModeExtended(true);
            twitterFactory = new TwitterFactory(configurationBuilder.build());
            twitter = twitterFactory.getInstance();
            Crawler.max_tweet_extraction = Integer.valueOf(configField.get("max_tweet_extraction"));
            max_days_profile = Integer.valueOf(configField.get("max_days_profile"));
            if (configField.get("sentiment_analysis_algorithm").equals("sentipolc")) {
                this.SENTIMENT_ANALYSIS_ALGORITHM = "sentipolc";
                this.JSON_CHILD_INDEX = "results";
                this.JSON_SCORE_INDEX = "polarity";
                this.JSON_SCORE_NEG_VALUE = "neg";

            } else if (configField.get("sentiment_analysis_algorithm").equals("HanSEL")) {
                this.SENTIMENT_ANALYSIS_ALGORITHM = "HanSEL";
                this.JSON_CHILD_INDEX = "results";
                this.JSON_SCORE_INDEX = "class";
                this.JSON_SCORE_NEG_VALUE = "1";
            } else {
                System.out.println("Algoritmo di sentiment configurato non riconosciuto");
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
