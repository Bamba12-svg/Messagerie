package com.server;

import com.messages.Message;
import com.messages.MessageType;
import com.messages.Status;
import com.messages.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Server {

    private static final int PORT = 9001;
    private static final HashMap<String, ObjectOutputStream> userWriters = new HashMap<>();
    private static final HashMap<String, User> onlineUsers = new HashMap<>();
    private static final ArrayList<User> usersList = new ArrayList<>();
    static Logger logger = LoggerFactory.getLogger(Server.class);

    private static com.server.repositories.UserRepository userRepository = new com.server.repositories.UserRepository();
    private static com.server.repositories.MessageRepository messageRepository = new com.server.repositories.MessageRepository();

    public static void main(String[] args) throws Exception {
        logger.info("Serveur démarré sur le port " + PORT);
        ServerSocket listener = new ServerSocket(PORT);

        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } catch (Exception e) {
            logger.error("Erreur serveur", e);
        } finally {
            listener.close();
            com.server.util.HibernateUtil.shutdown();
        }
    }

    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private Logger logger = LoggerFactory.getLogger(Handler.class);
        private User user;
        private ObjectInputStream input;
        private ObjectOutputStream output;

        public Handler(Socket socket) throws IOException {
            this.socket = socket;
        }

        public void run() {
            try {
                output = new ObjectOutputStream(socket.getOutputStream());
                input = new ObjectInputStream(socket.getInputStream());

                while (!socket.isClosed()) {
                    Object obj = input.readObject();
                    if (obj == null)
                        break;
                    if (!(obj instanceof Message))
                        continue;

                    Message inputmsg = (Message) obj;

                    switch (inputmsg.getType()) {
                        case REGISTER:
                            handleRegistration(inputmsg);
                            break;
                        case LOGIN:
                            handleLogin(inputmsg);
                            break;
                        case USER:
                        case VOICE:
                            // RG2 : Authentification indispensable
                            if (name == null) {
                                logger.warn("Tentative d'envoi de message par une socket non-authentifiée.");
                                return;
                            }
                            handleMessage(inputmsg);
                            break;
                        case STATUS:
                            if (name != null)
                                handleStatusChange(inputmsg);
                            break;
                        case DISCONNECTED:
                            logger.info("Déconnexion demandée par l'utilisateur : " + name);
                            return;
                        default:
                            break;
                    }
                }
            } catch (EOFException e) {
                logger.info("Utilisateur déconnecté : " + name);
            } catch (Exception e) {
                logger.error("Erreur handler pour " + name, e);
            } finally {
                closeConnections();
            }
        }

        private void handleRegistration(Message msg) throws IOException {
            logger.info("Tentative d'inscription : " + msg.getName());
            if (userRepository.findByUsername(msg.getName()).isPresent()) {
                sendAuthFailure("Nom d'utilisateur déjà pris.");
                return;
            }

            com.server.entities.User entity = new com.server.entities.User();
            entity.setUsername(msg.getName());
            entity.setPassword(userRepository.hashPassword(msg.getPassword()));
            entity.setPicture(msg.getPicture());
            userRepository.save(entity);

            Message response = new Message();
            response.setType(MessageType.AUTH_SUCCESS);
            response.setMsg("Inscription réussie !");
            output.writeObject(response);
            output.flush();
        }

        private void handleLogin(Message msg) throws IOException {
            logger.info("Tentative de connexion : " + msg.getName());
            if (userWriters.containsKey(msg.getName())) {
                sendAuthFailure("L'utilisateur est déjà connecté.");
                return;
            }

            if (userRepository.authenticate(msg.getName(), msg.getPassword())) {
                this.name = msg.getName();
                user = new User();
                user.setName(name);
                user.setStatus(Status.ONLINE);
                user.setPicture(msg.getPicture());

                userRepository.findByUsername(name).ifPresent(u -> {
                    u.setStatus("ONLINE");
                    userRepository.update(u);
                });

                synchronized (userWriters) {
                    userWriters.put(name, output);
                    onlineUsers.put(name, user);
                    usersList.add(user);
                }

                Message response = new Message();
                response.setType(MessageType.AUTH_SUCCESS);
                response.setMsg("Bienvenue");
                response.setPicture(msg.getPicture());
                output.writeObject(response);
                output.flush();

                broadcastUserUpdate();

                // On attend un court instant pour laisser le client charger l'UI (RG8 delay)
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        sendOfflineMessages();
                    } catch (Exception e) {
                        logger.error("Erreur lors de l'envoi différé", e);
                    }
                }).start();
            } else {
                sendAuthFailure("Identifiants incorrects.");
            }
        }

        private void handleMessage(Message msg) throws IOException {
            // RG7 Rule
            if (msg.getType() == MessageType.USER
                    && (msg.getMsg() == null || msg.getMsg().trim().isEmpty() || msg.getMsg().length() > 1000)) {
                return;
            }

            logger.info("Message de " + msg.getName() + " pour " + msg.getReceiver());

            String target = msg.getReceiver();
            com.server.entities.User senderEntity = userRepository.findByUsername(msg.getName()).orElse(null);

            // Send back to sender for client-side display if it was private (ChatController
            // adds it manually though)
            // But if it's "ALL", everyone should get it
            if (target == null || target.equals("ALL")) {
                try {
                    if (senderEntity != null) {
                        com.server.entities.Message entity = new com.server.entities.Message();
                        entity.setSender(senderEntity);
                        entity.setReceiver(null); // Global message
                        entity.setContent(msg.getMsg());
                        entity.setVoice(msg.getType() == MessageType.VOICE);
                        entity.setVoiceData(msg.getVoiceMsg());
                        entity.setStatus("RECU");
                        messageRepository.save(entity);
                    }
                } catch (Exception e) {
                    logger.error(
                            "Impossible de sauvegarder le message global en BDD (vérifiez la contrainte NOT NULL sur receiver_id)",
                            e);
                }
                broadcastMessage(msg);
                return;
            }

            // Private message routing
            com.server.entities.User receiverEntity = userRepository.findByUsername(target).orElse(null);
            if (senderEntity != null && receiverEntity != null) {
                com.server.entities.Message entity = new com.server.entities.Message();
                entity.setSender(senderEntity);
                entity.setReceiver(receiverEntity);
                entity.setContent(msg.getMsg());
                entity.setVoice(msg.getType() == MessageType.VOICE);
                entity.setVoiceData(msg.getVoiceMsg());

                synchronized (userWriters) {
                    if (userWriters.containsKey(target) && !target.equals(msg.getName())) {
                        try {
                            ObjectOutputStream writer = userWriters.get(target);
                            msg.setUserlist(onlineUsers);
                            msg.setUsers(new ArrayList<>(usersList));
                            writer.writeObject(msg);
                            writer.flush();
                            writer.reset();
                            entity.setStatus("RECU");
                        } catch (IOException e) {
                            logger.error("Erreur lors de l'envoi privé à {}", target);
                            entity.setStatus("ENVOYE"); // Sera livré plus tard
                        }
                    } else if (target.equals(msg.getName())) {
                        entity.setStatus("RECU"); // Déjà affiché localement
                    } else {
                        entity.setStatus("ENVOYE");
                    }
                }
                messageRepository.save(entity);
            }
        }

        private void broadcastMessage(Message msg) throws IOException {
            logger.info("Message global de " + msg.getName() + " : '" +
                    (msg.getType() == MessageType.VOICE ? "VOCAL" : msg.getMsg()) + "'");

            synchronized (userWriters) {
                // On met à jour les informations de liste pour le message
                msg.setUserlist(onlineUsers);
                msg.setUsers(new ArrayList<>(usersList));
                msg.setOnlineCount(onlineUsers.size());

                userWriters.forEach((userName, writer) -> {
                    try {
                        // On n'envoie pas à l'expéditeur car le client l'ajoute déjà localement
                        if (!userName.equals(msg.getName())) {
                            writer.writeObject(msg);
                            writer.flush();
                            writer.reset();
                        }
                    } catch (IOException e) {
                        logger.warn("Impossible d'envoyer le message global à {}, la connexion semble perdue.",
                                userName);
                        // On ne relance pas l'exception pour ne pas couper la connexion de l'expéditeur
                    }
                });
            }
        }

        private void handleStatusChange(Message msg) throws IOException {
            if (user != null) {
                user.setStatus(msg.getStatus());
                userRepository.findByUsername(name).ifPresent(u -> {
                    u.setStatus(msg.getStatus().toString());
                    userRepository.update(u);
                });
                broadcastUserUpdate();
            }
        }

        private void sendOfflineMessages() throws IOException {
            com.server.entities.User entity = userRepository.findByUsername(name).orElse(null);
            if (entity != null) {
                // Récupération de l'historique récent (inclus les messages offline)
                List<com.server.entities.Message> history = messageRepository.findRecentHistory(entity);
                List<com.server.entities.Message> offlineToMark = new ArrayList<>();

                for (com.server.entities.Message m : history) {
                    Message msg = new Message();
                    msg.setType(m.isVoice() ? MessageType.VOICE : MessageType.USER);
                    msg.setName(m.getSender().getUsername());
                    msg.setReceiver(m.getReceiver() != null ? m.getReceiver().getUsername() : "ALL");
                    msg.setMsg(m.getContent());
                    msg.setVoiceMsg(m.getVoiceData());
                    msg.setPicture(m.getSender().getPicture());

                    output.writeObject(msg);
                    output.flush();
                    output.reset();

                    if ("ENVOYE".equals(m.getStatus()) && entity.equals(m.getReceiver())) {
                        offlineToMark.add(m);
                    }
                }

                if (!offlineToMark.isEmpty()) {
                    messageRepository.markAsDelivered(offlineToMark);
                }
            }
        }

        private void broadcastUserUpdate() throws IOException {
            // RG : On veut voir TOUS les utilisateurs enregistrés
            List<com.server.entities.User> allSavedUsers = userRepository.findAll();
            ArrayList<com.messages.User> msgUsers = new ArrayList<>();

            for (com.server.entities.User entity : allSavedUsers) {
                com.messages.User u = new com.messages.User();
                u.setName(entity.getUsername());
                u.setPicture(entity.getPicture());

                // Déterminer le statut actuel
                if (userWriters.containsKey(entity.getUsername())) {
                    // Si connecté, on récupère son statut actuel (Online, Away, Busy)
                    u.setStatus(onlineUsers.get(entity.getUsername()).getStatus());
                } else {
                    u.setStatus(Status.OFFLINE);
                }
                msgUsers.add(u);
            }

            Message update = new Message();
            update.setType(MessageType.CONNECTED);
            update.setUsers(msgUsers);
            update.setOnlineCount(onlineUsers.size());

            synchronized (userWriters) {
                for (ObjectOutputStream writer : userWriters.values()) {
                    writer.writeObject(update);
                    writer.flush();
                    writer.reset();
                }
            }
        }

        private void sendAuthFailure(String msg) throws IOException {
            Message response = new Message();
            response.setType(MessageType.AUTH_FAILED);
            response.setMsg(msg);
            output.writeObject(response);
            output.flush();
        }

        private void closeConnections() {
            if (name != null) {
                synchronized (userWriters) {
                    userWriters.remove(name);
                    onlineUsers.remove(name);
                    usersList.remove(user);
                }
                userRepository.findByUsername(name).ifPresent(u -> {
                    u.setStatus("OFFLINE");
                    userRepository.update(u);
                });
                try {
                    broadcastUserUpdate();
                } catch (Exception e) {
                }
            }
            try {
                if (socket != null && !socket.isClosed())
                    socket.close();
            } catch (IOException e) {
            }
        }
    }
}
