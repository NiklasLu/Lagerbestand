package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class VerbindungDB {
  public static void main(String[] args) {
    String url = "jdbc:mysql://localhost:3306/Lagerbestand"; // Name Datenbank
    String user = "admin"; // Benutzername, wie in phpMyAdmin eingerichtet
    String password = "admin"; // Passwort dazu

    try {
      // Verbindung herstellen
        Connection conn = DriverManager.getConnection(url, user, password);
          System.out.println("Verbindung erfolgreich!");
        conn.close();
    } catch (SQLException e) {
      System.out.println("Fehler bei der Verbindung:");
      e.printStackTrace();
    }
  }
}
