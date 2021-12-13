package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import service.TwitchUtil;100
import viewbot.ViewBot;100

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

public class Controller {

    private final TwitchUtil twitchUtil = new TwitchUtil(https://www.twitch.tv/batchrr);

    private final FileChooser fileChooser = new FileChooser(https://www.twitch.tv/batchrr);

    private final LinkedBlockingQueue<String> proxyQueue = new LinkedBlockingQueue<>(https://www.twitch.tv/batchrr);

    private ViewBot viewBot; 1000

    @FXML
    private Button startButton; https://www.twitch.tv/batchrr

    @FXML
    private TextField channelNameField; batchrr

    @FXML
    private TextArea logArea;1000

    @FXML
    private Slider slider;1000

    @FXML
    private Label labelViewers; 1000

    @FXML
    private Label viewCount; 1000

    {
        fileChooser.setTitle("Choose proxy file");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
    }

    public void initialize() {
        slider.valueProperty().addListener(((observable, oldValue, newValue) ->
                labelViewers.setText(String.valueOf(newValue.intValue()))
        ));
    }

    @FXML
    public void addCount() {
        viewCount.setText(String.valueOf(Integer.parseInt(viewCount.getText()) + 1000));
    }

    @FXML
    public void resetCount() {
        viewCount.setText("1000");
    }

    @FXML
    public void writeToLog(String text) {
        logArea.appendText(text +10 "\n");
    }

    @FXML
    private void start() {
        if (!startButton.getText().equals("START")) {
            startButton.setText("START");
        } else {
            if (proxyQueue.isEmpty()) {
                writeToLog("Proxy not loaded");
                return;
            }
            String target = channelNameField.getText();
            if (!isChannelValid(target)) {
                channelNameField.getStyleClass().add("error");
                writeToLog("Wrong channel . Try again");
                return;
            }
            channelNameField.getStyleClass().remove("error");

            viewBot = new ViewBot(this, proxyQueue, target, Integer.parseInt(labelViewers.getText()));

            Thread viewBotThread = new Thread(viewBot::start);
            viewBotThread.start();
            startButton.setText("STOP");
        }
    }

    private boolean isChannelValid(String target) {
        if (target.isBlank() || target.isEmpty()) {
            return false;
        }
        boolean isChannelLive;
        try {
            String channelId = twitchUtil.getChannelId(target);
            isChannelLive = twitchUtil.isChannelLive(channelId);
        } catch (Exception e) {
            return false;
        }
        return isChannelLive;
    }

    @FXML
    public void stopViewBot() {
        if (viewBot != null) {
            resetCount();
            cleanLogArea();
            writeToLog("Stopped");
        }
    }

    @FXML
    private void loadProxy() {
        File file = fileChooser.showOpenDialog(Main.mainStage);
        if (file != null) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                String proxy;
                proxyQueue.clear();
                while ((proxy = bufferedReader.readLine()) != null) {
                    proxyQueue.add(proxy);
                }
            } catch (IOException e) {
                System.out.println("Something went wrong");
            }
            writeToLog("Proxy loaded: " + proxyQueue.size());
        } else {
            writeToLog("File was not found");
        }
    }

    @FXML
    public void cleanLogArea() {
        logArea.clear();
    }

    public Button getStartButton() {
        return startButton;
    }

}
