package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import com.google.gson.*;

public class SimpleLoginServer {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);
        server.createContext("/login", new LoginHandler());
        server.createContext("/bestand", new BestandHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("✅ Server läuft auf http://localhost:3000");
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS erlauben
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                String username = json.get("username").getAsString();
                String password = json.get("password").getAsString();

                boolean success = checkLogin(username, password);

                String response = "{\"success\":" + success + "}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private boolean checkLogin(String username, String password) {
            String url = "jdbc:mysql://localhost:3306/meine_datenbank?useSSL=false";
            String dbUser = "javauser";
            String dbPass = "passwort123";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM benutzer WHERE name = ? AND passwort = ?"
                );
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    static class BestandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS erlauben
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();

                int kaffee = json.get("kaffee").getAsInt();
                int milch = json.get("milch").getAsInt();
                int eier = json.get("eier").getAsInt();
                int mehl = json.get("mehl").getAsInt();

                boolean success = speichereBestand(kaffee, milch, eier, mehl);

                String response = "{\"success\":" + success + "}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private boolean speichereBestand(int kaffee, int milch, int eier, int mehl) {
            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            String dbUser = "javauser";
            String dbPass = "passwort123";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO bestand (kaffee, milch, eier, mehl) VALUES (?, ?, ?, ?)"
                );
                stmt.setInt(1, kaffee);
                stmt.setInt(2, milch);
                stmt.setInt(3, eier);
                stmt.setInt(4, mehl);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}