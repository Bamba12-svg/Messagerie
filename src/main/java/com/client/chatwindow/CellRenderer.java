package com.client.chatwindow;

import com.messages.User;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.util.Callback;

/**
 * A Class for Rendering users images / name on the userlist.
 */
class CellRenderer implements Callback<ListView<User>, ListCell<User>> {
    @Override
    public ListCell<User> call(ListView<User> p) {

        ListCell<User> cell = new ListCell<User>() {

            @Override
            protected void updateItem(User user, boolean bln) {
                super.updateItem(user, bln);
                setGraphic(null);
                setText(null);
                if (user != null && !bln) {
                    HBox hBox = new HBox();
                    hBox.setSpacing(10);
                    hBox.setAlignment(Pos.CENTER_LEFT);

                    Text name = new Text(user.getName());
                    name.setStyle("-fx-fill: white; -fx-font-family: 'Segoe UI';");

                    // Icône de statut
                    ImageView statusImageView = new ImageView();
                    String statusName = (user.getStatus() != null) ? user.getStatus().toString().toLowerCase()
                            : "offline";
                    java.net.URL statusURL = getClass().getClassLoader().getResource("images/" + statusName + ".png");

                    if (statusURL == null && "offline".equals(statusName)) {
                        // Fallback si offline.png manque (on pourrait utiliser une version grise de
                        // online)
                        statusURL = getClass().getClassLoader().getResource("images/busy.png");
                    }

                    if (statusURL != null) {
                        Image statusImage = new Image(statusURL.toString(), 16, 16, true, true);
                        statusImageView.setImage(statusImage);
                    }

                    // Image de profil
                    ImageView pictureImageView = new ImageView();
                    String picName = (user.getPicture() != null) ? user.getPicture().toLowerCase() : "default";

                    // Cas spécial pour l'icône de groupe
                    if ("group".equals(picName)) {
                        statusImageView.setVisible(false); // Pas de statut pour le groupe
                        statusImageView.setManaged(false);
                        picName = "fxchat"; // On utilise l'icône de l'appli par défaut pour le groupe
                    } else {
                        statusImageView.setVisible(true);
                        statusImageView.setManaged(true);
                    }

                    java.net.URL picURL = getClass().getClassLoader().getResource("images/" + picName + ".png");
                    if (picURL == null) {
                        picURL = getClass().getClassLoader().getResource("images/default.png");
                    }
                    if (picURL != null) {
                        Image image = new Image(picURL.toString(), 40, 40, true, true);
                        pictureImageView.setImage(image);
                    }

                    hBox.getChildren().addAll(statusImageView, pictureImageView, name);
                    setGraphic(hBox);
                }
            }
        };
        return cell;
    }
}