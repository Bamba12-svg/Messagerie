package com.client.login;

import com.client.chatwindow.ChatController;
import com.client.chatwindow.Listener;
import com.messages.Message;
import com.messages.MessageType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML
    private TextField hostnameTextfield;
    @FXML
    private TextField portTextfield;
    @FXML
    private TextField usernameTextfield;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ChoiceBox<String> imagePicker;
    @FXML
    private ImageView Defaultview;
    @FXML
    private ImageView Sarahview;
    @FXML
    private ImageView Dominicview;
    @FXML
    private BorderPane borderPane;

    private static ChatController con;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        imagePicker.getSelectionModel().selectFirst();

        // Listen directly to imagePicker selection
        imagePicker.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                Defaultview.setVisible(newValue.equals("Default"));
                Dominicview.setVisible(newValue.equals("Dominic"));
                Sarahview.setVisible(newValue.equals("Sarah"));
            }
        });
    }

    public void loginButtonAction() {
        connectToServer(MessageType.LOGIN);
    }

    public void registerButtonAction() {
        connectToServer(MessageType.REGISTER);
    }

    private void connectToServer(MessageType type) {
        String hostname = hostnameTextfield.getText();
        String portStr = portTextfield.getText();
        String username = usernameTextfield.getText();
        String password = passwordField.getText();
        String picture = imagePicker.getSelectionModel().getSelectedItem();

        if (username.isEmpty() || password.isEmpty() || hostname.isEmpty() || portStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Champs requis", "Veuillez remplir tous les champs.");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            Socket socket = new Socket(hostname, port);
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            Message msg = new Message();
            msg.setType(type);
            msg.setName(username);
            msg.setPassword(password);
            msg.setPicture(picture);

            oos.writeObject(msg);
            oos.flush();

            Message response = (Message) ois.readObject();

            if (response.getType() == MessageType.AUTH_SUCCESS) {
                if (type == MessageType.LOGIN) {
                    switchToChat(hostname, port, username, picture, socket, oos, ois);
                } else {
                    showAlert(Alert.AlertType.INFORMATION, "Succès", response.getMsg());
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Échec", response.getMsg());
                socket.close();
            }

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur Port", "Le port doit être un nombre.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur de connexion", "Impossible de se connecter : " + e.getMessage());
        }
    }

    private void switchToChat(String hostname, int port, String username, String picture, Socket socket,
            ObjectOutputStream oos, ObjectInputStream ois) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ChatView.fxml"));
        Parent window = (Pane) loader.load();
        con = loader.getController();
        con.setUsername(username);
        con.setImage(picture);

        Listener listener = new Listener(hostname, port, username, picture, con, socket, oos, ois);
        Thread x = new Thread(listener);
        x.start();

        Scene chatScene = new Scene(window);
        chatScene.getStylesheets().add(getClass().getResource("/styles/ChatViewStyle.css").toExternalForm());

        Platform.runLater(() -> {
            Stage stage = (Stage) borderPane.getScene().getWindow();
            stage.setResizable(true);
            stage.setWidth(1040);
            stage.setHeight(620);
            stage.setMinWidth(1040);
            stage.setMinHeight(620);
            stage.setScene(chatScene);
            stage.centerOnScreen();
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    @FXML
    public void minimizeWindow() {
        ((Stage) borderPane.getScene().getWindow()).setIconified(true);
    }

    @FXML
    public void closeSystem() {
        Platform.exit();
        System.exit(0);
    }
}
