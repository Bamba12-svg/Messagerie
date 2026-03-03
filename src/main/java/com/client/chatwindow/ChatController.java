package com.client.chatwindow;

import com.client.util.VoicePlayback;
import com.client.util.VoiceRecorder;
import com.client.util.VoiceUtil;
import com.messages.Message;
import com.messages.MessageType;
import com.messages.Status;
import com.messages.User;
import com.messages.bubble.BubbleSpec;
import com.messages.bubble.BubbledLabel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ChatController implements Initializable {

    @FXML
    private TextArea messageBox;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label onlineCountLabel;
    @FXML
    private ListView<User> userList;
    @FXML
    private ImageView userImageView;
    @FXML
    private Button recordBtn;
    @FXML
    private ListView<HBox> chatPane;
    @FXML
    private BorderPane borderPane;
    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private ImageView microphoneImageView;
    @FXML
    private Label chatHeaderLabel;

    private String userPicture = "default";
    private String username;
    private String selectedReceiver = "ALL";
    private Map<String, ObservableList<HBox>> historyMap = new HashMap<>();

    Image microphoneActiveImage = new Image(
            getClass().getClassLoader().getResource("images/microphone-active.png").toString());
    Image microphoneInactiveImage = new Image(
            getClass().getClassLoader().getResource("images/microphone.png").toString());

    Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        historyMap.put("ALL", FXCollections.observableArrayList());
        chatPane.setItems(historyMap.get("ALL"));

        userList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (newVal.getName().equals("Chat Global")) {
                    selectedReceiver = "ALL";
                    chatHeaderLabel.setText("Chat Global");
                } else {
                    selectedReceiver = newVal.getName();
                    chatHeaderLabel.setText("Chat avec : " + selectedReceiver);
                }
            } else {
                selectedReceiver = "ALL";
                chatHeaderLabel.setText("Chat Global");
            }
            if (!historyMap.containsKey(selectedReceiver)) {
                historyMap.put(selectedReceiver, FXCollections.observableArrayList());
            }
            chatPane.setItems(historyMap.get(selectedReceiver));
        });

        statusComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try {
                    Listener.sendStatusUpdate(Status.valueOf(newVal.toUpperCase()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        messageBox.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
            if (ke.getCode().equals(KeyCode.ENTER)) {
                try {
                    sendButtonAction();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ke.consume();
            }
        });
    }

    public void setUsername(String username) {
        this.username = username;
        this.usernameLabel.setText(username);
    }

    public void setImage(String picture) {
        this.userPicture = picture;
        this.userImageView.setImage(new Image(
                getClass().getClassLoader().getResource("images/" + picture.toLowerCase() + ".png").toString()));
    }

    public void sendButtonAction() throws IOException {
        String msgContent = messageBox.getText().trim();
        if (!msgContent.isEmpty()) {
            Message msg = new Message();
            msg.setType(MessageType.USER);
            msg.setName(username);
            msg.setReceiver(selectedReceiver);
            msg.setMsg(msgContent);
            msg.setPicture(userPicture); // Using selected picture ref

            Listener.send(msg);
            messageBox.clear();
            addToChat(msg);
        }
    }

    public void recordVoiceMessage() throws IOException {
        if (VoiceUtil.isRecording()) {
            Platform.runLater(() -> microphoneImageView.setImage(microphoneInactiveImage));
            VoiceUtil.setRecording(false);

            // Attendre un court instant que l'enregistrement se termine et soit envoyé
            // Idéalement, VoiceRecorder devrait notifier quand il a fini
        } else {
            Platform.runLater(() -> microphoneImageView.setImage(microphoneActiveImage));
            VoiceRecorder.setController(this);
            VoiceRecorder.captureAudio(username, userPicture, selectedReceiver);
        }
    }

    public synchronized void addToChat(Message msg) {
        // On s'assure que l'ajout se fait sur le thread JavaFX
        Platform.runLater(() -> {
            boolean isMe = msg.getName().equals(username);
            HBox x = new HBox();

            // Sécurité sur la largeur : si l'interface n'est pas encore rendue, on prend
            // une largeur par défaut
            double paneWidth = chatPane.getWidth();
            if (paneWidth <= 0)
                paneWidth = 400;
            x.setMaxWidth(paneWidth - 40);
            x.setAlignment(isMe ? Pos.TOP_RIGHT : Pos.TOP_LEFT);

            BubbledLabel bl6 = new BubbledLabel();
            if (msg.getType() == MessageType.VOICE) {
                try {
                    URL soundUrl = getClass().getClassLoader().getResource("images/sound.png");
                    if (soundUrl != null) {
                        ImageView soundIcon = new ImageView(new Image(soundUrl.toString()));
                        soundIcon.setFitWidth(18);
                        soundIcon.setFitHeight(18);
                        bl6.setGraphic(soundIcon);
                    }
                } catch (Exception e) {
                    logger.error("Erreur icône son", e);
                }
                bl6.setText(" Message vocal (cliquez)");
                bl6.setCursor(javafx.scene.Cursor.HAND);
                bl6.setOnMouseClicked(e -> {
                    if (msg.getVoiceMsg() != null)
                        VoicePlayback.playAudio(msg.getVoiceMsg());
                });
            } else {
                bl6.setText(isMe ? msg.getMsg() : msg.getName() + ": " + msg.getMsg());
            }

            String bgColor = isMe ? "#4a3f8a" : "#2a2a4a";
            bl6.setBackground(new Background(
                    new BackgroundFill(Color.web(bgColor), new javafx.scene.layout.CornerRadii(12), null)));
            bl6.setStyle("-fx-text-fill: white; -fx-padding: 8 14; -fx-font-family: 'Segoe UI';");
            bl6.setBubbleSpec(isMe ? BubbleSpec.FACE_RIGHT_CENTER : BubbleSpec.FACE_LEFT_CENTER);

            if (isMe) {
                x.getChildren().addAll(bl6);
            } else {
                String picName = (msg.getPicture() != null && !msg.getPicture().isEmpty())
                        ? msg.getPicture().toLowerCase()
                        : "default";
                URL imageUrl = getClass().getClassLoader().getResource("images/" + picName + ".png");
                if (imageUrl == null)
                    imageUrl = getClass().getClassLoader().getResource("images/default.png");

                if (imageUrl != null) {
                    ImageView iv = new ImageView(new Image(imageUrl.toString()));
                    iv.setFitWidth(30);
                    iv.setFitHeight(30);
                    x.getChildren().addAll(iv, bl6);
                } else {
                    x.getChildren().addAll(bl6);
                }
            }

            // Déterminer dans quelle conversation filtrer le message
            String key;
            if (msg.getReceiver() == null || msg.getReceiver().equals("ALL")) {
                key = "ALL";
            } else {
                key = (msg.getName().equals(username)) ? msg.getReceiver() : msg.getName();
            }

            if (!historyMap.containsKey(key)) {
                historyMap.put(key, FXCollections.observableArrayList());
            }
            historyMap.get(key).add(x);

            // Auto-scroll si c'est la vue actuelle
            if (key.equals(selectedReceiver)) {
                chatPane.scrollTo(x);
            }
        });
    }

    public void setUserList(Message msg) {
        Platform.runLater(() -> {
            ObservableList<User> users = FXCollections.observableArrayList();

            // Création de l'item spécial pour le groupe
            User globalGroup = new User();
            globalGroup.setName("Chat Global");
            globalGroup.setPicture("group"); // On utilisera une icône de groupe
            globalGroup.setStatus(Status.ONLINE);
            users.add(globalGroup);

            // Ajout des autres utilisateurs
            users.addAll(msg.getUsers());

            userList.setItems(users);
            userList.setCellFactory(new CellRenderer());

            // Si rien n'est sélectionné, on sélectionne le groupe par défaut
            if (userList.getSelectionModel().getSelectedItem() == null) {
                userList.getSelectionModel().select(0);
            }

            onlineCountLabel.setText(String.valueOf(msg.getOnlineCount()));
        });
    }

    public void updateUserStatus(Message msg) {
        Platform.runLater(() -> userList.refresh());
    }

    @FXML
    public void closeApplication() {
        Platform.exit();
        System.exit(0);
    }
}