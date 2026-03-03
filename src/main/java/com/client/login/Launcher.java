package com.client.login;

/**
 * Cette classe sert de point d'entrée pour contourner le problème
 * "JavaFX runtime components are missing" avec Java 11+.
 * Elle n'étend PAS Application, ce qui évite la vérification du module JavaFX.
 */
public class Launcher {
    public static void main(String[] args) {
        MainLauncher.main(args);
    }
}
