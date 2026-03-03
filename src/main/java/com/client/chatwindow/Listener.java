package com.client.chatwindow;

import com.messages.Message;
import com.messages.MessageType;
import com.messages.Status;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class Listener implements Runnable {

    private Socket socket;
    private ObjectInputStream input;
    private ChatController controller;
    private static ObjectOutputStream staticOutput;
    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    public Listener(String hostname, int port, String username, String picture, ChatController controller,
            Socket socket, ObjectOutputStream oos, ObjectInputStream ois) {
        this.controller = controller;
        this.socket = socket;
        this.input = ois;
        staticOutput = oos;
    }

    public static void send(Message msg) throws IOException {
        if (staticOutput != null) {
            staticOutput.writeObject(msg);
            staticOutput.flush();
            staticOutput.reset();
        }
    }

    public static void sendVoiceMessage(byte[] audio, String name, String picture, String receiver) throws IOException {
        Message msg = new Message();
        msg.setType(MessageType.VOICE);
        msg.setVoiceMsg(audio);
        msg.setName(name);
        msg.setPicture(picture);
        msg.setReceiver(receiver);
        send(msg);
    }

    public static void sendStatusUpdate(Status status) throws IOException {
        Message msg = new Message();
        msg.setType(MessageType.STATUS);
        msg.setStatus(status);
        send(msg);
    }

    public void run() {
        try {
            while (socket.isConnected() && !socket.isClosed()) {
                Object obj = input.readObject();
                if (obj instanceof Message) {
                    Message msg = (Message) obj;
                    switch (msg.getType()) {
                        case USER:
                        case VOICE:
                        case SERVER:
                        case NOTIFICATION:
                            Platform.runLater(() -> controller.addToChat(msg));
                            break;
                        case CONNECTED:
                            Platform.runLater(() -> controller.setUserList(msg));
                            break;
                        case STATUS:
                            Platform.runLater(() -> controller.updateUserStatus(msg));
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (EOFException e) {
            handleConnectionLoss("Connexion perdue : Le serveur a fermé la connexion.");
        } catch (Exception e) {
            logger.error("Error in Listener", e);
            handleConnectionLoss("Erreur de connexion : " + e.getMessage());
        } finally {
            closeSocket();
        }
    }

    private void handleConnectionLoss(String errorMsg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Déconnecté");
            alert.setHeaderText(null);
            alert.setContentText(errorMsg);
            alert.showAndWait();
            System.exit(0);
        });
    }

    private void closeSocket() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
