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
        server.createContext("/pruefen", new PruefHandler());
        server.createContext("/abweichungen", new AbweichungHandler());
        server.createContext("/abweichungen/clear", new AbweichungClearHandler());
        server.createContext("/abweichung/korrigieren", new AbweichungKorrigierenHandler());
        File file = new File("abweichungen.txt");
        if (file.exists()) {
            System.out.println("⚠️ Es liegen möglicherweise Abweichungen zur Prüfung vor (siehe abweichungen.txt)");
        }
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
            String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
            String dbUser = "javauser";
            String dbPass = "passwort123";

            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM benutzer WHERE name = ? AND passwort = ?"
                );
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();

                boolean gefunden = rs.next();
                System.out.println("Login-Versuch für '" + username + "' → gefunden: " + gefunden); // ← NEU
                return gefunden;
            } catch (SQLException e) {
                System.out.println("Fehler bei DB-Verbindung:");
                e.printStackTrace();
                return false;
            }
        }
    }

    static class PruefHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS-Header
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

                // Letzten Original-Bestand holen
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM bestand ORDER BY gespeichert_am DESC LIMIT 1");

                    if (rs.next()) {
                        int origId = rs.getInt("id");
                        boolean stimmt = (
                            rs.getInt("kaffee") == kaffee &&
                                rs.getInt("milch") == milch &&
                                rs.getInt("eier") == eier &&
                                rs.getInt("mehl") == mehl
                        );

                        PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO pruefung (original_id, kaffee, milch, eier, mehl, stimmt_ueberein) VALUES (?, ?, ?, ?, ?, ?)"
                        );
                        insert.setInt(1, origId);
                        insert.setInt(2, kaffee);
                        insert.setInt(3, milch);
                        insert.setInt(4, eier);
                        insert.setInt(5, mehl);
                        insert.setBoolean(6, stimmt);
                        insert.executeUpdate();

                        String response = stimmt
                                          ? "{\"success\": true, \"message\": \"Bestände stimmen überein.\"}"
                                          : "{\"success\": false, \"message\": \"Abweichung festgestellt. Chef wurde benachrichtigt.\"}";

                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();

                        if (!stimmt) {
                            // Nachricht an den "Chef" speichern
                            try (FileWriter fw = new FileWriter("abweichungen.txt", true);
                                 BufferedWriter bw = new BufferedWriter(fw);
                                 PrintWriter out = new PrintWriter(bw)) {
                                out.println("❌ Abweichung festgestellt am " + new java.util.Date());
                                out.println("Original: Kaffee=" + rs.getInt("kaffee") + ", Milch=" + rs.getInt("milch") + ", Eier=" + rs.getInt("eier") + ", Mehl=" + rs.getInt("mehl"));
                                out.println("Gezählt:  Kaffee=" + kaffee + ", Milch=" + milch + ", Eier=" + eier + ", Mehl=" + mehl);
                                out.println("-----------------------------------------------------");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class AbweichungHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS-Header
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM pruefung WHERE stimmt_ueberein = false ORDER BY timestamp DESC"
                    );
                    ResultSet rs = stmt.executeQuery();

                    JsonArray array = new JsonArray();
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("zeit", rs.getString("timestamp"));
                        obj.addProperty("kaffee", rs.getInt("kaffee"));
                        obj.addProperty("milch", rs.getInt("milch"));
                        obj.addProperty("eier", rs.getInt("eier"));
                        obj.addProperty("mehl", rs.getInt("mehl"));
                        array.add(obj);
                    }

                    String response = new Gson().toJson(array);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    static class AbweichungClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM pruefung WHERE stimmt_ueberein = false"
                    );
                    int rows = stmt.executeUpdate();

                    String response = "{\"success\": true, \"deleted\": " + rows + "}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    static class AbweichungKorrigierenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery(); // z.B. id=7
                int id = Integer.parseInt(query.split("=")[1]);

                String url = "jdbc:mysql://localhost:3306/lagerbestand?useSSL=false";
                String dbUser = "javauser";
                String dbPass = "passwort123";

                try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                    // Hole Korrekturwerte
                    PreparedStatement select = conn.prepareStatement("SELECT * FROM pruefung WHERE id = ?");
                    select.setInt(1, id);
                    ResultSet rs = select.executeQuery();

                    if (rs.next()) {
                        int originalId = rs.getInt("original_id");

                        // Aktualisiere Bestand
                        PreparedStatement update = conn.prepareStatement(
                            "UPDATE bestand SET kaffee = ?, milch = ?, eier = ?, mehl = ? WHERE id = ?"
                        );
                        update.setInt(1, rs.getInt("kaffee"));
                        update.setInt(2, rs.getInt("milch"));
                        update.setInt(3, rs.getInt("eier"));
                        update.setInt(4, rs.getInt("mehl"));
                        update.setInt(5, originalId);
                        update.executeUpdate();

                        // Lösche die Abweichung
                        PreparedStatement delete = conn.prepareStatement("DELETE FROM pruefung WHERE id = ?");
                        delete.setInt(1, id);
                        delete.executeUpdate();

                        String response = "{\"success\": true, \"message\": \"Bestand aktualisiert.\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
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