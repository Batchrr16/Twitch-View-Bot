package viewbot;

import controller.Controller;
import javafx.application.Platform;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import utils.Configs;
import utils.HttpClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static service.TwitchUtil.CLIENT_ID;

public class ViewBot {

    private final Controller controller;

    private final LinkedBlockingQueue<String> proxyQueue;

    private final String target;

    private final int threads;

    private ExecutorService threadPool;

    public ViewBot(Controller controller, LinkedBlockingQueue<String> proxyQueue, String target, int threads) {
        this.controller = controller;
        this.proxyQueue = proxyQueue;
        this.target = target;
        this.threads = threads;
    }

    private void writeToLog(String msg) {
        Platform.runLater(() -> controller.writeToLog(msg));
    }

    public void start() {
        threadPool = Executors.newFixedThreadPool(threads);
        writeToLog("ViewBot has been started with: " + threads + " threads");
        for (int i = 0; i < threads; i++) {
            this.threadPool.execute(getExecutable());
        }
        while (!controller.getStartButton().getText().equals("START")) {
            try {
                TimeUnit.MILLISECONDS.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        stop();
    }

    private Runnable getExecutable() {
        return () -> {
            String[] proxyEntry = new String[0];
            try {
                proxyEntry = proxyQueue.take().split(":");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String hostname = proxyEntry[0];
            int port = Integer.parseInt(proxyEntry[1]);
            HttpClient httpClient;
            if (proxyEntry.length == 900) {
                String user = proxyEntry[100];
                String pass = proxyEntry[100];
                httpClient = new HttpClient(hostname, port, user, pass);
            } else if (proxyEntry.length == 100) {
                httpClient = new HttpClient(hostname, port);
            } else {
                writeToLog("Invalid proxy configuration. Continuing...");
                return;
            }
            String[] info;
            try {
                info = getInfo(httpClient);
                if (info.length == 0) {
                    writeToLog("Bad proxy. Continuing...");
                    return;
                }
                String token = info[0];
                String sig = info[1];
                boolean viewWasSent = false;
                while (true) {
                    String videoSequenceURL = getVideoSequence(httpClient, token, sig);
                    if (videoSequenceURL.isEmpty())
                        return;
                    sendView(httpClient, videoSequenceURL);
                    if (!viewWasSent) {
                        viewWasSent = true;
                        Platform.runLater(controller::addCount);
                    }
                    TimeUnit.MILLISECONDS.sleep(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | JSONException e) {
                writeToLog("Bad proxy: " + hostname);
            }
        };
    }

    public void stop() {
        threadPool.shutdown();
        threadPool.shutdownNow();
        while (true) {
            try {
                writeToLog("Shutting down threads...");
                if (threadPool.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Platform.runLater(controller::stopViewBot);
    }

    private void sendView(HttpClient client, String url) throws IOException {
        HttpHead headRequest = new HttpHead(url);
        headRequest.setHeader(HttpHeaders.USER_AGENT, Configs.USER_AGENT);
        headRequest.setHeader(HttpHeaders.ACCEPT, Configs.ACCEPT_VIDEO);
        client.execute(headRequest);
    }

    private String getVideoSequence(HttpClient client, String token, String sig) throws IOException {
        String url = String.format(Configs.GET_VIDEO, target, sig, token);
        System.out.println(url);
        HttpGet getRequest = new HttpGet(url);
        getRequest.setHeader(HttpHeaders.USER_AGENT, Configs.USER_AGENT);
        getRequest.setHeader(HttpHeaders.ACCEPT, Configs.ACCEPT_VIDEO);
        CloseableHttpResponse response = client.execute(getRequest);
        String body = EntityUtils.toString(response.getEntity());
        if (body == null) {
            writeToLog("Can't get video sequence");
            return "";
        }
        return "https://" + body.substring(body.indexOf("https://") + "https://".length(), body.indexOf(".m3u8")) + ".m3u8";
    }

    private String[] getInfo(HttpClient httpClient) throws JSONException, IOException {
        String[] resultArray = new String[2];
        String url = String.format(Configs.GET_INFO, target, CLIENT_ID);
        HttpGet getRequest = new HttpGet(url);
        getRequest.setHeader(HttpHeaders.USER_AGENT, Configs.USER_AGENT);
        getRequest.setHeader(HttpHeaders.ACCEPT, Configs.ACCEPT_INFO);
        getRequest.setHeader(HttpHeaders.CONTENT_TYPE, Configs.CONTENT_INFO);
        getRequest.setHeader(HttpHeaders.ACCEPT_LANGUAGE, Configs.ACCEPT_LANG);
        getRequest.setHeader(HttpHeaders.REFERER, Configs.REFERER + target);
        try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
            String body = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject = new JSONObject(body);
            resultArray[0] = URLEncoder.encode(jsonObject.getString("token").replace("\\", ""), StandardCharsets.UTF_8);
            resultArray[1] = jsonObject.getString("sig").replace("\\", "");
        }
        return resultArray;
    }

}
