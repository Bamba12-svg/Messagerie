package com.client.util;

import com.client.chatwindow.Listener;
import com.messages.Message;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Dominic
 * @since 16-Oct-16
 *        Website: www.dominicheal.com
 *        Github: www.github.com/DomHeal
 */
public class VoiceRecorder extends VoiceUtil {

    private static com.client.chatwindow.ChatController controller;

    public static void setController(com.client.chatwindow.ChatController con) {
        controller = con;
    }

    public static void captureAudio(String name, String picture, String receiver) {
        try {
            final AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            final TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            Runnable runner = new Runnable() {
                int bufferSize = (int) format.getSampleRate() * format.getFrameSize();
                byte buffer[] = new byte[bufferSize];

                public void run() {
                    out = new ByteArrayOutputStream();
                    isRecording = true;
                    try {
                        while (isRecording) {
                            int count = line.read(buffer, 0, buffer.length);
                            if (count > 0) {
                                out.write(buffer, 0, count);
                            }
                        }
                    } finally {
                        try {
                            out.flush();
                            out.close();
                            line.drain();
                            line.close();
                            byte[] audioData = out.toByteArray();
                            Listener.sendVoiceMessage(audioData, name, picture, receiver);

                            // NEW: Add to sender's UI immediately
                            Message myMsg = new Message();
                            myMsg.setType(com.messages.MessageType.VOICE);
                            myMsg.setName(name);
                            myMsg.setVoiceMsg(audioData);
                            myMsg.setPicture(picture);
                            myMsg.setReceiver(receiver);

                            if (controller != null) {
                                javafx.application.Platform.runLater(() -> controller.addToChat(myMsg));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            Thread captureThread = new Thread(runner);
            captureThread.start();
        } catch (LineUnavailableException e) {
            System.err.println("Line unavailable: ");
            e.printStackTrace();
        }
    }
}
