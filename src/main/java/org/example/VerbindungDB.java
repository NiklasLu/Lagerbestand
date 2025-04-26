//package org.example;
//
//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.SQLException;
//
//public class VerbindungDB {
//  String url = "jdbc:postgresql://localhost:5432/dein_datenbankname";
//  String user = "admin";
//  String password = "admin";
//
//        try (Connection conn = DriverManager.getConnection(url, user, password)) {
//    System.out.println("Verbindung erfolgreich!");
//  } catch (SQLException e) {
//    e.printStackTrace();
//  }
//
//  public VerbindungDB() throws SQLException {
//  }
//}
