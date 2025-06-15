package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class VerbindungDB {

  private static final String URL = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
  private static final String USER = "admin";      // dein MySQL-Benutzer
  private static final String PASSWORD = "admin";  // dein Passwort

  // Neue Methode:
  public static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(URL, USER, PASSWORD);
  }

  public static void main(String[] args) {
    try {
      Connection conn = getConnection();
      System.out.println("Verbindung erfolgreich!");
      conn.close();
    } catch (SQLException e) {
      System.out.println("Fehler bei der Verbindung:");
      e.printStackTrace();
    }
  }
}